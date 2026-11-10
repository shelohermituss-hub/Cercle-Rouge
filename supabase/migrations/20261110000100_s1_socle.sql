-- S1 · Socle : profils, consentements, journal d'audit, consentement de l'adulte référent (ADR-0013).
-- Règles : RLS partout, écritures sensibles par RPC SECURITY DEFINER (search_path fixé, EXECUTE retiré à PUBLIC et anon),
-- (select auth.uid()) dans les politiques, contraintes CHECK en filet final. Voir docs/modules/backend-api.md.

-- ---------------------------------------------------------------------------
-- Fonctions utilitaires
-- ---------------------------------------------------------------------------

-- Tranche d'âge à une date donnée, à partir de l'année et du mois de naissance (minimisation : pas de jour).
create function public.age_band(p_birth_year int, p_birth_month int, p_at date default current_date)
returns text language sql immutable set search_path = public as $$
  select case
    when age < 12 then 'under12'
    when age < 16 then '12_15'
    when age < 18 then '16_17'
    else 'adult'
  end
  from (select extract(year from age(p_at, make_date(p_birth_year, p_birth_month, 1)))::int as age) a
$$;

-- Numéro de téléphone normalisé (chiffres seuls, comme auth.users.phone) puis haché.
create function public.phone_hash(p_phone text)
returns text language sql immutable set search_path = public as $$
  select encode(extensions.digest(regexp_replace(coalesce(p_phone, ''), '[^0-9]', '', 'g'), 'sha256'), 'hex')
$$;

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text not null check (char_length(display_name) between 1 and 40),
  locale text not null default 'fr' check (locale in ('fr', 'ht', 'en')),
  birth_year int not null check (birth_year between 1900 and 2100),
  birth_month int not null check (birth_month between 1 and 12),
  is_adult_attested boolean not null default false,
  role text not null default 'user' check (role in ('user', 'support', 'admin')),
  created_at timestamptz not null default now()
);

create table public.guardian_links (
  id uuid primary key default gen_random_uuid(),
  minor_id uuid not null references public.profiles (id) on delete cascade,
  -- Numéro en clair seulement le temps d'envoyer l'invitation ; effacé dès la décision (minimisation).
  guardian_phone text,
  guardian_phone_hash text not null,
  guardian_id uuid references public.profiles (id) on delete set null,
  relationship text not null check (relationship in ('parent', 'tuteur', 'autre')),
  status text not null default 'pending' check (status in ('pending', 'confirmed', 'refused', 'revoked', 'expired')),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '7 days',
  decided_at timestamptz,
  check (guardian_id is null or guardian_id <> minor_id),
  check (status <> 'confirmed' or (guardian_id is not null and decided_at is not null))
);
-- Un seul lien en attente ou confirmé à la fois par jeune.
create unique index guardian_links_one_active on public.guardian_links (minor_id) where status in ('pending', 'confirmed');
create index guardian_links_guardian on public.guardian_links (guardian_id) where status = 'confirmed';
create index guardian_links_phone on public.guardian_links (guardian_phone_hash) where status = 'pending';

create table public.consents (
  id bigint generated always as identity primary key,
  user_id uuid not null references public.profiles (id) on delete cascade,
  scope text not null check (scope in ('MICROPHONE', 'LOCATION', 'NOTIFICATIONS', 'CONTACTS', 'AUDIO_CLIP_SHARING',
                                       'AUTHORITIES', 'MODEL_IMPROVEMENT', 'PRIVACY_POLICY')),
  granted boolean not null,
  text_version text not null check (char_length(text_version) between 1 and 32),
  locale text not null check (locale in ('fr', 'ht', 'en')),
  at timestamptz not null default now()
);
create index consents_user on public.consents (user_id, scope, at desc);

-- Journal d'audit append-only (règle 7) : aucune politique UPDATE ni DELETE.
create table public.audit_logs (
  id bigint generated always as identity primary key,
  actor uuid,
  action text not null,
  target uuid,
  meta jsonb not null default '{}'::jsonb,
  at timestamptz not null default now()
);

create table public.rate_limits (
  user_id uuid not null references public.profiles (id) on delete cascade,
  action text not null,
  day date not null default current_date,
  count int not null default 0 check (count >= 0),
  primary key (user_id, action, day)
);

-- ---------------------------------------------------------------------------
-- Helpers SECURITY DEFINER pour les politiques (pas de sous-requête inline sur profiles : règle 5)
-- ---------------------------------------------------------------------------

create function public.is_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.profiles where id = (select auth.uid()) and role = 'admin')
$$;

-- Téléphone vérifié (OTP) de l'utilisateur courant, tel que Supabase Auth le stocke.
create function public.current_phone_hash() returns text
language sql stable security definer set search_path = public as $$
  select public.phone_hash(phone) from auth.users where id = (select auth.uid())
$$;

-- R-PAR-01 : la protection automatique est-elle permise à cet utilisateur ?
-- Uniquement pour l'utilisateur courant : ne révèle rien sur les autres comptes.
create function public.protection_allowed() returns boolean
language sql stable security definer set search_path = public as $$
  select case public.age_band(p.birth_year, p.birth_month)
    when 'under12' then false
    when '12_15' then exists (
      select 1 from public.guardian_links g where g.minor_id = p.id and g.status = 'confirmed')
    else true
  end
  from public.profiles p
  where p.id = (select auth.uid())
$$;

-- ---------------------------------------------------------------------------
-- Triggers (jamais appelables via l'API)
-- ---------------------------------------------------------------------------

create function public.tg_profiles_guard() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.role is distinct from old.role then
    -- current_user vaut le propriétaire dans une fonction SECURITY DEFINER : on se fie aux claims du JWT.
    -- Autorisés : un admin, le service_role (Edge Functions), ou la maintenance directe sans JWT.
    if not (public.is_admin()
            or auth.role() = 'service_role'
            or ((select auth.uid()) is null and session_user in ('postgres', 'supabase_admin'))) then
      raise exception 'role_change_forbidden';
    end if;
    insert into public.audit_logs (actor, action, target, meta)
    values ((select auth.uid()), 'profile.role', new.id, jsonb_build_object('from', old.role, 'to', new.role));
  end if;
  if (new.birth_year, new.birth_month) is distinct from (old.birth_year, old.birth_month) then
    raise exception 'birth_date_immutable';
  end if;
  return new;
end $$;
create trigger profiles_guard before update on public.profiles for each row execute function public.tg_profiles_guard();

create function public.tg_guardian_links_audit() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if tg_op = 'INSERT' or new.status is distinct from old.status then
    insert into public.audit_logs (actor, action, target, meta)
    values ((select auth.uid()), 'guardian_link.' || new.status, new.id,
            jsonb_build_object('minor', new.minor_id, 'guardian', new.guardian_id));
  end if;
  return new;
end $$;
create trigger guardian_links_audit after insert or update on public.guardian_links
  for each row execute function public.tg_guardian_links_audit();

-- ---------------------------------------------------------------------------
-- RPC (écritures sensibles)
-- ---------------------------------------------------------------------------

-- Profil de l'utilisateur courant. La date de naissance ne change plus une fois posée ; le rôle n'est jamais modifiable ici.
create function public.set_profile(p_display_name text, p_locale text, p_birth_year int, p_birth_month int, p_is_adult_attested boolean default false)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := (select auth.uid());
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  if public.age_band(p_birth_year, p_birth_month) = 'under12' then raise exception 'under_minimum_age'; end if;
  if p_is_adult_attested and public.age_band(p_birth_year, p_birth_month) <> 'adult' then
    raise exception 'adult_attestation_inconsistent';
  end if;
  insert into public.profiles (id, display_name, locale, birth_year, birth_month, is_adult_attested)
  values (v_uid, p_display_name, p_locale, p_birth_year, p_birth_month, p_is_adult_attested)
  on conflict (id) do update
    set display_name = excluded.display_name,
        locale = excluded.locale,
        is_adult_attested = excluded.is_adult_attested or public.profiles.is_adult_attested,
        birth_year = excluded.birth_year,     -- le trigger refuse tout changement réel
        birth_month = excluded.birth_month;
end $$;

-- Un 12–15 ans invite un adulte référent (ADR-0013). Retourne l'id du lien. 3 invitations par jour au plus.
create function public.request_guardian_consent(p_guardian_phone text, p_relationship text)
returns uuid language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := (select auth.uid());
  v_profile public.profiles;
  v_hash text := public.phone_hash(p_guardian_phone);
  v_count int;
  v_id uuid;
begin
  select * into v_profile from public.profiles where id = v_uid;
  if v_profile is null then raise exception 'profile_required'; end if;
  if public.age_band(v_profile.birth_year, v_profile.birth_month) <> '12_15' then raise exception 'consent_not_required'; end if;
  if char_length(regexp_replace(coalesce(p_guardian_phone, ''), '[^0-9]', '', 'g')) not between 8 and 15 then
    raise exception 'invalid_phone';
  end if;
  if v_hash = public.current_phone_hash() then raise exception 'own_phone'; end if;
  if exists (select 1 from public.guardian_links where minor_id = v_uid and status = 'confirmed') then
    raise exception 'already_confirmed';
  end if;

  insert into public.rate_limits (user_id, action, count) values (v_uid, 'guardian_invite', 1)
  on conflict (user_id, action, day) do update set count = public.rate_limits.count + 1
  returning count into v_count;
  if v_count > 3 then raise exception 'rate_limited'; end if;

  -- Une nouvelle invitation remplace l'invitation en attente.
  update public.guardian_links set status = 'expired', guardian_phone = null
  where minor_id = v_uid and status = 'pending';

  insert into public.guardian_links (minor_id, guardian_phone, guardian_phone_hash, relationship)
  values (v_uid, '+' || regexp_replace(p_guardian_phone, '[^0-9]', '', 'g'), v_hash, p_relationship)
  returning id into v_id;
  return v_id;
end $$;

-- Demandes en attente adressées au numéro vérifié de l'utilisateur courant (écran de l'adulte référent).
create function public.pending_guardian_requests()
returns table (link_id uuid, minor_display_name text, minor_age_band text, relationship text, created_at timestamptz, expires_at timestamptz)
language sql stable security definer set search_path = public as $$
  select g.id, p.display_name, public.age_band(p.birth_year, p.birth_month), g.relationship, g.created_at, g.expires_at
  from public.guardian_links g
  join public.profiles p on p.id = g.minor_id
  where g.status = 'pending'
    and g.expires_at > now()
    and g.guardian_phone_hash = public.current_phone_hash()
$$;

-- L'adulte référent confirme ou refuse, depuis son propre téléphone vérifié (R-PAR-03, R-PAR-05).
create function public.confirm_guardian_consent(p_link_id uuid, p_accept boolean)
returns text language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := (select auth.uid());
  v_link public.guardian_links;
  v_guardian public.profiles;
  v_decision text := case when p_accept then 'confirmed' else 'refused' end;
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  select * into v_link from public.guardian_links where id = p_link_id for update;
  if v_link is null then raise exception 'not_found'; end if;
  -- Idempotence : la même décision rejouée par le même adulte ne change rien.
  if v_link.guardian_id = v_uid and v_link.status = v_decision then
    return v_link.status;
  end if;
  if v_link.status <> 'pending' then raise exception 'not_pending'; end if;
  if v_link.guardian_phone_hash is distinct from public.current_phone_hash() then raise exception 'not_found'; end if;
  if v_link.minor_id = v_uid then raise exception 'own_request'; end if;
  if v_link.expires_at <= now() then
    update public.guardian_links set status = 'expired', guardian_phone = null where id = p_link_id;
    return 'expired';
  end if;

  select * into v_guardian from public.profiles where id = v_uid;
  if p_accept then
    if v_guardian is null or not v_guardian.is_adult_attested
       or public.age_band(v_guardian.birth_year, v_guardian.birth_month) <> 'adult' then
      raise exception 'guardian_must_be_adult';
    end if;
    if (select count(*) from public.guardian_links where guardian_id = v_uid and status = 'confirmed') >= 6 then
      raise exception 'guardian_limit_reached';
    end if;
  end if;

  update public.guardian_links
  set status = v_decision,
      guardian_id = v_uid,
      decided_at = now(),
      guardian_phone = null
  where id = p_link_id;
  return v_decision;
end $$;

-- Retrait du consentement, par le jeune ou par l'adulte référent (R-PAR-04).
create function public.revoke_guardian_consent(p_link_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := (select auth.uid());
  v_link public.guardian_links;
begin
  select * into v_link from public.guardian_links where id = p_link_id for update;
  if v_link is null or v_uid not in (v_link.minor_id, v_link.guardian_id) then raise exception 'not_found'; end if;
  if v_link.status not in ('pending', 'confirmed') then return; end if;
  update public.guardian_links set status = 'revoked', guardian_phone = null, decided_at = now() where id = p_link_id;
end $$;

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------

alter table public.profiles enable row level security;
alter table public.guardian_links enable row level security;
alter table public.consents enable row level security;
alter table public.audit_logs enable row level security;
alter table public.rate_limits enable row level security;

create policy profiles_select_own on public.profiles for select to authenticated
  using (id = (select auth.uid()));

-- R-PAR-02 : le lien ne donne au parent aucun accès aux données du jeune ; il ne voit que le lien lui-même.
create policy guardian_links_select_parties on public.guardian_links for select to authenticated
  using (minor_id = (select auth.uid()) or guardian_id = (select auth.uid()));

create policy consents_select_own on public.consents for select to authenticated
  using (user_id = (select auth.uid()));
create policy consents_insert_own on public.consents for insert to authenticated
  with check (user_id = (select auth.uid()));

create policy audit_logs_select_admin on public.audit_logs for select to authenticated
  using ((select public.is_admin()));

-- ---------------------------------------------------------------------------
-- Droits : moindre privilège, en plus de la RLS
-- ---------------------------------------------------------------------------

revoke all on public.profiles, public.guardian_links, public.consents, public.audit_logs, public.rate_limits from anon, authenticated;
grant select on public.profiles, public.guardian_links to authenticated;
grant select, insert on public.consents to authenticated;
grant select on public.audit_logs to authenticated;

revoke execute on all functions in schema public from public, anon, authenticated;
grant execute on function
  public.set_profile(text, text, int, int, boolean),
  public.request_guardian_consent(text, text),
  public.pending_guardian_requests(),
  public.confirm_guardian_consent(uuid, boolean),
  public.revoke_guardian_consent(uuid),
  public.protection_allowed()
to authenticated;
-- Les helpers utilisés dans les politiques doivent rester exécutables par authenticated.
grant execute on function public.is_admin(), public.age_band(int, int, date), public.phone_hash(text) to authenticated;

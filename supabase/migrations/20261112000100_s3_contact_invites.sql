-- S3.03 · Accord des proches : invitation par SMS (Vonage), page « accepter / refuser / ne plus recevoir » (STOP), statut
-- relu par l'app. Le numéro du proche n'est jamais stocké en clair : seulement son hachage (quotas, liste STOP).
-- Écritures par les Edge Functions `contact-invite` et `contact-consent` (connexion directe, fonctions du schéma private).

create table public.contact_invites (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles (id) on delete cascade,
  -- Identifiant local du contact dans l'app (aucune donnée personnelle).
  client_ref text not null check (char_length(client_ref) between 8 and 64),
  label text check (char_length(label) <= 40),
  phone_hash text not null,
  token_hash text not null unique,
  status text not null default 'pending' check (status in ('pending', 'accepted', 'refused', 'stopped', 'expired')),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '7 days',
  decided_at timestamptz,
  unique (owner_id, client_ref),
  check (status = 'pending' or status = 'expired' or decided_at is not null)
);
create index contact_invites_phone on public.contact_invites (phone_hash);

-- Liste STOP : un numéro qui a demandé à ne plus rien recevoir de Cercle Rouge (invitations et SMS serveur).
create table private.contact_optouts (
  phone_hash text primary key,
  at timestamptz not null default now()
);

alter table public.contact_invites enable row level security;
alter table private.contact_optouts enable row level security;
create policy contact_invites_select_own on public.contact_invites for select to authenticated
  using (owner_id = (select auth.uid()));
revoke all on public.contact_invites from anon, authenticated;
grant select (client_ref, label, status, created_at, expires_at, decided_at) on public.contact_invites to authenticated;
revoke all on private.contact_optouts from public, anon, authenticated;

-- Statut des invitations du propriétaire (relu par l'app pour mettre à jour ses contacts).
create function public.my_contact_invites()
returns table (client_ref text, status text, decided_at timestamptz)
language sql stable security definer set search_path = public as $$
  select i.client_ref,
         case when i.status = 'pending' and i.expires_at <= now() then 'expired' else i.status end,
         i.decided_at
  from public.contact_invites i
  where i.owner_id = (select auth.uid())
$$;

-- Crée (ou renouvelle) l'invitation et rend le jeton UNE fois. Null si le numéro a demandé STOP.
-- Limites : 10 invitations par jour et par compte ; 1 par heure et par contact.
create function private.contact_invite_create(p_owner uuid, p_client_ref text, p_label text, p_phone text) returns text
language plpgsql security definer set search_path = public as $$
declare
  v_hash text := public.phone_hash(p_phone);
  v_token text;
  v_count int;
  v_prev public.contact_invites;
begin
  if not exists (select 1 from public.profiles where id = p_owner) then raise exception 'profile_required'; end if;
  if exists (select 1 from private.contact_optouts where phone_hash = v_hash) then return null; end if;
  select * into v_prev from public.contact_invites where owner_id = p_owner and client_ref = p_client_ref for update;
  if v_prev.id is not null and v_prev.status = 'accepted' and v_prev.phone_hash = v_hash then raise exception 'already_accepted'; end if;
  if v_prev.id is not null and v_prev.created_at > now() - interval '1 hour' then raise exception 'rate_limited'; end if;
  insert into public.rate_limits (user_id, action, count) values (p_owner, 'contact_invite', 1)
  on conflict (user_id, action, day) do update set count = public.rate_limits.count + 1
  returning count into v_count;
  if v_count > 10 then raise exception 'rate_limited'; end if;

  v_token := translate(encode(extensions.gen_random_bytes(24), 'base64'), '+/', '-_');
  insert into public.contact_invites (owner_id, client_ref, label, phone_hash, token_hash)
  values (p_owner, p_client_ref, nullif(trim(p_label), ''), v_hash, private.token_hash(v_token))
  on conflict (owner_id, client_ref) do update
    set label = excluded.label, phone_hash = excluded.phone_hash, token_hash = excluded.token_hash,
        status = 'pending', created_at = now(), expires_at = now() + interval '7 days', decided_at = null;
  return v_token;
end $$;

-- Lecture par le proche (page web) : prénom de la personne qui l'a choisi et statut. Null si jeton inconnu.
create function private.contact_invite_read(p_token text) returns jsonb
language sql stable security definer set search_path = public as $$
  select jsonb_build_object(
    'name', p.display_name,
    'status', case when i.status = 'pending' and i.expires_at <= now() then 'expired' else i.status end
  )
  from public.contact_invites i join public.profiles p on p.id = i.owner_id
  where i.token_hash = private.token_hash(p_token)
$$;

-- Décision du proche : accept, refuse, ou stop (refus + plus aucun message de Cercle Rouge vers ce numéro).
create function private.contact_invite_decide(p_token text, p_decision text) returns boolean
language plpgsql security definer set search_path = public as $$
declare i public.contact_invites;
begin
  if p_decision not in ('accept', 'refuse', 'stop') then return false; end if;
  select * into i from public.contact_invites where token_hash = private.token_hash(p_token) for update;
  if i.id is null then return false; end if;
  if p_decision = 'stop' then
    insert into private.contact_optouts (phone_hash) values (i.phone_hash) on conflict do nothing;
    update public.contact_invites set status = 'stopped', decided_at = now() where phone_hash = i.phone_hash;
    return true;
  end if;
  if i.status in ('stopped') or i.expires_at <= now() and i.status = 'pending' then return false; end if;
  update public.contact_invites
  set status = case p_decision when 'accept' then 'accepted' else 'refused' end, decided_at = now()
  where id = i.id;
  return true;
end $$;

-- Les SMS de secours ne partent jamais vers un numéro qui a demandé STOP.
create or replace function private.server_sms_allowed(p_owner uuid, p_alert_id uuid, p_phone text) returns boolean
language plpgsql security definer set search_path = public as $$
declare
  a public.alerts;
  v_hash text := public.phone_hash(p_phone);
begin
  if exists (select 1 from private.contact_optouts where phone_hash = v_hash) then return false; end if;
  select * into a from public.alerts where id = p_alert_id and owner_id = p_owner for update;
  if a.id is null then return false; end if;
  if a.status <> 'open' and (a.closed_at is null or a.closed_at < now() - interval '1 hour') then return false; end if;
  if (select count(distinct phone_hash) from private.server_sms_log where alert_id = a.id and phone_hash <> v_hash) >= 5 then return false; end if;
  if (select count(*) from private.server_sms_log where alert_id = a.id) >= 12 then return false; end if;
  if (select count(*) from private.server_sms_log where owner_id = p_owner and at > now() - interval '24 hours') >= 20 then return false; end if;
  insert into private.server_sms_log (alert_id, owner_id, phone_hash) values (a.id, p_owner, v_hash);
  return true;
end $$;

revoke execute on function public.my_contact_invites() from public, anon;
grant execute on function public.my_contact_invites() to authenticated;
revoke execute on function private.contact_invite_create(uuid, text, text, text), private.contact_invite_read(text),
  private.contact_invite_decide(text, text), private.server_sms_allowed(uuid, uuid, text) from public, anon, authenticated;

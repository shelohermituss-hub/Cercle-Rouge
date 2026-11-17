-- 1.21 : console d'administration (S7.07–S7.09, R-ADM-01..05).
-- Accès : rôle `admin` ET double authentification (session `aal2`, MFA TOTP de Supabase Auth). Sans les deux : `forbidden`.
-- Données : seulement des agrégats et des métadonnées de compte. JAMAIS de contenu, de position, de numéro ni de nom (R-ADM-03).
-- Chaque appel est inscrit au journal d'audit, désormais strictement en ajout (aucune modification ni suppression possible).

-- Administrateur avec MFA (niveau d'assurance aal2 dans le jeton).
create function private.is_admin_mfa() returns boolean
language sql stable security definer set search_path = public as $$
  select private.is_admin()
     and coalesce(nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'aal', '') = 'aal2'
$$;
revoke execute on function private.is_admin_mfa() from public, anon;
grant execute on function private.is_admin_mfa() to authenticated;

-- Code de support : 10 caractères tirés de l'identifiant, que l'utilisateur lit dans Réglages et donne au support.
create function private.support_code(p_user uuid) returns text
language sql immutable set search_path = public as $$
  select upper(substr(encode(extensions.digest('cercle-rouge/support/' || p_user::text, 'sha256'), 'hex'), 1, 10))
$$;
revoke execute on function private.support_code(uuid) from public, anon, authenticated;

create function public.my_support_code() returns text
language sql stable security definer set search_path = public as $$
  select private.support_code((select auth.uid()))
$$;

create function private.admin_guard(p_action text, p_meta jsonb default '{}'::jsonb) returns void
language plpgsql security definer set search_path = public as $$
begin
  if not private.is_admin_mfa() then raise exception 'forbidden'; end if;
  insert into public.audit_logs (actor, action, target, meta) values ((select auth.uid()), p_action, null, p_meta);
end $$;
revoke execute on function private.admin_guard(text, jsonb) from public, anon, authenticated;

-- Santé des envois : nombres par jour, canal, source et statut (aucun destinataire, aucune alerte identifiable).
create function public.admin_delivery_health(p_days int default 7)
returns table (day date, channel text, source text, status text, n bigint)
language plpgsql security definer set search_path = public as $$
begin
  perform private.admin_guard('admin.delivery_health', jsonb_build_object('days', p_days));
  return query
    select d.at::date, d.channel, d.source, d.status, count(*)
    from private.deliveries d
    where d.at > now() - make_interval(days => least(greatest(p_days, 1), 30))
    group by 1, 2, 3, 4 order by 1 desc, 2, 3, 4;
end $$;

-- Abus : actions qui atteignent leurs plafonds, par jour ; comptes repérés seulement par leur code de support.
create function public.admin_abuse(p_days int default 7)
returns table (day date, action text, accounts bigint, max_count int, top_code text)
language plpgsql security definer set search_path = public as $$
begin
  perform private.admin_guard('admin.abuse', jsonb_build_object('days', p_days));
  return query
    select r.day, r.action, count(*), max(r.count),
           (array_agg(private.support_code(r.user_id) order by r.count desc))[1]
    from public.rate_limits r
    where r.day > current_date - least(greatest(p_days, 1), 7)
    group by 1, 2 order by 1 desc, 4 desc;
end $$;

-- Support : état d'un compte d'après son code. Ni nom, ni numéro, ni contenu, ni position.
create function public.admin_support(p_code text)
returns table (
  created_at timestamptz, phone_verified boolean, age_band text, role text, alerts_30d bigint, open_alert boolean,
  active_session boolean, invites_accepted bigint, invites_pending bigint, limits_today jsonb
)
language plpgsql security definer set search_path = public as $$
declare
  v_user uuid;
begin
  perform private.admin_guard('admin.support', jsonb_build_object('code', upper(p_code)));
  select p.id into v_user from public.profiles p where private.support_code(p.id) = upper(trim(p_code));
  if v_user is null then raise exception 'not_found'; end if;
  return query
    select p.created_at,
           (u.phone_confirmed_at is not null),
           public.age_band(p.birth_year, p.birth_month),
           p.role,
           (select count(*) from public.alerts a where a.owner_id = v_user and a.opened_at > now() - interval '30 days'),
           exists (select 1 from public.alerts a where a.owner_id = v_user and a.status = 'open'),
           exists (select 1 from public.trips t where t.owner_id = v_user and t.status = 'active'),
           (select count(*) from public.contact_invites ci where ci.owner_id = v_user and ci.status = 'accepted'),
           (select count(*) from public.contact_invites ci where ci.owner_id = v_user and ci.status = 'pending'),
           coalesce((select jsonb_object_agg(r.action, r.count) from public.rate_limits r where r.user_id = v_user and r.day = current_date), '{}'::jsonb)
    from public.profiles p join auth.users u on u.id = p.id
    where p.id = v_user;
end $$;

-- Action de support : lever les plafonds du jour d'un compte (bloqué à tort). Inscrite au journal avec le code.
create function public.admin_reset_limits(p_code text)
returns int
language plpgsql security definer set search_path = public as $$
declare
  v_user uuid;
  n int;
begin
  perform private.admin_guard('admin.reset_limits', jsonb_build_object('code', upper(p_code)));
  select p.id into v_user from public.profiles p where private.support_code(p.id) = upper(trim(p_code));
  if v_user is null then raise exception 'not_found'; end if;
  update public.rate_limits set count = 0 where user_id = v_user and day = current_date;
  get diagnostics n = row_count;
  return n;
end $$;

-- Journal d'audit des actions admin (lecture seule, 200 dernières).
create function public.admin_audit(p_limit int default 100)
returns table (at timestamptz, action text, actor_code text, meta jsonb)
language plpgsql security definer set search_path = public as $$
begin
  perform private.admin_guard('admin.audit_read');
  return query
    select l.at, l.action, case when l.actor is null then null else private.support_code(l.actor) end, l.meta
    from public.audit_logs l order by l.at desc limit least(greatest(p_limit, 1), 200);
end $$;

-- Journal d'audit en ajout seulement : aucune modification ni suppression, même par le propriétaire des tables.
create function private.audit_logs_append_only() returns trigger
language plpgsql set search_path = public as $$
begin
  raise exception 'audit_logs_append_only';
end $$;
revoke execute on function private.audit_logs_append_only() from public, anon, authenticated;
create trigger audit_logs_no_update before update or delete on public.audit_logs
  for each row execute function private.audit_logs_append_only();

revoke execute on function public.my_support_code(), public.admin_delivery_health(int), public.admin_abuse(int),
  public.admin_support(text), public.admin_reset_limits(text), public.admin_audit(int) from public, anon;
grant execute on function public.my_support_code(), public.admin_delivery_health(int), public.admin_abuse(int),
  public.admin_support(text), public.admin_reset_limits(text), public.admin_audit(int) to authenticated;

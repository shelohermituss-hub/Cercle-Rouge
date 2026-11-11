-- S3 · Alertes via Supabase et liens de suivi des proches (docs/modules/backend-api.md, ADR-0022).
-- Comptes anonymes d'abord (pas encore de fournisseur SMS pour l'OTP) : un compte anonyme est un `authenticated` normal.
-- Règles : RLS partout ; écritures uniquement par RPC SECURITY DEFINER (search_path fixé, EXECUTE retiré à PUBLIC et anon),
-- idempotentes, FOR UPDATE ; jetons de suivi ≥ 192 bits dont seul le hachage est stocké ; lecture publique uniquement
-- par l'Edge Function `track` (service_role), via les fonctions du schéma `private`.

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

create table public.alerts (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles (id) on delete cascade,
  idempotency_key text not null check (char_length(idempotency_key) between 8 and 64),
  level int not null check (level between 0 and 4),
  reason text not null check (reason ~ '^[A-Z_]{2,32}$'),
  confidence text not null check (confidence in ('LOW', 'MEDIUM', 'HIGH')),
  trigger text not null check (trigger in ('SOS', 'SECRET_PHRASE', 'AUTOMATIC', 'TRIP', 'HEARTBEAT_LOSS')),
  silent boolean not null default false,
  status text not null default 'open' check (status in ('open', 'closed', 'expired')),
  closure text check (closure in ('USER_SAFE', 'FALSE_ALARM', 'EXPIRED')),
  opened_at timestamptz not null default now(),
  closed_at timestamptz,
  unique (owner_id, idempotency_key),
  check ((status = 'open') = (closed_at is null))
);
create index alerts_open on public.alerts (opened_at) where status = 'open';
create index alerts_closed on public.alerts (closed_at) where status <> 'open';

create table public.alert_events (
  id bigint generated always as identity primary key,
  alert_id uuid not null references public.alerts (id) on delete cascade,
  type text not null check (type ~ '^[a-z_]{2,32}$'),
  payload jsonb not null default '{}'::jsonb check (pg_column_size(payload) <= 2048),
  at timestamptz not null default now()
);
create index alert_events_alert on public.alert_events (alert_id, at);

create table public.alert_positions (
  id bigint generated always as identity primary key,
  alert_id uuid not null references public.alerts (id) on delete cascade,
  lat double precision not null check (lat between -90 and 90),
  lon double precision not null check (lon between -180 and 180),
  accuracy_m real check (accuracy_m >= 0),
  ts timestamptz not null
);
create index alert_positions_alert on public.alert_positions (alert_id, ts desc);

create table public.tracking_links (
  id uuid primary key default gen_random_uuid(),
  alert_id uuid not null references public.alerts (id) on delete cascade,
  token_hash text not null unique,
  -- Prénom du proche destinataire, pour le journal des consultations ; jamais son numéro.
  label text check (char_length(label) <= 40),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  revoked_at timestamptz,
  check (expires_at > created_at and expires_at <= created_at + interval '6 hours')
);
create index tracking_links_alert on public.tracking_links (alert_id);

create table public.link_views (
  id bigint generated always as identity primary key,
  link_id uuid not null references public.tracking_links (id) on delete cascade,
  at timestamptz not null default now()
);
create index link_views_link on public.link_views (link_id, at desc);

create table public.contact_actions (
  id bigint generated always as identity primary key,
  link_id uuid not null references public.tracking_links (id) on delete cascade,
  alert_id uuid not null references public.alerts (id) on delete cascade,
  action text not null check (action in ('TOOK_CONTACT', 'FALSE_ALARM', 'CALLED_EMERGENCY')),
  at timestamptz not null default now(),
  unique (link_id, action)
);
create index contact_actions_alert on public.contact_actions (alert_id, at);

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------

create function private.token_hash(p_token text) returns text
language sql immutable set search_path = public as $$
  select encode(extensions.digest(coalesce(p_token, ''), 'sha256'), 'hex')
$$;

-- Alerte du propriétaire courant, verrouillée ; erreur sinon (ne révèle pas l'existence des alertes des autres).
create function private.own_alert_for_update(p_alert_id uuid) returns public.alerts
language plpgsql security definer set search_path = public as $$
declare v public.alerts;
begin
  select * into v from public.alerts where id = p_alert_id and owner_id = (select auth.uid()) for update;
  if v.id is null then raise exception 'not_found'; end if;
  return v;
end $$;

-- ---------------------------------------------------------------------------
-- RPC du propriétaire (application)
-- ---------------------------------------------------------------------------

-- Crée l'alerte, ou renvoie l'existante pour la même clé (idempotente : l'app rejoue sans risque après une coupure).
-- R-PAR-01 : un 12–15 ans sans consentement confirmé ne peut ouvrir qu'un SOS manuel.
create function public.create_alert(
  p_idempotency_key text, p_level int, p_reason text, p_confidence text, p_trigger text, p_silent boolean
) returns uuid language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := (select auth.uid());
  v_id uuid;
  v_count int;
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  if not exists (select 1 from public.profiles where id = v_uid) then raise exception 'profile_required'; end if;
  select id into v_id from public.alerts where owner_id = v_uid and idempotency_key = p_idempotency_key;
  if v_id is not null then return v_id; end if;
  if p_trigger <> 'SOS' and not coalesce(public.protection_allowed(), false) then raise exception 'protection_not_allowed'; end if;
  if p_trigger = 'HEARTBEAT_LOSS' then raise exception 'server_only_trigger'; end if;

  insert into public.rate_limits (user_id, action, count) values (v_uid, 'create_alert', 1)
  on conflict (user_id, action, day) do update set count = public.rate_limits.count + 1
  returning count into v_count;
  if v_count > 30 then raise exception 'rate_limited'; end if;

  insert into public.alerts (owner_id, idempotency_key, level, reason, confidence, trigger, silent)
  values (v_uid, p_idempotency_key, p_level, p_reason, p_confidence, p_trigger, coalesce(p_silent, false))
  on conflict (owner_id, idempotency_key) do nothing
  returning id into v_id;
  if v_id is null then
    select id into v_id from public.alerts where owner_id = v_uid and idempotency_key = p_idempotency_key;
  else
    insert into public.alert_events (alert_id, type, payload) values (v_id, 'opened', jsonb_build_object('level', p_level));
  end if;
  return v_id;
end $$;

-- Escalade : le niveau ne descend jamais ; rejouer le même niveau ne change rien.
create function public.update_alert(p_alert_id uuid, p_level int, p_event text default null)
returns int language plpgsql security definer set search_path = public as $$
declare v public.alerts;
begin
  v := private.own_alert_for_update(p_alert_id);
  if v.status <> 'open' then raise exception 'alert_closed'; end if;
  if p_level > v.level then
    update public.alerts set level = p_level where id = v.id;
    insert into public.alert_events (alert_id, type, payload) values (v.id, 'escalated', jsonb_build_object('level', p_level));
  end if;
  if p_event is not null then
    insert into public.alert_events (alert_id, type) values (v.id, p_event);
  end if;
  return greatest(v.level, p_level);
end $$;

-- Position pendant une alerte ouverte seulement (R-LE-04). Au plus une position toutes les 5 s (R-BE-05).
create function public.add_alert_position(p_alert_id uuid, p_lat double precision, p_lon double precision, p_accuracy real, p_ts timestamptz)
returns void language plpgsql security definer set search_path = public as $$
declare v public.alerts;
begin
  v := private.own_alert_for_update(p_alert_id);
  if v.status <> 'open' then raise exception 'alert_closed'; end if;
  if p_lat not between -90 and 90 or p_lon not between -180 and 180 or p_accuracy < 0 then raise exception 'invalid_position'; end if;
  if p_ts > now() + interval '1 minute' or p_ts < v.opened_at - interval '1 minute' then raise exception 'invalid_timestamp'; end if;
  if exists (select 1 from public.alert_positions where alert_id = v.id and ts > p_ts - interval '5 seconds' and ts <= p_ts) then
    return;
  end if;
  insert into public.alert_positions (alert_id, lat, lon, accuracy_m, ts) values (v.id, p_lat, p_lon, p_accuracy, p_ts);
end $$;

-- Clôture (idempotente). Les liens de suivi sont révoqués : la page affiche « alerte terminée » sans plus de position.
create function public.close_alert(p_alert_id uuid, p_closure text)
returns void language plpgsql security definer set search_path = public as $$
declare v public.alerts;
begin
  v := private.own_alert_for_update(p_alert_id);
  if v.status <> 'open' then return; end if;
  if p_closure not in ('USER_SAFE', 'FALSE_ALARM') then raise exception 'invalid_closure'; end if;
  update public.alerts set status = 'closed', closure = p_closure, closed_at = now() where id = v.id;
  insert into public.alert_events (alert_id, type, payload) values (v.id, 'closed', jsonb_build_object('closure', p_closure));
  update public.tracking_links set revoked_at = now() where alert_id = v.id and revoked_at is null;
end $$;

-- Lien de suivi pour un proche : jeton aléatoire de 192 bits renvoyé UNE fois, seul son hachage est stocké. TTL 2 à 6 h.
create function public.create_tracking_link(p_alert_id uuid, p_label text default null, p_ttl_minutes int default 240)
returns text language plpgsql security definer set search_path = public as $$
declare
  v public.alerts;
  v_token text;
  v_count int;
begin
  v := private.own_alert_for_update(p_alert_id);
  if v.status <> 'open' then raise exception 'alert_closed'; end if;
  if p_ttl_minutes not between 120 and 360 then raise exception 'invalid_ttl'; end if;
  select count(*) into v_count from public.tracking_links where alert_id = v.id;
  if v_count >= 10 then raise exception 'rate_limited'; end if;
  v_token := translate(encode(extensions.gen_random_bytes(24), 'base64'), '+/', '-_');
  insert into public.tracking_links (alert_id, token_hash, label, expires_at)
  values (v.id, private.token_hash(v_token), nullif(trim(p_label), ''), now() + make_interval(mins => p_ttl_minutes));
  return v_token;
end $$;

create function public.revoke_tracking_link(p_link_id uuid)
returns void language plpgsql security definer set search_path = public as $$
begin
  update public.tracking_links l set revoked_at = now()
  from public.alerts a
  where l.id = p_link_id and a.id = l.alert_id and a.owner_id = (select auth.uid()) and l.revoked_at is null;
end $$;

-- Ce que les proches ont fait (« J'ai pris contact », « Alerte fausse »…) et combien de fois le lien a été ouvert.
create function public.alert_feedback(p_alert_id uuid)
returns table (action text, label text, at timestamptz)
language sql stable security definer set search_path = public as $$
  select c.action, l.label, c.at
  from public.contact_actions c
  join public.tracking_links l on l.id = c.link_id
  join public.alerts a on a.id = c.alert_id
  where c.alert_id = p_alert_id and a.owner_id = (select auth.uid())
  union all
  select 'VIEWED', l.label, max(v.at)
  from public.link_views v
  join public.tracking_links l on l.id = v.link_id
  join public.alerts a on a.id = l.alert_id
  where l.alert_id = p_alert_id and a.owner_id = (select auth.uid())
  group by l.id, l.label
$$;


-- ---------------------------------------------------------------------------
-- Fonctions pour l'Edge Function `track` (service_role uniquement, schéma private non exposé)
-- ---------------------------------------------------------------------------

-- Lecture par jeton : statut, prénom, niveau, dernières positions (20), chronologie. Null si jeton invalide, expiré ou révoqué
-- (la fonction ne distingue pas les cas : jetons non énumérables). Chaque lecture est journalisée.
create function private.track_read(p_token text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  l public.tracking_links;
  a public.alerts;
  v_name text;
begin
  select * into l from public.tracking_links where token_hash = private.token_hash(p_token);
  if l.id is null or l.expires_at <= now() then return null; end if;
  select * into a from public.alerts where id = l.alert_id;
  select display_name into v_name from public.profiles where id = a.owner_id;
  -- Une consultation par minute au plus dans le journal.
  if not exists (select 1 from public.link_views where link_id = l.id and at > now() - interval '1 minute') then
    insert into public.link_views (link_id) values (l.id);
  end if;
  return jsonb_build_object(
    'name', v_name,
    'status', case when l.revoked_at is not null and a.status = 'open' then 'revoked' else a.status end,
    'level', a.level,
    'reason', a.reason,
    'silent', a.silent,
    'opened_at', a.opened_at,
    'closed_at', a.closed_at,
    'closure', a.closure,
    'expires_at', l.expires_at,
    -- Positions seulement tant que l'alerte est ouverte et le lien valide (R-LE-04).
    'positions', case when a.status = 'open' and l.revoked_at is null then coalesce((
      select jsonb_agg(jsonb_build_object('lat', p.lat, 'lon', p.lon, 'acc', p.accuracy_m, 'ts', p.ts) order by p.ts desc)
      from (select * from public.alert_positions where alert_id = a.id order by ts desc limit 20) p), '[]'::jsonb)
      else '[]'::jsonb end,
    'events', coalesce((select jsonb_agg(jsonb_build_object('type', e.type, 'at', e.at) order by e.at)
                        from public.alert_events e where e.alert_id = a.id), '[]'::jsonb),
    'actions', coalesce((select jsonb_agg(c.action) from public.contact_actions c where c.link_id = l.id), '[]'::jsonb)
  );
end $$;

-- Action d'un proche (idempotente par lien et par action). Faux si le jeton n'est pas valable ou l'alerte close.
create function private.track_act(p_token text, p_action text) returns boolean
language plpgsql security definer set search_path = public as $$
declare l public.tracking_links;
begin
  if p_action not in ('TOOK_CONTACT', 'FALSE_ALARM', 'CALLED_EMERGENCY') then return false; end if;
  select * into l from public.tracking_links where token_hash = private.token_hash(p_token) for update;
  if l.id is null or l.expires_at <= now() or l.revoked_at is not null then return false; end if;
  if not exists (select 1 from public.alerts where id = l.alert_id and status = 'open') then return false; end if;
  insert into public.contact_actions (link_id, alert_id, action) values (l.id, l.alert_id, p_action)
  on conflict (link_id, action) do nothing;
  insert into public.alert_events (alert_id, type) values (l.alert_id, 'contact_' || lower(p_action));
  return true;
end $$;

-- ---------------------------------------------------------------------------
-- Tâches planifiées (S3.11) : purge et suppression de compte dans 20261111000300_s3_purge.sql
-- ---------------------------------------------------------------------------

-- Alerte ouverte depuis 24 h → expirée, liens révoqués.
create function private.expire_alerts() returns int
language plpgsql security definer set search_path = public as $$
declare n int;
begin
  with x as (
    update public.alerts set status = 'expired', closure = 'EXPIRED', closed_at = now()
    where status = 'open' and opened_at < now() - interval '24 hours'
    returning id
  ), r as (
    update public.tracking_links set revoked_at = now() where alert_id in (select id from x) and revoked_at is null
  )
  select count(*) into n from x;
  return n;
end $$;



-- ---------------------------------------------------------------------------
-- RLS et droits
-- ---------------------------------------------------------------------------

alter table public.alerts enable row level security;
alter table public.alert_events enable row level security;
alter table public.alert_positions enable row level security;
alter table public.tracking_links enable row level security;
alter table public.link_views enable row level security;
alter table public.contact_actions enable row level security;

create policy alerts_select_own on public.alerts for select to authenticated using (owner_id = (select auth.uid()));
create policy alert_events_select_own on public.alert_events for select to authenticated
  using (exists (select 1 from public.alerts a where a.id = alert_id and a.owner_id = (select auth.uid())));
create policy alert_positions_select_own on public.alert_positions for select to authenticated
  using (exists (select 1 from public.alerts a where a.id = alert_id and a.owner_id = (select auth.uid())));
create policy tracking_links_select_own on public.tracking_links for select to authenticated
  using (exists (select 1 from public.alerts a where a.id = alert_id and a.owner_id = (select auth.uid())));
create policy link_views_no_direct_access on public.link_views for all to authenticated using (false) with check (false);
create policy contact_actions_select_own on public.contact_actions for select to authenticated
  using (exists (select 1 from public.alerts a where a.id = alert_id and a.owner_id = (select auth.uid())));

revoke all on public.alerts, public.alert_events, public.alert_positions, public.tracking_links, public.link_views,
  public.contact_actions from anon, authenticated;
grant select on public.alerts, public.alert_events, public.alert_positions, public.contact_actions to authenticated;
-- tracking_links : pas de lecture directe de token_hash ; le propriétaire passe par alert_feedback().
grant select (id, alert_id, label, created_at, expires_at, revoked_at) on public.tracking_links to authenticated;

revoke execute on all functions in schema public from public, anon;
revoke execute on all functions in schema private from public, anon, authenticated;
grant execute on function
  public.create_alert(text, int, text, text, text, boolean),
  public.update_alert(uuid, int, text),
  public.add_alert_position(uuid, double precision, double precision, real, timestamptz),
  public.close_alert(uuid, text),
  public.create_tracking_link(uuid, text, int),
  public.revoke_tracking_link(uuid),
  public.alert_feedback(uuid)
to authenticated;
grant execute on function private.is_admin() to authenticated;
grant usage on schema private to service_role;
grant execute on function private.track_read(text), private.track_act(text, text) to service_role;

-- 1.9 · Tableau de bord des proches complet : état du téléphone (batterie, réseau) pendant l'alerte, lien de suivi de trajet
-- (S4.10), actions des autres proches visibles (R-DASH-05). Lecture toujours par jeton, via l'Edge Function `track`.

-- État du téléphone pendant l'alerte : écrit par l'app (set_alert_device), lu par la page des proches.
alter table public.alerts
  add column battery_pct int check (battery_pct between 0 and 100),
  add column online boolean,
  add column device_at timestamptz;

create function public.set_alert_device(p_alert_id uuid, p_battery int, p_online boolean)
returns void language plpgsql security definer set search_path = public as $$
declare a public.alerts;
begin
  a := private.own_alert_for_update(p_alert_id);
  if a.status <> 'open' then raise exception 'alert_closed'; end if;
  if p_battery is not null and p_battery not between 0 and 100 then raise exception 'invalid_battery'; end if;
  update public.alerts set battery_pct = p_battery, online = p_online, device_at = now() where id = a.id;
end $$;

-- Un lien peut suivre une alerte OU un trajet (jamais les deux).
alter table public.tracking_links
  add column trip_id uuid references public.trips (id) on delete cascade,
  alter column alert_id drop not null,
  add constraint tracking_links_one_target check (num_nonnulls(alert_id, trip_id) = 1);
create index tracking_links_trip on public.tracking_links (trip_id) where trip_id is not null;

-- Lien de suivi d'un trajet pour un proche : jeton de 192 bits renvoyé UNE fois, seul son hachage est stocké. TTL 2 à 6 h.
create function public.create_trip_link(p_trip_id uuid, p_label text default null, p_ttl_minutes int default 240)
returns text language plpgsql security definer set search_path = public as $$
declare
  t public.trips;
  v_token text;
begin
  select * into t from public.trips where id = p_trip_id and owner_id = (select auth.uid()) for update;
  if t.id is null then raise exception 'not_found'; end if;
  if t.status <> 'active' then raise exception 'trip_ended'; end if;
  if p_ttl_minutes not between 120 and 360 then raise exception 'invalid_ttl'; end if;
  if (select count(*) from public.tracking_links where trip_id = t.id) >= 10 then raise exception 'rate_limited'; end if;
  v_token := translate(encode(extensions.gen_random_bytes(24), 'base64'), '+/', '-_');
  insert into public.tracking_links (trip_id, token_hash, label, expires_at)
  values (t.id, private.token_hash(v_token), nullif(trim(p_label), ''), now() + make_interval(mins => p_ttl_minutes));
  return v_token;
end $$;

-- Lecture par jeton (alerte ou trajet). Null si jeton invalide, expiré ou révoqué (cas non distingués). Chaque lecture est journalisée.
-- Trajet : seule la dernière position du battement est visible, tant que le trajet est en cours (R-DASH-01, R-LE-04).
create or replace function private.track_read(p_token text) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
  l public.tracking_links;
  a public.alerts;
  t public.trips;
  v_name text;
begin
  select * into l from public.tracking_links where token_hash = private.token_hash(p_token);
  if l.id is null or l.expires_at <= now() then return null; end if;
  -- Une consultation par minute au plus dans le journal.
  if not exists (select 1 from public.link_views where link_id = l.id and at > now() - interval '1 minute') then
    insert into public.link_views (link_id) values (l.id);
  end if;

  if l.trip_id is not null then
    select * into t from public.trips where id = l.trip_id;
    if t.id is null then return null; end if;
    select display_name into v_name from public.profiles where id = t.owner_id;
    return jsonb_build_object(
      'kind', 'trip',
      'name', v_name,
      'status', case
        when l.revoked_at is not null then 'revoked'
        when t.status = 'ended' then 'ended'
        when t.lost_at is not null and t.recovered_at is null then 'lost'
        else 'active' end,
      'mode', t.mode,
      'started_at', t.started_at,
      'planned_arrival', t.planned_arrival,
      'ended_at', t.ended_at,
      'last_heartbeat_at', t.last_heartbeat_at,
      'battery', t.battery,
      'expires_at', l.expires_at,
      'positions', case when t.status = 'active' and l.revoked_at is null and t.last_lat is not null
        then jsonb_build_array(jsonb_build_object('lat', t.last_lat, 'lon', t.last_lon, 'acc', t.last_accuracy, 'ts', t.last_heartbeat_at))
        else '[]'::jsonb end,
      'events', '[]'::jsonb,
      'actions', '[]'::jsonb,
      'others', '[]'::jsonb
    );
  end if;

  select * into a from public.alerts where id = l.alert_id;
  select display_name into v_name from public.profiles where id = a.owner_id;
  return jsonb_build_object(
    'kind', 'alert',
    'name', v_name,
    'status', case when l.revoked_at is not null and a.status = 'open' then 'revoked' else a.status end,
    'level', a.level,
    'reason', a.reason,
    'silent', a.silent,
    'opened_at', a.opened_at,
    'closed_at', a.closed_at,
    'closure', a.closure,
    'expires_at', l.expires_at,
    'battery', case when a.status = 'open' then a.battery_pct end,
    'online', case when a.status = 'open' then a.online end,
    'device_at', case when a.status = 'open' then a.device_at end,
    -- Positions seulement tant que l'alerte est ouverte et le lien valide (R-LE-04).
    'positions', case when a.status = 'open' and l.revoked_at is null then coalesce((
      select jsonb_agg(jsonb_build_object('lat', p.lat, 'lon', p.lon, 'acc', p.accuracy_m, 'ts', p.ts) order by p.ts desc)
      from (select * from public.alert_positions where alert_id = a.id order by ts desc limit 20) p), '[]'::jsonb)
      else '[]'::jsonb end,
    'events', coalesce((select jsonb_agg(jsonb_build_object('type', e.type, 'at', e.at) order by e.at)
                        from public.alert_events e where e.alert_id = a.id), '[]'::jsonb),
    'actions', coalesce((select jsonb_agg(c.action) from public.contact_actions c where c.link_id = l.id), '[]'::jsonb),
    -- Ce que les autres proches ont fait (R-DASH-05) : prénom choisi par l'utilisateur, action, heure.
    'others', coalesce((
      select jsonb_agg(jsonb_build_object('label', o.label, 'action', c.action, 'at', c.at) order by c.at)
      from public.contact_actions c join public.tracking_links o on o.id = c.link_id
      where c.alert_id = a.id and c.link_id <> l.id), '[]'::jsonb)
  );
end $$;

revoke execute on function public.set_alert_device(uuid, int, boolean), public.create_trip_link(uuid, text, int) from public, anon;
grant execute on function public.set_alert_device(uuid, int, boolean), public.create_trip_link(uuid, text, int) to authenticated;

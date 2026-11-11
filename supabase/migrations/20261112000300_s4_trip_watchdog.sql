-- S4.07, S4.08 · Trajet suivi par le serveur (opt-in) : battement toutes les 2 min ; sans battement depuis 10 min, le serveur
-- prévient par SMS (Vonage) jusqu'à 3 proches choisis, avec la dernière position connue ; un 2ᵉ SMS quand le téléphone revient
-- (R-BE-08 : une fois par palier, annulée au retour). Réservé aux comptes au numéro vérifié (frein aux abus).
-- Numéros des proches chiffrés (pgcrypto, clé dans Supabase Vault), effacés à la fin du trajet ; position effacée aussi.

-- pg_net existe sur Supabase ; absent du PostgreSQL nu des tests locaux.
do $$
begin
  if exists (select 1 from pg_available_extensions where name = 'pg_net') then
    create extension if not exists pg_net;
  end if;
end $$;

-- Clés et réglages dans Vault (créés une fois ; jamais dans le dépôt).
do $$
begin
  if not exists (select 1 from vault.secrets where name = 'cr_trip_key') then
    perform vault.create_secret(encode(extensions.gen_random_bytes(32), 'hex'), 'cr_trip_key', 'Chiffrement des numéros des proches pendant un trajet');
  end if;
  if not exists (select 1 from vault.secrets where name = 'cr_cron_secret') then
    perform vault.create_secret(encode(extensions.gen_random_bytes(32), 'hex'), 'cr_cron_secret', 'Appel de trip-watchdog par pg_cron');
  end if;
end $$;

create function private.secret(p_name text) returns text
language sql stable security definer set search_path = public as $$
  select decrypted_secret from vault.decrypted_secrets where name = p_name
$$;

create table public.trips (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles (id) on delete cascade,
  idempotency_key text not null check (char_length(idempotency_key) between 8 and 64),
  status text not null default 'active' check (status in ('active', 'ended')),
  mode text not null check (mode in ('WALK', 'CAR', 'TAP_TAP', 'TAXI', 'MOTO')),
  planned_arrival timestamptz not null,
  started_at timestamptz not null default now(),
  ended_at timestamptz,
  last_heartbeat_at timestamptz not null default now(),
  last_lat double precision check (last_lat between -90 and 90),
  last_lon double precision check (last_lon between -180 and 180),
  last_accuracy real check (last_accuracy >= 0),
  battery int check (battery between 0 and 100),
  lost_at timestamptz,
  lost_notified_at timestamptz,
  recovered_at timestamptz,
  recovery_notified_at timestamptz,
  unique (owner_id, idempotency_key),
  check (planned_arrival <= started_at + interval '12 hours'),
  check ((status = 'active') = (ended_at is null))
);
create index trips_active on public.trips (last_heartbeat_at) where status = 'active';

-- Destinataires chiffrés (3 au plus), effacés à la fin du trajet.
create table private.trip_recipients (
  trip_id uuid not null references public.trips (id) on delete cascade,
  position int not null check (position between 0 and 2),
  phone_enc bytea,
  phone_hash text not null,
  lang text not null check (lang in ('fr', 'ht', 'en')),
  primary key (trip_id, position)
);

alter table public.trips enable row level security;
alter table private.trip_recipients enable row level security;
create policy trips_select_own on public.trips for select to authenticated using (owner_id = (select auth.uid()));
revoke all on public.trips from anon, authenticated;
grant select (id, status, mode, planned_arrival, started_at, ended_at, last_heartbeat_at, lost_at, recovered_at) on public.trips to authenticated;
revoke all on private.trip_recipients from public, anon, authenticated;

-- Démarre le suivi (idempotent). Numéro vérifié exigé ; 10 trajets par jour ; durée prévue ≤ 12 h.
create function public.start_trip(p_idempotency_key text, p_mode text, p_planned_arrival timestamptz)
returns uuid language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := (select auth.uid());
  v_id uuid;
  v_count int;
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  if not exists (select 1 from public.profiles where id = v_uid) then raise exception 'profile_required'; end if;
  if not exists (select 1 from auth.users where id = v_uid and phone_confirmed_at is not null) then raise exception 'phone_required'; end if;
  select id into v_id from public.trips where owner_id = v_uid and idempotency_key = p_idempotency_key;
  if v_id is not null then return v_id; end if;
  insert into public.rate_limits (user_id, action, count) values (v_uid, 'start_trip', 1)
  on conflict (user_id, action, day) do update set count = public.rate_limits.count + 1
  returning count into v_count;
  if v_count > 10 then raise exception 'rate_limited'; end if;
  -- Un seul trajet suivi à la fois : l'ancien se termine.
  update public.trips set status = 'ended', ended_at = now(), last_lat = null, last_lon = null where owner_id = v_uid and status = 'active';
  insert into public.trips (owner_id, idempotency_key, mode, planned_arrival)
  values (v_uid, p_idempotency_key, p_mode, p_planned_arrival)
  returning id into v_id;
  return v_id;
end $$;

-- Proches à prévenir (3 au plus), chiffrés aussitôt. Les numéros STOP sont ignorés.
create function public.set_trip_recipients(p_trip_id uuid, p_phones text[], p_langs text[])
returns int language plpgsql security definer set search_path = public as $$
declare
  t public.trips;
  i int;
  n int := 0;
  v_phone text;
begin
  select * into t from public.trips where id = p_trip_id and owner_id = (select auth.uid()) for update;
  if t.id is null then raise exception 'not_found'; end if;
  if t.status <> 'active' then raise exception 'trip_ended'; end if;
  if coalesce(array_length(p_phones, 1), 0) > 3 or coalesce(array_length(p_phones, 1), 0) <> coalesce(array_length(p_langs, 1), 0) then
    raise exception 'invalid_recipients';
  end if;
  update private.trip_recipients set phone_enc = null, phone_hash = '' where trip_id = t.id;
  for i in 1 .. coalesce(array_length(p_phones, 1), 0) loop
    v_phone := p_phones[i];
    if v_phone !~ '^\+[1-9][0-9]{7,14}$' then raise exception 'invalid_phone'; end if;
    if exists (select 1 from private.contact_optouts where phone_hash = public.phone_hash(v_phone)) then continue; end if;
    insert into private.trip_recipients (trip_id, position, phone_enc, phone_hash, lang)
    values (t.id, n, extensions.pgp_sym_encrypt(v_phone, private.secret('cr_trip_key')), public.phone_hash(v_phone),
            case when p_langs[i] in ('fr', 'ht', 'en') then p_langs[i] else 'fr' end)
    on conflict (trip_id, position) do update set phone_enc = excluded.phone_enc, phone_hash = excluded.phone_hash, lang = excluded.lang;
    n := n + 1;
  end loop;
  return n;
end $$;

-- Battement (toutes les 2 min) : dernière position et batterie. Après une perte signalée, marque le retour.
create function public.trip_heartbeat(p_trip_id uuid, p_lat double precision, p_lon double precision, p_accuracy real, p_battery int)
returns void language plpgsql security definer set search_path = public as $$
declare t public.trips;
begin
  select * into t from public.trips where id = p_trip_id and owner_id = (select auth.uid()) for update;
  if t.id is null then raise exception 'not_found'; end if;
  if t.status <> 'active' then raise exception 'trip_ended'; end if;
  update public.trips set
    last_heartbeat_at = now(),
    last_lat = coalesce(p_lat, last_lat), last_lon = coalesce(p_lon, last_lon), last_accuracy = coalesce(p_accuracy, last_accuracy),
    battery = coalesce(p_battery, battery),
    recovered_at = case when lost_notified_at is not null and recovered_at is null then now() else recovered_at end,
    lost_at = case when lost_notified_at is null then null else lost_at end
  where id = t.id;
end $$;

-- Fin du trajet : position et numéros effacés (R-TRIP-06, S4.11).
create function public.end_trip(p_trip_id uuid)
returns void language plpgsql security definer set search_path = public as $$
begin
  update public.trips set status = 'ended', ended_at = now(), last_lat = null, last_lon = null, last_accuracy = null
  where id = p_trip_id and owner_id = (select auth.uid()) and status = 'active';
  update private.trip_recipients set phone_enc = null, phone_hash = '' where trip_id = p_trip_id
    and exists (select 1 from public.trips where id = p_trip_id and owner_id = (select auth.uid()));
end $$;

-- Chaque minute (pg_cron) : marque les trajets silencieux depuis 10 min, termine ceux qui dépassent 12 h + 2 h,
-- et réveille l'Edge Function `trip-watchdog` s'il y a des SMS à envoyer.
create function private.trip_watchdog_tick() returns int
language plpgsql security definer set search_path = public as $$
declare n int;
begin
  update public.trips set status = 'ended', ended_at = now(), last_lat = null, last_lon = null
  where status = 'active' and planned_arrival < now() - interval '2 hours' and started_at < now() - interval '12 hours';
  update public.trips set lost_at = now()
  where status = 'active' and lost_at is null and lost_notified_at is null and last_heartbeat_at < now() - interval '10 minutes';
  select count(*) into n from public.trips
  where (lost_at is not null and lost_notified_at is null) or (recovered_at is not null and recovery_notified_at is null);
  if n > 0 and private.secret('cr_functions_url') is not null then
    perform net.http_post(
      url := private.secret('cr_functions_url') || '/trip-watchdog',
      headers := jsonb_build_object('Content-Type', 'application/json', 'x-cr-cron', private.secret('cr_cron_secret')),
      body := '{}'::jsonb
    );
  end if;
  return n;
end $$;

-- Pour trip-watchdog : SMS à envoyer (numéros déchiffrés), puis marquage. Jamais exposé à l'API.
create function private.trip_alerts_due()
returns table (trip_id uuid, kind text, name text, phone text, lang text, lat double precision, lon double precision, at timestamptz)
language sql security definer set search_path = public as $$
  select t.id,
         case when t.lost_at is not null and t.lost_notified_at is null then 'lost' else 'recovered' end,
         p.display_name,
         extensions.pgp_sym_decrypt(r.phone_enc, private.secret('cr_trip_key')),
         r.lang, t.last_lat, t.last_lon, t.last_heartbeat_at
  from public.trips t
  join public.profiles p on p.id = t.owner_id
  join private.trip_recipients r on r.trip_id = t.id and r.phone_enc is not null
  where (t.lost_at is not null and t.lost_notified_at is null) or (t.recovered_at is not null and t.recovery_notified_at is null)
$$;

create function private.trip_alerts_sent(p_trip_id uuid, p_kind text) returns void
language sql security definer set search_path = public as $$
  update public.trips set
    lost_notified_at = case when p_kind = 'lost' then now() else lost_notified_at end,
    recovery_notified_at = case when p_kind = 'recovered' then now() else recovery_notified_at end
  where id = p_trip_id
$$;

do $$
begin
  if exists (select 1 from pg_extension where extname = 'pg_cron') then
    perform cron.schedule('cr-trip-watchdog', '* * * * *', 'select private.trip_watchdog_tick()');
  end if;
end $$;

revoke execute on function public.start_trip(text, text, timestamptz), public.set_trip_recipients(uuid, text[], text[]),
  public.trip_heartbeat(uuid, double precision, double precision, real, int), public.end_trip(uuid) from public, anon;
grant execute on function public.start_trip(text, text, timestamptz), public.set_trip_recipients(uuid, text[], text[]),
  public.trip_heartbeat(uuid, double precision, double precision, real, int), public.end_trip(uuid) to authenticated;
revoke execute on function private.secret(text), private.trip_watchdog_tick(), private.trip_alerts_due(),
  private.trip_alerts_sent(uuid, text) from public, anon, authenticated;

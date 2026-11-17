-- 1.17 : surveillance de la PROTECTION par le serveur (PDF 13.1, S7.03, S4.09) et racine du journal anti-effacement.
-- La protection armée (hors trajet) devient une session `trips` de mode PROTECTION : même battement, même chien de garde,
-- mêmes proches choisis, SMS « dernière activité » si le téléphone se tait 10 min (fermeture forcée, téléphone cassé ou éteint).
-- Une seule session suivie à la fois : un trajet qui démarre remplace la session de protection, et inversement.

alter table public.trips drop constraint trips_mode_check;
alter table public.trips add constraint trips_mode_check
  check (mode in ('WALK', 'CAR', 'TAP_TAP', 'TAXI', 'MOTO', 'BUS', 'TRAIN', 'PROTECTION'));

-- Pour trip-watchdog : comme trip_alerts_due, avec le mode (le texte du SMS diffère pour la protection).
create function private.watch_alerts_due()
returns table (trip_id uuid, kind text, mode text, name text, phone text, lang text, lat double precision, lon double precision, at timestamptz)
language sql security definer set search_path = public as $$
  select t.id,
         case when t.lost_at is not null and t.lost_notified_at is null then 'lost' else 'recovered' end,
         t.mode,
         p.display_name,
         extensions.pgp_sym_decrypt(r.phone_enc, private.secret('cr_trip_key')),
         r.lang, t.last_lat, t.last_lon, t.last_heartbeat_at
  from public.trips t
  join public.profiles p on p.id = t.owner_id
  join private.trip_recipients r on r.trip_id = t.id and r.phone_enc is not null
  where (t.lost_at is not null and t.lost_notified_at is null) or (t.recovered_at is not null and t.recovery_notified_at is null)
$$;
revoke execute on function private.watch_alerts_due() from public, anon, authenticated;

-- Vue « proche » : une session de protection apparaît comme telle (pas comme un trajet).
create or replace function public.my_watched()
returns table (
  kind text, owner_name text, status text, level int, reason text, since timestamptz,
  battery int, online boolean, lat double precision, lon double precision, accuracy real, position_at timestamptz,
  planned_arrival timestamptz
)
language sql stable security definer set search_path = public as $$
  with me as (select public.current_phone_hash() as h),
  owners as (
    select distinct ci.owner_id
    from public.contact_invites ci, me
    where me.h is not null and ci.status = 'accepted' and ci.phone_hash = me.h
  )
  select 'alert'::text, p.display_name, a.status, a.level, a.reason, a.opened_at, a.battery_pct, a.online,
         pos.lat, pos.lon, pos.accuracy_m, pos.ts, null::timestamptz
  from public.alerts a
  join owners o on o.owner_id = a.owner_id
  join public.profiles p on p.id = a.owner_id
  left join lateral (
    select lat, lon, accuracy_m, ts from public.alert_positions where alert_id = a.id order by ts desc limit 1
  ) pos on true
  where a.status = 'open' and a.level >= 2
  union all
  select case when t.mode = 'PROTECTION' then 'protection' else 'trip' end, p.display_name,
         case when t.lost_at is not null and t.recovered_at is null then 'lost' else 'active' end, 0, null::text,
         t.started_at, t.battery, null::boolean,
         -- Protection seule : la position n'est montrée qu'en cas de perte de contact (données minimales, R-DASH-01).
         case when t.mode <> 'PROTECTION' or t.lost_at is not null then t.last_lat end,
         case when t.mode <> 'PROTECTION' or t.lost_at is not null then t.last_lon end,
         case when t.mode <> 'PROTECTION' or t.lost_at is not null then t.last_accuracy end,
         t.last_heartbeat_at,
         case when t.mode = 'PROTECTION' then null else t.planned_arrival end
  from public.trips t
  join owners o on o.owner_id = t.owner_id
  join public.profiles p on p.id = t.owner_id
  where t.status = 'active'
    and exists (select 1 from private.trip_recipients r, me where r.trip_id = t.id and r.phone_hash = me.h and r.phone_enc is not null)
$$;

-- Racine du journal chaîné du téléphone, déposée pendant une alerte ou une session suivie : si le journal est effacé ou
-- réécrit ensuite sur le téléphone, la racine horodatée par le serveur le montre (anti-effacement). Ajout seulement.
create table public.log_roots (
  id bigint generated always as identity primary key,
  owner_id uuid not null references public.profiles (id) on delete cascade,
  seq bigint not null check (seq >= 0),
  root text not null check (root ~ '^[0-9a-f]{64}$'),
  received_at timestamptz not null default now()
);
create index log_roots_owner on public.log_roots (owner_id, received_at desc);
alter table public.log_roots enable row level security;
create policy log_roots_select_own on public.log_roots for select to authenticated using (owner_id = (select auth.uid()));
revoke insert, update, delete on public.log_roots from anon, authenticated;

create function public.register_log_root(p_seq bigint, p_root text)
returns void language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := (select auth.uid());
  v_count int;
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  if not exists (select 1 from public.profiles where id = v_uid) then raise exception 'profile_required'; end if;
  if p_root !~ '^[0-9a-f]{64}$' or p_seq is null or p_seq < 0 then raise exception 'invalid_root'; end if;
  -- Même racine déjà déposée : rien (idempotent). Plafond : 600 dépôts par jour (une alerte d'une heure en dépose ~60).
  if exists (select 1 from public.log_roots where owner_id = v_uid and seq = p_seq and root = p_root) then return; end if;
  insert into public.rate_limits (user_id, action, count) values (v_uid, 'log_root', 1)
  on conflict (user_id, action, day) do update set count = public.rate_limits.count + 1
  returning count into v_count;
  if v_count > 600 then raise exception 'rate_limited'; end if;
  insert into public.log_roots (owner_id, seq, root) values (v_uid, p_seq, p_root);
end $$;

-- Minimisation : les racines de plus de 30 jours partent avec la purge horaire existante.
create or replace function private.purge_expired() returns void
language plpgsql security definer set search_path = public as $$
begin
  delete from public.alert_positions p using public.alerts a
  where p.alert_id = a.id and a.status <> 'open' and a.closed_at < now() - interval '24 hours';
  delete from public.alerts where status <> 'open' and closed_at < now() - interval '30 days';
  delete from public.rate_limits where day < current_date - 7;
  delete from public.log_roots where received_at < now() - interval '30 days';
end $$;

revoke execute on function public.register_log_root(bigint, text) from public, anon;
grant execute on function public.register_log_root(bigint, text) to authenticated;
revoke execute on function public.my_watched() from public, anon;
grant execute on function public.my_watched() to authenticated;

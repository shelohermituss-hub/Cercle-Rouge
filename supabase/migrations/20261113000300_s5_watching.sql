-- S3.09 · Vue « proche » dans l'application : un proche inscrit (numéro vérifié) voit les alertes et trajets en cours de ceux qui
-- l'ont choisi et dont il a accepté l'invitation. Aucun historique : seulement ce qui est ouvert maintenant.
-- Règles de visibilité (données minimales, R-DASH-01) :
--  · alerte : ouverte et de niveau 2 ou plus (celles qui partent vers les proches), avec sa dernière position ;
--  · trajet : en cours, et seulement si ce proche fait partie des destinataires choisis pour ce trajet.
create function public.my_watched()
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
  select 'trip'::text, p.display_name,
         case when t.lost_at is not null and t.recovered_at is null then 'lost' else 'active' end, 0, null::text,
         t.started_at, t.battery, null::boolean, t.last_lat, t.last_lon, t.last_accuracy, t.last_heartbeat_at, t.planned_arrival
  from public.trips t
  join owners o on o.owner_id = t.owner_id
  join public.profiles p on p.id = t.owner_id
  where t.status = 'active'
    and exists (select 1 from private.trip_recipients r, me where r.trip_id = t.id and r.phone_hash = me.h and r.phone_enc is not null)
$$;

revoke execute on function public.my_watched() from public, anon;
grant execute on function public.my_watched() to authenticated;

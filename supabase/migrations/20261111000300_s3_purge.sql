-- S3 · Suppressions : purge planifiée et suppression de compte (séparées de s3_alerts pour une validation explicite).
-- Toutes les suppressions de données du Sprint 3 sont ici.

-- Suppression du compte (S3.12) : toutes les données en cascade, puis le compte Auth lui-même.
create function public.delete_my_account()
returns void language plpgsql security definer set search_path = public as $$
declare v_uid uuid := (select auth.uid());
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;
  insert into public.audit_logs (actor, action, target) values (null, 'account.deleted', null);
  delete from auth.users where id = v_uid;
end $$;

-- Minimisation (R-BE-03) : positions 24 h après la clôture ; alertes (et tout ce qui en dépend) 30 jours après.
create function private.purge_expired() returns void
language plpgsql security definer set search_path = public as $$
begin
  delete from public.alert_positions p using public.alerts a
  where p.alert_id = a.id and a.status <> 'open' and a.closed_at < now() - interval '24 hours';
  delete from public.alerts where status <> 'open' and closed_at < now() - interval '30 days';
  delete from public.rate_limits where day < current_date - 7;
end $$;

-- pg_cron existe sur Supabase ; absent du PostgreSQL nu des tests locaux, d'où la condition.
do $$
begin
  if exists (select 1 from pg_available_extensions where name = 'pg_cron') then
    create extension if not exists pg_cron;
    perform cron.schedule('cr-expire-alerts', '*/15 * * * *', 'select private.expire_alerts()');
    perform cron.schedule('cr-purge-expired', '17 * * * *', 'select private.purge_expired()');
  end if;
end $$;

revoke execute on function public.delete_my_account(), private.purge_expired() from public, anon;
revoke execute on function private.purge_expired() from authenticated;
grant execute on function public.delete_my_account() to authenticated;

-- S1 · Durcissement après le premier Security Advisor du vrai projet (2026-11-11). Sans suppression (aucune instruction DROP).
-- 1) Le helper is_admin() n'a pas à être appelable via l'API (/rest/v1/rpc) : copie dans un schéma `private`, non exposé ;
--    l'ancienne fonction public.is_admin() n'est plus utilisée nulle part et plus exécutable par personne.
-- 2) rate_limits n'est lu ni écrit que par les RPC SECURITY DEFINER : politique explicite « aucun accès direct ».

create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

create function private.is_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.profiles where id = (select auth.uid()) and role = 'admin')
$$;
revoke execute on function private.is_admin() from public, anon;
grant execute on function private.is_admin() to authenticated;

alter policy audit_logs_select_admin on public.audit_logs using ((select private.is_admin()));

create or replace function public.tg_profiles_guard() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.role is distinct from old.role then
    if not (private.is_admin()
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
revoke execute on function public.tg_profiles_guard() from public, anon, authenticated;

revoke execute on function public.is_admin() from public, anon, authenticated;

create policy rate_limits_no_direct_access on public.rate_limits for all to authenticated
  using (false) with check (false);

-- Tests de sécurité (R-BE-09) : impersonation de chaque rôle dans une transaction ANNULÉE à la fin.
-- Une assertion échouée lève une exception et arrête le script (psql -v ON_ERROR_STOP=1).
-- Lancer : supabase/scripts/test-local.sh (PostgreSQL local) ou psql sur une base de développement Supabase.

\set ON_ERROR_STOP 1
\o /dev/null
\set minor    '11111111-1111-1111-1111-111111111111'
\set guardian '22222222-2222-2222-2222-222222222222'
\set stranger '33333333-3333-3333-3333-333333333333'
\set teen     '44444444-4444-4444-4444-444444444444'
\set admin    '55555555-5555-5555-5555-555555555555'

begin;

-- Outils d'assertion (supprimés par le ROLLBACK final)
create schema tests;
create function tests.expect(p_ok boolean, p_msg text) returns void language plpgsql as $$
begin
  if not coalesce(p_ok, false) then raise exception 'ÉCHEC : %', p_msg; end if;
end $$;
create function tests.expect_error(p_sql text, p_like text) returns void language plpgsql as $$
begin
  begin
    execute p_sql;
  exception when others then
    if sqlerrm like p_like then return; end if;
    raise exception 'ÉCHEC : [%] devait échouer avec « % », a échoué avec « % »', p_sql, p_like, sqlerrm;
  end;
  raise exception 'ÉCHEC : [%] devait échouer avec « % » mais a réussi', p_sql, p_like;
end $$;
create function tests.as_user(p_uid uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p_uid, 'role', 'authenticated')::text, true)
$$;
grant usage on schema tests to anon, authenticated;
grant execute on all functions in schema tests to anon, authenticated;

-- Comptes vérifiés par OTP (Supabase stocke le E.164 sans « + »)
insert into auth.users (id, phone) values
  (:'minor', '50930000001'), (:'guardian', '50930000002'), (:'stranger', '50930000003'),
  (:'teen', '50930000004'), (:'admin', '50930000005');

-- ===== Profils =====
select tests.as_user(:'minor');    set local role authenticated; select public.set_profile('Naïka', 'ht', 2013, 5); reset role;
select tests.as_user(:'guardian'); set local role authenticated; select public.set_profile('Maman', 'fr', 1985, 2, true); reset role;
select tests.as_user(:'stranger'); set local role authenticated; select public.set_profile('Inconnu', 'fr', 1990, 7, true); reset role;
select tests.as_user(:'teen');     set local role authenticated; select public.set_profile('Jo', 'fr', 2010, 1); reset role;
select tests.as_user(:'admin');    set local role authenticated; select public.set_profile('Admin', 'fr', 1980, 1, true); reset role;
select set_config('request.jwt.claims', '', true);
update public.profiles set role = 'admin' where id = :'admin'; -- maintenance directe, sans JWT

-- ===== anon : aucun accès =====
set local role anon;
select tests.expect_error('select * from public.profiles', '%permission denied%');
select tests.expect_error('select * from public.guardian_links', '%permission denied%');
select tests.expect_error('select * from public.consents', '%permission denied%');
select tests.expect_error($$select public.request_guardian_consent('+50930000002', 'parent')$$, '%permission denied%');
select tests.expect_error($$select public.protection_allowed()$$, '%permission denied%');
select tests.expect_error($$select private.is_admin()$$, '%permission denied%');
reset role;

-- ===== Âge et rôle =====
select tests.as_user(:'stranger'); set local role authenticated;
select tests.expect_error($$select public.set_profile('Bébé', 'fr', 2020, 1)$$, '%under_minimum_age%');
select tests.expect_error($$select public.set_profile('Jeune', 'fr', 2012, 1)$$, '%birth_date_immutable%');
select tests.expect_error($$select public.set_profile('Jeune', 'fr', 2012, 1, true)$$, '%adult_attestation_inconsistent%');
select tests.expect_error($$update public.profiles set role = 'admin' where id = auth.uid()$$, '%permission denied%');
select tests.expect_error($$select public.is_admin()$$, '%permission denied%');   -- remplacée par private.is_admin()
select tests.expect_error($$select * from public.rate_limits$$, '%permission denied%');
select tests.expect((select count(*) from public.profiles) = 1, 'un utilisateur ne voit que son propre profil');
reset role;

-- ===== R-PAR-01 : protection avant consentement =====
select tests.as_user(:'minor'); set local role authenticated;
select tests.expect(public.protection_allowed() = false, 'un 12–15 ans sans consentement n''a pas de protection automatique');
select tests.expect_error($$select public.request_guardian_consent('+50930000001', 'parent')$$, '%own_phone%');
select tests.expect_error($$select public.request_guardian_consent('123', 'parent')$$, '%invalid_phone%');
select tests.expect_error($$select public.request_guardian_consent('+50930000002', 'cousin')$$, '%check constraint%');
select public.request_guardian_consent('+509 3000-0002', 'parent');
select tests.expect((select count(*) from public.guardian_links where status = 'pending') = 1, 'le jeune voit sa demande');
reset role;

select tests.as_user(:'teen'); set local role authenticated;
select tests.expect(public.protection_allowed() = true, 'un 16–17 ans n''a pas besoin de consentement');
select tests.expect_error($$select public.request_guardian_consent('+50930000002', 'parent')$$, '%consent_not_required%');
reset role;

-- ===== R-PAR-03 : seul le numéro invité peut confirmer =====
select tests.as_user(:'stranger'); set local role authenticated;
select tests.expect((select count(*) from public.pending_guardian_requests()) = 0, 'un inconnu ne voit pas la demande');
select tests.expect((select count(*) from public.guardian_links) = 0, 'un inconnu ne voit aucun lien');
reset role;
-- Même en connaissant l'identifiant de la demande, un inconnu ne peut pas la confirmer.
do $$
declare v_id uuid := (select id from public.guardian_links where status = 'pending');
begin
  perform tests.as_user('33333333-3333-3333-3333-333333333333');
  set local role authenticated;
  perform tests.expect_error(format('select public.confirm_guardian_consent(%L, true)', v_id), '%not_found%');
  reset role;
end $$;

select tests.as_user(:'guardian'); set local role authenticated;
select tests.expect((select count(*) from public.pending_guardian_requests()) = 1, 'l''adulte invité voit la demande');
select tests.expect((select minor_display_name from public.pending_guardian_requests()) = 'Naïka', 'avec le prénom du jeune');
select tests.expect((select status from public.guardian_links limit 1) is null, 'l''adulte ne voit pas le lien avant de décider');
select tests.expect(
  public.confirm_guardian_consent((select link_id from public.pending_guardian_requests()), true) = 'confirmed',
  'l''adulte référent confirme');
select tests.expect((select count(*) from public.guardian_links where status = 'confirmed') = 1, 'l''adulte voit le lien confirmé');
select tests.expect((select guardian_phone from public.guardian_links) is null, 'le numéro en clair est effacé après la décision');
-- R-PAR-02 : aucun accès aux données du jeune
select tests.expect((select count(*) from public.profiles) = 1, 'l''adulte ne voit pas le profil du jeune');
reset role;

select tests.as_user(:'minor'); set local role authenticated;
select tests.expect(public.protection_allowed() = true, 'protection débloquée après confirmation');
select tests.expect_error($$select public.request_guardian_consent('+50930000003', 'autre')$$, '%already_confirmed%');
select tests.expect_error($$update public.guardian_links set status = 'pending'$$, '%permission denied%');
select tests.expect_error($$delete from public.guardian_links$$, '%permission denied%');
reset role;

-- ===== Journal d'audit =====
select tests.as_user(:'minor'); set local role authenticated;
select tests.expect((select count(*) from public.audit_logs) = 0, 'un utilisateur ne lit pas le journal d''audit');
select tests.expect_error($$insert into public.audit_logs (action) values ('faux')$$, '%permission denied%');
reset role;
select tests.as_user(:'admin'); set local role authenticated;
select tests.expect((select count(*) from public.audit_logs where action = 'guardian_link.confirmed') = 1, 'l''admin lit la confirmation');
select tests.expect_error($$delete from public.audit_logs$$, '%permission denied%');
reset role;

-- ===== R-PAR-04 : retrait =====
select tests.as_user(:'guardian'); set local role authenticated;
select public.revoke_guardian_consent((select id from public.guardian_links where status = 'confirmed'));
reset role;
select tests.as_user(:'minor'); set local role authenticated;
select tests.expect(public.protection_allowed() = false, 'retrait du consentement : retour au mode restreint');
reset role;

-- ===== Limites : 3 invitations par jour, invitation expirée =====
select tests.as_user(:'minor'); set local role authenticated;
select public.request_guardian_consent('+50930000002', 'parent');   -- 2e invitation du jour
select public.request_guardian_consent('+50930000002', 'parent');   -- 3e : remplace la précédente
select tests.expect((select count(*) from public.guardian_links where status = 'pending') = 1, 'une seule invitation en attente');
select tests.expect_error($$select public.request_guardian_consent('+50930000002', 'parent')$$, '%rate_limited%');
reset role;
select set_config('request.jwt.claims', '', true);
update public.guardian_links set expires_at = now() - interval '1 minute' where status = 'pending';
select tests.as_user(:'guardian'); set local role authenticated;
select tests.expect((select count(*) from public.pending_guardian_requests()) = 0, 'une demande expirée n''est plus proposée');
reset role;
do $$
declare v_id uuid := (select id from public.guardian_links where status = 'pending');
begin
  perform tests.as_user('22222222-2222-2222-2222-222222222222');
  set local role authenticated;
  perform tests.expect(public.confirm_guardian_consent(v_id, true) = 'expired', 'une demande expirée ne peut pas être confirmée');
  reset role;
end $$;

-- ===== R-PAR-05 : 6 jeunes au plus par adulte référent =====
select set_config('request.jwt.claims', '', true);
do $$
declare i int; v uuid;
begin
  for i in 1..7 loop
    v := gen_random_uuid();
    insert into auth.users (id, phone) values (v, '5093100000' || i);
    insert into public.profiles (id, display_name, birth_year, birth_month) values (v, 'Jeune ' || i, 2013, 1);
    if i <= 6 then
      insert into public.guardian_links (minor_id, guardian_phone_hash, guardian_id, relationship, status, decided_at)
      values (v, public.phone_hash('50930000002'), '22222222-2222-2222-2222-222222222222', 'autre', 'confirmed', now());
    else
      insert into public.guardian_links (minor_id, guardian_phone_hash, relationship)
      values (v, public.phone_hash('50930000002'), 'autre');
    end if;
  end loop;
end $$;
do $$
declare v_id uuid := (select g.id from public.guardian_links g join public.profiles p on p.id = g.minor_id
                      where p.display_name = 'Jeune 7');
begin
  perform tests.as_user('22222222-2222-2222-2222-222222222222');
  set local role authenticated;
  perform tests.expect_error(format('select public.confirm_guardian_consent(%L, true)', v_id), '%guardian_limit_reached%');
  reset role;
end $$;

-- ===== Consentements : append-only, chacun pour soi =====
select tests.as_user(:'minor'); set local role authenticated;
insert into public.consents (user_id, scope, granted, text_version, locale) values (auth.uid(), 'MICROPHONE', true, 'v1', 'ht');
select tests.expect_error(
  $$insert into public.consents (user_id, scope, granted, text_version, locale) values ('22222222-2222-2222-2222-222222222222', 'MICROPHONE', true, 'v1', 'fr')$$,
  '%row-level security%');
select tests.expect_error($$update public.consents set granted = false$$, '%permission denied%');
select tests.expect_error($$delete from public.consents$$, '%permission denied%');
reset role;

-- ===== Fonctions : jamais exécutables par anon ni par PUBLIC =====
select tests.expect(
  not exists (
    select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname in ('public', 'private') and p.prosecdef
      and (has_function_privilege('anon', p.oid, 'execute'))),
  'aucune fonction SECURITY DEFINER exécutable par anon');
select tests.expect(
  not exists (
    select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname in ('public', 'private') and p.prosecdef
      and not exists (select 1 from unnest(coalesce(p.proconfig, '{}')) c where c like 'search_path=%')),
  'toutes les fonctions SECURITY DEFINER fixent search_path');
select tests.expect(
  not exists (select 1 from pg_tables where schemaname = 'public' and not rowsecurity),
  'RLS activée sur toutes les tables de public');

\echo '✅ security.sql : toutes les assertions passent'
rollback;

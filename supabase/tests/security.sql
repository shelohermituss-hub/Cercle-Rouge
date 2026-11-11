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
grant usage on schema tests to anon, authenticated, service_role;
grant execute on all functions in schema tests to anon, authenticated, service_role;

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

-- ===== S3 · Alertes, positions, liens de suivi =====
set local role anon;
select tests.expect_error($$select public.create_alert('k-anon-0001', 2, 'SOS', 'HIGH', 'SOS', false)$$, '%permission denied%');
select tests.expect_error($$select private.track_read('x')$$, '%permission denied%');
reset role;

select tests.as_user(:'stranger'); set local role authenticated;
create temp table s3 (k text primary key, v text) on commit drop;
grant all on s3 to authenticated, service_role;
insert into s3 values ('alert', public.create_alert('k-stranger-001', 2, 'SOS', 'HIGH', 'SOS', false)::text);
select tests.expect(public.create_alert('k-stranger-001', 2, 'SOS', 'HIGH', 'SOS', false)::text = (select v from s3 where k = 'alert'),
  'create_alert est idempotente (même clé → même alerte)');
select tests.expect_error($$select public.create_alert('k-stranger-hb', 2, 'SOS', 'HIGH', 'HEARTBEAT_LOSS', false)$$, '%server_only_trigger%');
select tests.expect_error($$select public.create_alert('k-stranger-bad', 9, 'SOS', 'HIGH', 'SOS', false)$$, '%check constraint%');
select tests.expect(public.update_alert((select v::uuid from s3 where k = 'alert'), 3) = 3, 'escalade 2 → 3');
select tests.expect(public.update_alert((select v::uuid from s3 where k = 'alert'), 1) = 3, 'le niveau ne descend jamais');
select public.add_alert_position((select v::uuid from s3 where k = 'alert'), 18.54, -72.34, 12, now());
select public.add_alert_position((select v::uuid from s3 where k = 'alert'), 18.55, -72.34, 12, now()); -- ignorée (< 5 s)
select tests.expect((select count(*) from public.alert_positions) = 1, 'une position toutes les 5 s au plus');
select tests.expect_error($$select public.add_alert_position((select v::uuid from s3 where k = 'alert'), 95, 0, 1, now())$$, '%invalid_position%');
select tests.expect_error($$select public.create_tracking_link((select v::uuid from s3 where k = 'alert'), 'Maman', 30)$$, '%invalid_ttl%');
insert into s3 values ('token', public.create_tracking_link((select v::uuid from s3 where k = 'alert'), 'Maman', 240));
select tests.expect(char_length((select v from s3 where k = 'token')) = 32, 'jeton de 192 bits (32 caractères base64url)');
select tests.expect_error($$select token_hash from public.tracking_links$$, '%permission denied%');
select tests.expect((select count(*) from public.tracking_links) = 1, 'le propriétaire voit son lien (sans le hachage)');
reset role;

-- Un tiers ne voit ni ne touche l'alerte d'un autre.
select tests.as_user(:'teen'); set local role authenticated;
select tests.expect((select count(*) from public.alerts) = 0, 'un tiers ne voit pas les alertes des autres');
select tests.expect((select count(*) from public.alert_positions) = 0, 'un tiers ne voit pas les positions des autres');
select tests.expect_error($$select public.update_alert((select v::uuid from s3 where k = 'alert'), 4)$$, '%not_found%');
select tests.expect_error($$select public.close_alert((select v::uuid from s3 where k = 'alert'), 'USER_SAFE')$$, '%not_found%');
select tests.expect_error($$select public.create_tracking_link((select v::uuid from s3 where k = 'alert'), null, 240)$$, '%not_found%');
select tests.expect_error($$select private.track_read((select v from s3 where k = 'token'))$$, '%permission denied%');
select tests.expect_error($$insert into public.alerts (owner_id, idempotency_key, level, reason, confidence, trigger) values (auth.uid(), 'direct-insert', 2, 'SOS', 'HIGH', 'SOS')$$, '%permission denied%');
reset role;

-- R-PAR-01 : un 12–15 ans sans consentement n'ouvre qu'un SOS manuel.
select tests.as_user(:'minor'); set local role authenticated;
select tests.expect_error($$select public.create_alert('k-minor-auto', 2, 'DISTRESS', 'MEDIUM', 'AUTOMATIC', false)$$, '%protection_not_allowed%');
select tests.expect(public.create_alert('k-minor-sos-1', 2, 'SOS', 'HIGH', 'SOS', false) is not null, 'un mineur peut toujours lancer un SOS');
reset role;

-- Page des proches (service_role, Edge Function `track`).
select set_config('request.jwt.claims', '', true);
set local role service_role;
select tests.expect(private.track_read('jeton-invalide') is null, 'jeton invalide → rien');
select tests.expect(private.track_read((select v from s3 where k = 'token')) ->> 'name' = 'Inconnu', 'le proche voit le prénom');
select tests.expect(jsonb_array_length(private.track_read((select v from s3 where k = 'token')) -> 'positions') = 1, 'et la position');
select tests.expect(private.track_act((select v from s3 where k = 'token'), 'TOOK_CONTACT'), 'action « J''ai pris contact »');
select tests.expect(private.track_act((select v from s3 where k = 'token'), 'TOOK_CONTACT'), 'action rejouée sans doublon');
select tests.expect(not private.track_act((select v from s3 where k = 'token'), 'DELETE_ALL'), 'action inconnue refusée');
reset role;
select tests.expect((select count(*) from public.contact_actions) = 1, 'une seule action enregistrée');
select tests.expect((select count(*) from public.link_views) = 1, 'consultations journalisées (une par minute)');

select tests.as_user(:'stranger'); set local role authenticated;
select tests.expect((select count(*) from public.alert_feedback((select v::uuid from s3 where k = 'alert')) where action = 'TOOK_CONTACT') = 1,
  'le propriétaire voit que son proche a pris contact');
select public.close_alert((select v::uuid from s3 where k = 'alert'), 'USER_SAFE');
select public.close_alert((select v::uuid from s3 where k = 'alert'), 'USER_SAFE'); -- idempotente
select tests.expect((select status from public.alerts where id = (select v::uuid from s3 where k = 'alert')) = 'closed', 'alerte close');
select tests.expect_error($$select public.add_alert_position((select v::uuid from s3 where k = 'alert'), 18.54, -72.34, 12, now())$$, '%alert_closed%');
reset role;
set local role service_role;
select tests.expect(private.track_read((select v from s3 where k = 'token')) ->> 'status' = 'closed', 'la page affiche « terminée »');
select tests.expect(jsonb_array_length(private.track_read((select v from s3 where k = 'token')) -> 'positions') = 0, 'plus aucune position après la clôture');
select tests.expect(not private.track_act((select v from s3 where k = 'token'), 'FALSE_ALARM'), 'plus d''action après la clôture');
reset role;

-- Tâches planifiées : expiration à 24 h, purge des positions.
select set_config('request.jwt.claims', '', true);
update public.alerts set opened_at = now() - interval '25 hours' where idempotency_key = 'k-minor-sos-1';
select tests.expect(private.expire_alerts() = 1, 'alerte de plus de 24 h expirée');
update public.alerts set closed_at = now() - interval '2 days' where id = (select v::uuid from s3 where k = 'alert');
select private.purge_expired();
select tests.expect((select count(*) from public.alert_positions) = 0, 'positions purgées 24 h après la clôture');

-- Suppression du compte.
select tests.as_user(:'teen'); set local role authenticated;
select public.create_alert('k-teen-0001', 2, 'SOS', 'HIGH', 'SOS', false);
select public.delete_my_account();
reset role;
select set_config('request.jwt.claims', '', true);
select tests.expect(not exists (select 1 from public.profiles where id = :'teen'), 'compte supprimé : profil effacé');
select tests.expect(not exists (select 1 from public.alerts a where a.idempotency_key = 'k-teen-0001'), 'compte supprimé : alertes effacées');

-- ===== S3.04 · SMS de secours par le serveur : garde-fous =====
select tests.as_user(:'guardian'); set local role authenticated;
insert into s3 values ('g_alert', public.create_alert('k-guardian-01', 2, 'SOS', 'HIGH', 'SOS', false)::text);
select tests.expect_error($$select private.server_sms_allowed(auth.uid(), (select v::uuid from s3 where k = 'g_alert'), '+50930000009')$$, '%permission denied%');
reset role;
select set_config('request.jwt.claims', '', true);
select tests.expect(private.server_sms_allowed(:'guardian', (select v::uuid from s3 where k = 'g_alert'), '+50931000001'), 'SMS serveur permis pour son alerte ouverte');
select tests.expect(not private.server_sms_allowed(:'stranger', (select v::uuid from s3 where k = 'g_alert'), '+50931000001'), 'jamais pour l''alerte d''un autre');
select tests.expect(not private.server_sms_allowed(:'stranger', (select v::uuid from s3 where k = 'alert'), '+50931000001'), 'jamais pour une alerte close depuis plus d''1 h');
do $$ declare i int; begin
  for i in 2..5 loop perform private.server_sms_allowed('22222222-2222-2222-2222-222222222222', (select v::uuid from s3 where k = 'g_alert'), '+5093100000' || i); end loop;
end $$;
select tests.expect(not private.server_sms_allowed(:'guardian', (select v::uuid from s3 where k = 'g_alert'), '+50931000099'), '5 destinataires distincts au plus par alerte');
select tests.expect(private.server_sms_allowed(:'guardian', (select v::uuid from s3 where k = 'g_alert'), '+50931000001'), 'un destinataire déjà prévenu peut recevoir la suite');
do $$ declare i int; begin
  for i in 1..10 loop perform private.server_sms_allowed('22222222-2222-2222-2222-222222222222', (select v::uuid from s3 where k = 'g_alert'), '+50931000001'); end loop;
end $$;
select tests.expect((select count(*) from private.server_sms_log where alert_id = (select v::uuid from s3 where k = 'g_alert')) = 12, '12 SMS au plus par alerte');
select tests.expect(not exists (select 1 from private.server_sms_log where phone_hash like '+%'), 'aucun numéro en clair');

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

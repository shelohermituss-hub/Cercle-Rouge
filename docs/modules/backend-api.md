# backend-api (Supabase)

Décision : [ADR-0011](../decisions/ADR-0011-backend-supabase.md). Tout est versionné dans `supabase/` (migrations, fonctions, tests).

## Responsabilité

Comptes (OTP par téléphone), profils, consentement parental, contacts et leur consentement, relais des alertes (push, SMS, e-mail), liens de suivi, perte de contact, stockage temporaire des preuves partagées, page web des proches, purges de rétention.

## Hors périmètre

Analyse audio (toujours sur le téléphone) ; décision d'alerte (sauf « perte de contact », qui ne peut venir que du serveur).

## Architecture

```
App Android (supabase-kt) ──JWT──► PostgREST ─► RPC SECURITY DEFINER ─► tables (RLS)
        │                                                     │
        ├── Realtime (alertes, actions des proches)  ◄────────┤ triggers ─► audit_logs
        ├── Storage TUS (morceaux chiffrés)                    │
        └── Functions (invocation directe)                     └► Database Webhooks ─► Edge Functions
                                                                                     ├─ notify-alert (FCM, SMS, e-mail)
Page web des proches (sans compte) ──► Edge Function `track` (jeton)                 ├─ send-invite (SMS)
                                                                                     └─ track, contact-consent
pg_cron ─► heartbeat_watchdog() chaque minute · purge_expired() chaque heure · age_upgrade() chaque mois
```

## Schéma (tables principales)

| Table | Colonnes clés | RLS (résumé) |
|---|---|---|
| `profiles` | `id` (= `auth.uid`), `display_name`, `locale`, `birth_year_month`, `age_band`, `country`, `role` (`user`, `support`, `admin`) | Lecture et mise à jour de sa ligne (sauf `role`, `age_band` : calculés) ; un trigger empêche l'élévation de rôle |
| `guardian_links` | `minor_id`, `guardian_phone_hash`, `guardian_id?`, `status` (`pending`, `confirmed`, `refused`, `revoked`, `expired`), `expires_at`, `confirmed_at` | Le jeune lit les siennes ; le parent lit celles qui le concernent ; **écriture par RPC uniquement** |
| `devices` | `user_id`, `fcm_token`, `platform`, `last_seen_at` | Propriétaire |
| `trusted_contacts` | `owner_id`, `display_name`, `phone_enc` (pgsodium ou chiffrement applicatif), `phone_hash`, `locale`, `channels[]`, `rank`, `role`, `consent_status`, `contact_user_id?` | Propriétaire en CRUD (sauf `consent_status`) ; le contact lit les lignes qui le concernent |
| `contact_invites` | `contact_id`, `token_hash`, `expires_at` | Aucune (Edge Function) |
| `alerts` | `id`, `owner_id`, `idempotency_key` (unique), `level` CHECK 0–4, `reason`, `confidence`, `trigger`, `silent`, `status`, `opened_at`, `closed_at`, `closure` | Propriétaire en lecture ; contacts consentis en lecture **pendant l'alerte** ; écriture par RPC |
| `alert_events` | `alert_id`, `seq`, `type`, `payload` jsonb, `created_at` | Comme `alerts` ; append-only |
| `alert_positions` | `alert_id`, `lat`, `lon`, `accuracy_m`, `ts` | Comme `alerts` |
| `trips` | `id`, `owner_id`, `status`, `planned_arrival`, `heartbeat_enabled`, `last_heartbeat_at`, `last_position` | Propriétaire ; contacts suiveurs en lecture pendant le trajet |
| `tracking_links` | `id`, `token_hash`, `alert_id?`, `trip_id?`, `contact_id`, `expires_at` CHECK ≤ +6 h, `revoked_at` | Propriétaire (lecture, révocation via RPC) |
| `link_views`, `contact_actions` | Consultations ; « J'ai pris contact », « Alerte fausse », « Secours » | Propriétaire en lecture ; écriture par l'Edge Function `track` |
| `deliveries` | `alert_id`, `contact_id`, `channel`, `status`, `provider_ref`, `attempts` | Propriétaire en lecture |
| `evidence_objects` | `alert_id`, `storage_path`, `sha256`, `prev_hash`, `size`, `retention_until` | Propriétaire ; contacts si partagée |
| `chain_roots` | `alert_id`, `root_hash`, `signature`, `received_at` | Propriétaire en lecture |
| `consents` | `user_id`, `scope`, `granted`, `text_version`, `locale`, `at` | Propriétaire ; append-only |
| `audit_logs` | `actor`, `action`, `target`, `at`, `meta` | Admin en lecture ; aucune politique UPDATE ni DELETE |
| `rate_limits` | Compteurs par utilisateur et par action | Aucune (fonctions) |

## Fonctions RPC (`SECURITY DEFINER`, `authenticated` uniquement)

| RPC | Effet | Garde-fous |
|---|---|---|
| `request_guardian_consent(phone)` | Crée un lien `pending` et déclenche le SMS | Âge 12–15 ; 3 par jour ; numéro ≠ le sien |
| `confirm_guardian_consent(link_id, accept)` | Confirme ou refuse | Téléphone vérifié = numéro invité ; adulte ; pas expiré (R-PAR-03) |
| `revoke_guardian_consent(link_id)` | Retrait par le parent ou le jeune | Journalisé |
| `invite_contact(contact_id)` | Envoie l'invitation SMS | Limite ; propriétaire |
| `create_alert(idempotency_key, level, reason, confidence, trigger, silent, position?)` | Crée l'alerte (ou renvoie l'existante) | Idempotente ; R-PAR-01 (mineur sans consentement → refus, sauf `trigger = SOS`) |
| `update_alert(alert_id, level, position?, event)` | Escalade, positions | Le niveau ne descend pas ; propriétaire ; `FOR UPDATE` |
| `close_alert(alert_id, closure)` | Clôture | Propriétaire |
| `start_trip(...)`, `heartbeat(trip_id, position, battery)`, `end_trip(trip_id)` | Trajet | Propriétaire ; suppression des points à la fin (R-TRIP-06) |
| `create_tracking_link(alert_id or trip_id, contact_id, ttl)` | Jeton ≥ 128 bits (seul son hachage est stocké) | TTL 2 à 6 h |
| `register_chain_root(alert_id, root, signature)` | Racine de la chaîne de preuves | — |
| `delete_my_account()` | Supprime les données, programme la purge du Storage, révoque les sessions | Confirmation par OTP récent |

## Edge Functions (Deno, `service_role`)

| Fonction | Déclencheur | Rôle |
|---|---|---|
| `notify-alert` | Webhook INSERT/UPDATE sur `alerts` | Résout les destinataires consentis ; FCM (réveil seulement) ; SMS ; e-mail ; écrit `deliveries` ; réessais |
| `send-invite` | Webhook sur `guardian_links` et `contact_invites` | SMS d'invitation (parent, contact) |
| `contact-consent` | HTTP (page) | Acceptation ou refus d'un contact sans compte ; STOP |
| `track` | HTTP GET/POST (page web des proches) | Valide le jeton (hachage, expiration, révocation) ; renvoie statut, positions, chronologie ; enregistre les actions ; journalise la consultation |
| `sms-inbound` | Webhook du fournisseur SMS | STOP, rapports de remise |

## Tâches `pg_cron`

| Tâche | Fréquence | Effet |
|---|---|---|
| `heartbeat_watchdog()` | Chaque minute | Trajet avec battement activé et `last_heartbeat_at` > 10 min → alerte « perte de contact » niveau 2 (une fois par palier, annulée au retour) (R-BE-08) |
| `expire_alerts()` | Toutes les 15 min | Alerte ouverte depuis 24 h → `EXPIRED` + message aux proches |
| `purge_expired()` | Chaque heure | Preuves, positions, liens, invitations selon la rétention (R-BE-03) |
| `age_upgrade()` | Mensuelle | Passage à 16 ans → lien parental facultatif |

## Règles

| ID | Règle |
|---|---|
| R-BE-01 | **DOIT** : RLS activée sur 100 % des tables ; les écritures sensibles passent uniquement par des RPC idempotentes. |
| R-BE-02 | **DOIT** : aucun audio brut d'analyse n'est reçu ; seules des preuves explicitement partagées (niveau ≥ 2), déjà chiffrées par l'application. |
| R-BE-03 | **DOIT** : purge automatique conforme à la rétention (`pg_cron` + test). |
| R-BE-04 | **DOIT** : clés FCM, SMS et `service_role` dans les secrets Supabase ; jamais dans l'app ni dans le dépôt. |
| R-BE-05 | **DOIT** : limitation de débit par utilisateur, par contact et par IP (`rate_limits` + Edge Functions) ; jetons de suivi non énumérables. |
| R-BE-06 | **DOIT** : journaux des Edge Functions sans numéro, position ni message. |
| R-BE-07 | **DOIT** : surveillance du taux de livraison et de la latence (p95 < 5 s entre l'insertion de l'alerte et l'envoi). |
| R-BE-08 | **DOIT** : la perte de contact est envoyée au plus 1 fois par trajet et par palier ; elle est annulée si l'appareil revient. |
| R-BE-09 | **DOIT** : `supabase/tests/security.sql` couvre chaque table et chaque RPC pour les rôles `anon`, `authenticated` (propriétaire, contact, parent, tiers) et `admin` ; il est exécuté en CI. |
| R-BE-10 | **DOIT** : projet en plan Pro (pas de mise en pause) ; PITR activé ; deux environnements (staging, production). |

# Suivi du Sprint 3 — Alertes via Supabase et page des proches

Mis à jour le 2026-11-11. Légende : ✅ fait et vérifié · 🟡 fait, non vérifié sur téléphone · ⏳ à faire · ⏸ attend un compte ou une décision · 👤 dépend d'une action humaine

## Décisions (ADR-0022)
- Comptes **anonymes** d'abord (pas encore de fournisseur SMS pour l'OTP).
- Page des proches sur **GitHub Pages**.

## Étapes

| Étape | Statut | Note |
|---|---|---|
| S3.01 Migration alertes, positions, liens, consultations, actions + RLS + CHECK | ✅ | `s3_alerts` + `s3_purge` appliquées ; `security.sql` vert en local et sur le projet. `devices`, `trusted_contacts`, `contact_invites`, `deliveries` : avec l'OTP et les fournisseurs |
| S3.02 RPC `create_alert`, `update_alert`, `close_alert`, `create_tracking_link` | ✅ | Idempotence, propriétaire, mineur (SOS seulement), niveau qui ne descend pas : testés |
| S3.03 `contact-consent` | ⏸ | Avec l'OTP |
| S3.04 `notify-alert` (FCM, SMS, e-mail) | ⏸ | Firebase et fournisseur SMS (👤) |
| S3.05 Jeton FCM | ⏸ | Firebase (👤) |
| S3.06 Synchronisation de l'app (jobs serveur, repli SMS direct) | ⏳ | Prochaine étape |
| S3.07 Edge Function `track` | ✅ | Déployée ; testée de bout en bout sur le vrai projet |
| S3.08 Page des proches | ✅ (navigateur) / 👤 (Pages) | `web/track` ≈ 50 Ko compressés, FR/HT/EN, testée dans Chromium contre une vraie alerte. **Activer GitHub Pages** (Settings → Pages → GitHub Actions) |
| S3.09 Vue « proche » dans l'app | ⏸ | Avec l'OTP |
| S3.10 « J'ai pris contact » / « Alerte fausse » / clôture | ✅ (serveur) / ⏳ (app) | Actions enregistrées et visibles par le propriétaire (`alert_feedback`) ; affichage dans l'app avec S3.06 |
| S3.11 `pg_cron` : expiration et purge | ✅ | Toutes les 15 min et toutes les heures ; testées avec une date simulée |
| S3.12 Suppression de compte | ✅ (serveur) | `delete_my_account` ; bouton dans l'app avec S3.06. Export : déjà local (journal, preuves) |

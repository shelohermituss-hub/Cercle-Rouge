# ADR-0022 — Sprint 3 : comptes anonymes d'abord, page des proches sur GitHub Pages

- Statut : **Acceptée** · Date : 2026-11-11 · Sprint 3 (S3.01, S3.02, S3.07, S3.08, S3.11, S3.12) · Lié à ADR-0011, Q13

## Contexte
Le projet Supabase « Cercle rouge » (`lvxdpmjyeelrxfbrtvay`, us-east-1) existe. L'OTP par SMS demande un fournisseur (Twilio, Vonage…) que le projet n'a pas encore (Q13). La page des proches doit être hébergée hors de Supabase, qui ne sert pas de pages HTML.

## Décisions
| Sujet | Décision |
|---|---|
| Comptes | **Comptes anonymes Supabase** (« Allow anonymous sign-ins » activé) : chaque téléphone obtient un compte sans numéro. L'OTP SMS s'ajoutera au compte existant (`updateUser` + vérification) quand un fournisseur sera choisi, sans perte de données |
| Tables | `alerts`, `alert_events`, `alert_positions`, `tracking_links`, `link_views`, `contact_actions` ; RLS : propriétaire en lecture ; aucune écriture directe |
| RPC | `create_alert` (idempotente par clé ; mineur sans consentement → SOS seulement ; `HEARTBEAT_LOSS` réservé au serveur ; 30 par jour), `update_alert` (le niveau ne descend jamais), `add_alert_position` (alerte ouverte, 1 toutes les 5 s), `close_alert` (révoque les liens), `create_tracking_link` (jeton de 192 bits rendu une fois, seul le hachage est stocké, 2 à 6 h, 10 par alerte), `revoke_tracking_link`, `alert_feedback`, `delete_my_account` |
| Page des proches | **GitHub Pages** (`web/track`, dépôt public), ≈ 50 Ko compressés. Jeton dans le **fragment** (`…/Cercle-Rouge/#t=…`) : jamais envoyé à GitHub. CSP stricte, `noindex`, `no-referrer`. Rafraîchie toutes les 5 s, FR/HT/EN. Carte Leaflet embarquée (pas de CDN), tuiles OpenStreetMap avec `strict-origin` |
| Relais | Edge Function `track` (sans JWT, le jeton fait office d'authentification), connexion directe à la base, appelle `private.track_read` / `private.track_act` (exécutables par `service_role` seulement). Jeton dans le corps, jamais dans l'URL ; aucune donnée personnelle dans les journaux ; CORS limité à `https://shelohermituss-hub.github.io` ; 404 identique pour tout jeton non valable |
| Planification | `pg_cron` : `expire_alerts` toutes les 15 min (alerte ouverte 24 h → expirée, liens révoqués), `purge_expired` toutes les heures (positions 24 h après la clôture, alertes 30 jours après, compteurs après 7 jours) |
| Migrations | `s3_alerts` (appliquée par l'outil) et `s3_purge` (toutes les suppressions, appliquée par le porteur du projet dans l'éditeur SQL après validation) |

## Avertissements du Security Advisor acceptés
- **14 RPC `SECURITY DEFINER` appelables par `authenticated`** : voulu, c'est la règle du projet (écritures sensibles uniquement par RPC).
- **« Anonymous Access Policies »** : conséquence directe des comptes anonymes. Toutes les politiques limitent chacun à **ses propres lignes** ; `rate_limits` et `link_views` n'ont qu'une politique de refus. Les tables `cron.*` sont gérées par Supabase.

## Reporté
- `notify-alert` (push FCM, SMS serveur, e-mail) : en attente de Firebase et d'un fournisseur SMS. Les SMS partent du téléphone (Sprint 2), avec le lien de suivi.
- `contact-consent`, `trusted_contacts` côté serveur, vue « proche » dans l'app (S3.03, S3.09) : avec l'OTP.
- Battement et chien de garde de trajet (S4.07, S4.08).

## Conséquences
- Un proche n'a besoin d'aucun compte ni d'aucune app pour voir l'alerte et répondre.
- Activer GitHub Pages une fois : Settings → Pages → Source « GitHub Actions » (workflow `pages.yml`).
- Vérifié de bout en bout sur le vrai projet : compte anonyme → profil → alerte → position → lien → page → « J'ai pris contact » → clôture → suppression du compte (rien ne reste).

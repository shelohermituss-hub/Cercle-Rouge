# ADR-0011 — Backend : Supabase

- Statut : **Acceptée** (« pour le moment », à réévaluer après le pilote) · Date : 2026-11-10

## Contexte
Il faut un backend pour les comptes, les alertes, les liens de suivi, le temps réel, les notifications et le stockage des preuves. L'équipe connaît déjà Supabase (projet Konvwa) et dispose des règles de sécurité associées.

## Décision
| Besoin | Brique Supabase |
|---|---|
| Comptes | Supabase Auth, **connexion par numéro de téléphone (OTP SMS)** |
| Données | PostgreSQL + **RLS** sur toutes les tables ; écritures sensibles via des RPC `SECURITY DEFINER` |
| Temps réel (application) | Supabase Realtime (Postgres Changes ou Broadcast, autorisés par RLS) |
| Temps réel (page web des proches sans compte) | Edge Function + polling toutes les 5 s (MVP) |
| Notifications, SMS, e-mail | Edge Functions (Deno/TypeScript) déclenchées par Database Webhooks ; FCM HTTP v1 ; passerelle SMS (❓ Twilio, à valider pour Digicel et Natcom) |
| Tâches planifiées | `pg_cron` : surveillance des battements (chaque minute), purges de rétention |
| Preuves | Supabase Storage, bucket privé, envois reprenables (TUS), fichiers **déjà chiffrés par l'app** |
| Client Android | `supabase-kt` (Auth, Postgrest, Realtime, Storage, Functions) |
| Versionnage | Dossier `supabase/` : `migrations/`, `functions/`, `tests/security.sql`, `seed.sql` |

Plan **Pro** obligatoire en production : le plan gratuit met le projet en pause après une période d'inactivité, et une alerte ne doit jamais tomber sur un serveur en pause. Région : ❓ `us-east-1` (la plus proche d'Haïti), à confirmer.

## Règles de sécurité (reprises du projet Konvwa)
1. La RLS est la seule vraie barrière ; chaque table a la RLS activée et des politiques par rôle, testées.
2. Le client ne décide jamais d'un statut, d'un niveau d'alerte côté serveur, ni d'un consentement : passage par des RPC `SECURITY DEFINER` qui relisent `auth.uid()`, vérifient le propriétaire, verrouillent la ligne et sont idempotentes.
3. Les fonctions `SECURITY DEFINER` fixent `SET search_path = public`, retirent `EXECUTE` à `PUBLIC` et `anon`, et ne l'accordent qu'à `authenticated` si besoin.
4. Les politiques utilisent `(select auth.uid())` et des helpers `SECURITY DEFINER` (`is_admin()`…), jamais une sous-requête sur `profiles`.
5. Contraintes `CHECK` en filet final (statuts, niveaux 0 à 4, dates d'expiration).
6. `service_role` uniquement dans les Edge Functions ; jamais dans l'application ; secrets dans les secrets Supabase.
7. Journal d'audit append-only (`audit_logs`), alimenté par des triggers.
8. Après chaque migration : `supabase/tests/security.sql` (impersonation par rôle, transaction annulée) + Security Advisor sans nouvelle alerte.

## Écarts avec la spécification initiale
| Spécification | Avec Supabase | Mesure compensatoire |
|---|---|---|
| Serveur de secours dans une 2e région | Un projet, une région | Le **SMS direct depuis le téléphone** reste le canal indépendant (R-ALR-04) ; PITR et sauvegardes ; page de statut. Une migration est possible (Postgres standard) si le pilote l'exige |
| Identité de l'appareil par clé du Keystore | JWT Supabase (OTP téléphone) | Jetons de session chiffrés localement (Tink) ; `K_device` sert à signer les racines de preuves ; ❓ Play Integrity en V2 |
| WebSocket maison | Realtime (app) + polling (page web) | Suffisant en MVP |

## Conséquences
+ Mise en route rapide, Postgres standard (pas de dépendance forte), compétences existantes.
− Dépendance à un fournisseur (localisation des données, disponibilité) ; coûts OTP par SMS ; la RLS doit être testée rigoureusement.

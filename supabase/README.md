# Supabase — Cercle Rouge

Backend décidé par l'ADR-0011. Schéma et règles : [`docs/modules/backend-api.md`](../docs/modules/backend-api.md).

| Dossier | Contenu |
|---|---|
| `migrations/` | Migrations SQL, appliquées dans l'ordre ; elles décrivent exactement ce qui est en production |
| `tests/security.sql` | Tests d'impersonation (anon, jeune, adulte référent, inconnu, ado de 16 ans, admin) dans une transaction annulée |
| `tests/local_supabase_stub.sql` | **Local uniquement** : imite les rôles et `auth.uid()` de Supabase sur un PostgreSQL nu. Jamais appliqué sur un vrai projet |
| `scripts/test-local.sh` | Crée une base jetable, applique stub + migrations, lance `security.sql` |

## Tester en local

```bash
# PostgreSQL 16 local, par exemple sur le port 54322
PGHOST=/tmp/pg PGPORT=54322 PGUSER=postgres supabase/scripts/test-local.sh
# → ✅ security.sql : toutes les assertions passent
```

La CI (`.github/workflows/supabase.yml`) lance le même script à chaque modification de `supabase/`.

## Projet relié (2026-11-11)

- Projet Supabase « Cercle rouge » (`lvxdpmjyeelrxfbrtvay`, us-east-1). Migrations appliquées : `s1_socle`, `s1_hardening`.
- `tests/security.sql` relancé sur le projet (dans une transaction annulée) : toutes les assertions passent ; Security Advisor :
  seules restent les 6 RPC voulues (`set_profile`, `request_guardian_consent`, `pending_guardian_requests`,
  `confirm_guardian_consent`, `revoke_guardian_consent`, `protection_allowed`), appelables par `authenticated` par conception.
- App Android : `supabase.url` et `supabase.publishableKey` dans `android/local.properties` (hors du dépôt) → `BuildConfig`.

## Procédure d'origine (S0.08)

1. `supabase link --project-ref <ref>` puis `supabase db push` (staging d'abord).
2. Lancer `tests/security.sql` sur la base de staging, sans le stub.
3. Security Advisor : aucune nouvelle alerte tolérée.
4. Brancher Vonage sur l'OTP de Supabase Auth, puis écrire l'Edge Function `send-invite` (S1.17, en attente du compte Vonage).

## Choix notables

- Le numéro de l'adulte référent n'est gardé en clair que le temps d'envoyer l'invitation ; il est effacé à la décision, seul son hachage reste.
- `protection_allowed()` ne répond que pour l'utilisateur courant : elle ne révèle rien sur les autres comptes.
- Le trigger anti-élévation de rôle se fie aux claims du JWT, car `current_user` vaut le propriétaire dans une fonction `SECURITY DEFINER`.

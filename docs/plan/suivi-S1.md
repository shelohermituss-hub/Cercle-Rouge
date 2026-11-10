# Suivi du Sprint 1 — Socle Android et socle Supabase

Mis à jour le 2026-11-10. Légende : ✅ fait et vérifié · 🟡 fait en version provisoire · ⏳ à faire · 👤 dépend d'une action humaine

## Résultat

| Bloc | Statut | Preuve |
|---|---|---|
| A · Projet, contrôles, CI | ✅ | `./gradlew check` vert (ktlint, Lint en erreur, `!!` interdit, traductions FR/HT/EN) ; `assembleRelease` vert ; workflows `android.yml`, `supabase.yml` |
| B · Machine à états + score de risque | ✅ | 52 tests JVM : table des transitions + 8 propriétés sur 24 000 événements aléatoires ; un bug volontaire est bien détecté |
| C · Stockage chiffré, journal, PIN | ✅ | 9 tests JVM + test instrumenté sur émulateur API 34 (base illisible sans clé, canari absent, chaîne intacte) |
| D · Supabase (local) | ✅ | `supabase/tests/security.sql` vert sur PostgreSQL 16 : anon, jeune, adulte référent, inconnu, ado de 16 ans, admin ; une faille volontaire est bien détectée |
| E · Écrans | 🟡 | Onboarding complet, accueil radar, SOS de bout en bout, journal : vérifiés sur émulateur (thème sombre, français) |

## Étapes du plan

| Étape | Statut | Note |
|---|---|---|
| S1.01–S1.04 | ✅ | AGP 9.5.1, Gradle 9.9, Kotlin 2.4.10, compileSdk 37, minSdk 29 ; miroir Google de Maven Central (refus 429) |
| S1.05–S1.09 | ✅ | `AlertEngine.reduce`, `RiskFusion` |
| S1.10–S1.13 | ✅ | Keystore + Tink + SQLCipher (clé brute, ouverture différée hors du fil principal), journal chaîné, PIN PBKDF2 310 000 |
| S1.13b clés de partage des preuves | ⏳ | Reporté à S6, où elles servent (ADR-0007 amendé) |
| S1.14–S1.16, S1.18 | ✅ | Migration, RLS, RPC, tests, CI |
| S1.17 Edge Function `send-invite` | ⏳ 👤 | Attend le compte Vonage |
| S1.19 Thème, polices, langues | ✅ | Design v3 « Pixel Lime » (fond noir-gris, accueil carte + bouton central monochrome/couleur) ; Pixelify Sans + Space Mono ; **créole à faire relire par des locuteurs natifs** |
| S1.20 Inscription par code SMS | ⏳ 👤 | Attend les comptes Supabase et Vonage |
| S1.21 Âge (12 ans et +) | ✅ | Même règle côté app (`AgeBand`) et côté serveur (`age_band`) |
| S1.22 Parcours jeune 12–15 ans | 🟡 | Écrans faits ; envoi et confirmation **simulés localement** (bouton « Simuler la confirmation », builds de développement seulement) |
| S1.23 Parcours adulte référent | ⏳ 👤 | Côté serveur prêt et testé ; écran à brancher sur les vraies RPC quand Supabase existera |
| S1.24 Retrait du consentement | 🟡 | Côté serveur prêt et testé ; écran à faire avec S1.23 |
| S1.25 Onboarding, consentements, permissions | ✅ | Conservation sans présélection, PIN, notifications, attestations ; consentements historisés |
| S1.26 Accueil, SOS, notifications | ✅ | Appui long 1,5 s → 5 s d'annulation → mode urgence ; notifications neutres sur l'écran verrouillé |

## Bugs trouvés et corrigés pendant le sprint

- Notifications jamais affichées sur Android 10 à 12 (permission inexistante avant Android 13).
- Plantage de la vibration sur Android 10 et 11 (`VibratorManager` n'existe qu'à partir d'Android 12).
- Textes noirs sur fond noir (couleur de contenu par défaut absente du thème).
- Blocage du démarrage : ouverture de la base chiffrée sur le fil principal.
- Un texte de l'app écrasait un texte d'accessibilité interne de Compose.

## Limites connues (prévues dans les sprints suivants)

- **L'armement ne démarre encore ni le micro ni la position** : l'état change, mais les moteurs arrivent avec le service de premier plan (S2, S4, S5). Le texte « Analyse locale » est donc anticipé.
- **Aucun envoi aux proches** : les alertes sont journalisées ; le SMS arrive au S2.
- **L'état d'alerte vit en mémoire** : sa persistance avant chaque effet (R-SM-01) arrive avec le service au S2.
- Trajet et contacts : boutons présents, « Bientôt disponible » (S2, S4).

# Cercle-Rouge — Project Instructions

## Projet

Cercle Rouge (nom provisoire) : application Android de sécurité personnelle. Elle protège une personne pendant un trajet, une sortie ou une urgence, grâce à une détection audio **locale**, aux capteurs, à une géolocalisation **temporaire**, à l'alerte des proches et à des preuves **chiffrées**.

**Statut : phase de spécification. Aucun code n'est écrit tant que l'utilisateur ne l'a pas demandé.** La source de vérité est `docs/` (commencer par `docs/README.md`).

- Android natif : Kotlin + Jetpack Compose, minSdk 29, `com.cerclerouge.app`. IA locale : LiteRT. Langues : FR (défaut), créole haïtien (`ht`), EN.
- Backend (❓ Go ou Node.js), PostgreSQL, Redis, WebSocket, FCM + SMS de secours. iOS en phase 2.

## Règles absolues (détail dans `docs/00-vision-principes.md`)

1. Écoute **opt-in**, toujours visible (service de premier plan `microphone` + notification). Aucun mode caché, aucun usage de surveillance d'un tiers.
2. **Aucun audio brut** ne quitte le téléphone en fonctionnement normal.
3. **Escalade progressive** (`docs/01-etats-et-escalade.md`). L'IA ne décide jamais seule d'appeler les secours ; les numéros d'urgence passent toujours par le composeur.
4. Données minimales : position et médias partagés seulement pendant un trajet protégé ou une alerte ; liens de suivi de 2 à 6 h.
5. Preuves chiffrées avant écriture (Keystore + Tink), rétention de 24 h par défaut et de 30 j au maximum, crypto-shredding.
6. Local d'abord : file persistante, SMS de secours, idempotence.
7. Ne jamais prétendre prédire un danger. Probabilités calibrées, versions des modèles journalisées.
8. Toute exigence a un identifiant `R-XXX-nn` ; le code et les tests y font référence. Toute nouvelle permission → `docs/conformite/android-permissions-play.md`.
9. Avant d'implémenter un module, vérifier `docs/questions-ouvertes.md` (questions 🔴 bloquantes) et poser les questions à l'utilisateur plutôt que supposer.

Les skills web (`/shadcn`, Tailwind) ne concernent que la page de suivi des proches et `admin-console`. Pour l'app Android, appliquer les principes de `/ui-ux-pro-max`, `/frontend-ui-engineering` et `/emil-design-eng` en Compose.

## Skills installés (`.claude/skills/`)

Versionnés dans le dépôt, donc disponibles dans chaque session. Checklists partagées : `.claude/references/`. Personas : `.claude/agents/` (code-reviewer, security-auditor, test-engineer, web-performance-auditor).

**Pour toute requête, consulter le skill adapté avant de coder ou de décider.**

### UI / design

| Contexte | Skill |
|---|---|
| Composants shadcn/ui, registry, CLI | `/shadcn` |
| Décisions UI/UX, layouts, couleurs, typographie | `/ui-ux-pro-max` |
| Animations, micro-interactions, polish | `/emil-design-eng` puis `/review-animations` |
| Theming Tailwind, variables CSS, dark mode, accessibilité | `/ui-styling` |
| Design général, logos, identité visuelle | `/design` |
| Design tokens, composants systématisés | `/design-system` |
| Marque, ton, messaging | `/brand` |
| Présentations HTML | `/slides` |
| Bannières, héros visuels | `/banner-design` |

### graphify — carte du code (avant de grepper)

- `graphify-out/GRAPH_REPORT.md` résume modules, « god nodes » et communautés : le lire avant d'explorer une zone inconnue.
- `graphify query "<question>"`, `graphify path "A" "B"`, `graphify explain "<symbole>"` ; après un gros refactor : `graphify update .` puis committer `GRAPH_REPORT.md`.
- Installation : `uv tool install "graphifyy[sql]"` ou `pip install "graphifyy[sql]"`.

### ponytail — anti sur-ingénierie

- La solution la plus simple qui marche (YAGNI, stdlib/plateforme avant dépendance). `/ponytail` (lite / full / ultra).
- Avant un diff non trivial : `/ponytail-review` ; dossier : `/ponytail-audit` ; dette : `/ponytail-debt` ; aide : `/ponytail-help`.
- Ne prime jamais sur la sécurité ni sur les exigences UI/UX (états de chargement, accessibilité).

### agent-skills — cycle d'ingénierie

| Phase | Skill |
|---|---|
| Cadrage | `/idea-refine`, `/interview-me`, `/spec-driven-development` |
| Découpage | `/planning-and-task-breakdown` |
| Implémentation | `/incremental-implementation`, `/test-driven-development`, `/api-and-interface-design`, `/frontend-ui-engineering` |
| Bug | `/debugging-and-error-recovery` |
| Qualité | `/code-review-and-quality`, `/code-simplification`, `/performance-optimization`, `/browser-testing-with-devtools` |
| Sécurité | `/security-and-hardening` |
| Livraison | `/git-workflow-and-versioning`, `/ci-cd-and-automation`, `/shipping-and-launch`, `/documentation-and-adrs` |
| Évolution | `/deprecation-and-migration`, `/observability-and-instrumentation` |

Méta : `/using-agent-skills`, `/context-engineering`, `/source-driven-development`, `/constraint-driven-development`, `/doubt-driven-development`.

### Précédence

1. Sécurité et conventions du projet.
2. Skills UI/UX pour tout ce qui se voit.
3. agent-skills pour le processus, puis ponytail pour la sobriété du code.

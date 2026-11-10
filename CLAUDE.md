# Cercle-Rouge — Project Instructions

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

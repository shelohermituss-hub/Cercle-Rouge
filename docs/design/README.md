# Design — décisions

## Accueil adaptatif : radar puis carte (2026-11-10)

Proposition initiale : l'accueil est une carte de suivi en temps réel entourée de boutons.

| Point | Problème si la carte est toujours l'accueil | Solution retenue |
|---|---|---|
| État par défaut « Désactivé » (ADR-0015) | Pas de position en `OFF` (P4, N5) : la carte serait vide, ou il faudrait suivre la personne en permanence | **Accueil « Radar »** en `OFF` et en armé ; la **carte devient l'accueil** automatiquement en `TRIP_ACTIVE` et pendant une alerte |
| Stress, 12 ans et + | Une carte chargée détourne l'attention du SOS | SOS au centre du radar ; boutons « satellites » autour (l'idée des boutons tout autour est conservée) |
| Téléphones d'entrée de gamme, données mobiles chères | Une carte animée en permanence consomme batterie, GPU et données | La carte n'est affichée que pendant un trajet ou une alerte |
| Style « hacking » | Peut paraître anxiogène ou évoquer la surveillance | Direction **HUD tactique épuré** : données temps réel en monospace, grille fine, radar ; pas de glitch ni de vert Matrix |

Prompt complet : [prompt-maquettes.md](prompt-maquettes.md).

## Tokens provisoires

Fond `#0B0F14`, rouge de marque `#E5263B` (logo, SOS, alertes), protégé `#2DD4BF`, vigilance `#F5A524`, alerte `#FF3B4E`. Polices : Space Grotesk (titres), Inter (texte), JetBrains Mono (données). À valider sur les maquettes, puis à formaliser dans le design system (S1.19).

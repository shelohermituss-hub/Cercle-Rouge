# Design — décisions

## v2 « salle d'opérations nocturne » (2026-11-10)

La v1 (HUD épuré) a été jugée trop plate. Nouvelle direction voulue par le porteur du projet, d'après une image de référence : mur de moniteurs vert phosphore, cartes tactiques, alertes rouges, matière et profondeur (cadres biseautés, lueurs, lignes de balayage légères, grain, vignettage). Garde-fous conservés : pas de réticules ni d'imagerie de surveillance, textes non alarmistes, aucun point de position quand la protection est désactivée, SOS dominant, variantes « réduire les animations » et « mode discret ».

Palette v2 : fond `#050807`, panneaux `#0B1310`, vert phosphore `#3DDC84` (protégé, données), texte `#D9F2E4`, rouge de marque `#E5263B`, alerte `#FF4155`, ambre `#FFB020`. Titres en Chakra Petch. Le design system et le thème Compose seront alignés une fois les maquettes v2 validées.

L'accueil devient une « console » : un mur de moniteurs (radar + SOS, carte de la ville sans position, journal), puis la carte en direct plein écran pendant un trajet.

---

## v1 (remplacée)

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

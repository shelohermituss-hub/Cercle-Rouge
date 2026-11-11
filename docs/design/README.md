# Design — décisions

## v3 « Pixel Lime » (2026-11-10) — retenue, implémentée dans l'app

Maquettes du porteur du projet (canevas « Cercle Rouge — Maquettes », page « v3 · Pixel Lime », 20 écrans 360×800), puis deux retouches demandées :

1. **Fond noir-gris neutre** (comme la référence « Pixel VPN »), pas de vert dans le fond. Le lime reste la seule couleur d'action.
2. **Accueil en deux moitiés** (demande du 2026-11-11) : **en haut la carte réelle**, avec un bouton pour l'agrandir en plein écran ; **en bas quatre « moniteurs »** (CH.01 écoute IA avec le bouton ON/OFF, CH.02 position, CH.03 contacts, et le **SOS en rouge franc** `#E5202B`, texte blanc 4,6:1). La grande barre SOS du bas est supprimée : le moniteur SOS garde l'appui long de 1,5 s (un geste, R-HOME-01) et l'appui court vers l'écran SOS. En veille tout est **monochrome** (carte noir et blanc, disque blanc-gris) ; protection active, **la couleur revient**.
3. **Orbes animées** ([OrbMotion](https://github.com/valentineRutto/OrbMotionKMPLibrary), MIT) pour les activités en cours : écoute (`LISTENING`) autour du bouton ON, respiration sans micro, compte à rebours (`CONNECTING`, rouge), alerte envoyée (`SEARCHING`), attente de l'adulte référent, démarrage. Figées si « réduire les animations », ignorées par TalkBack.

| Token | Valeur | Usage |
|---|---|---|
| void / surface / raised | `#0A0B0C` / `#121416` / `#1B1E21` | fonds, panneaux crénelés |
| forestDeep / forest | `#2A2E33` / `#009443` | blocs secondaires, escalier des affiches |
| lime / onLime | `#A0E705` / `#0E1012` | action, état protégé ; texte sur lime ou rouge |
| ink / muted | `#E6E8EA` / `#9BA1A8` | texte (15:1 et 7,1:1 sur surface) |
| line / lineControl | `#3A3F45` / `#6B7279` | bordure décorative ; bordure de contrôle (≥ 3,4:1) |
| danger / warn | `#FF6B5A` / `#FFD23F` | texte et voyants d'alerte ; vérification |
| sos / onSos | `#E5202B` / `#FFFFFF` | boutons SOS (rouge franc) |

Typo : Pixelify Sans (titres, capitales), Space Mono (texte). Angles crénelés de 4 dp, disques en pixels, icônes 8×8, animations par paliers (désactivées si « réduire les animations »).

Revue visuelle sans émulateur : `./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*'` rend l'accueil (veille / actif ; carte remplacée par `build/map-preview-*.png` si présent, MapLibre étant natif), le SOS, le compte à rebours et une affiche en PNG dans `android/app/build/screenshots/` (Robolectric, rendu natif).

Garde-fous conservés :
- **Carte réelle en direct** (MapLibre + OpenFreeMap, style maison gris / lime, [ADR-0018](../decisions/ADR-0018-carte-accueil-maplibre-openfreemap.md)) : en veille, carte noir et blanc centrée sur Port-au-Prince **sans point**, GPS coupé ; protection active, carte en couleurs avec **ton point en direct**. La position reste sur le téléphone (P4).
- SOS toujours en rouge, même en veille monochrome ; appui long 1,5 s (un seul geste) ou appui court vers l'écran SOS.
- Écarts assumés avec les maquettes : 4ᵉ onglet « Journal » (pas encore d'écran Réglages) ; consentement 12–15 ans = notre parcours « adulte référent » (ADR-0013) habillé en Pixel Lime ; données réelles affichées (0 / 5 contacts au Sprint 1).

---

## v2 « salle d'opérations nocturne » (remplacée)

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

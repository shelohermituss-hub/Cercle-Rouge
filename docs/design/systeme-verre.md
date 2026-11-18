# Système de design « Verre »

Statut : **proposé, en cours de validation** (2026-11-18). Remplace le style « Pixel Lime / tableau de bord » une fois validé.
Maquettes : canevas « Cercle Rouge — Nouveau design », page **Design Verre** (planche du système et 13 écrans).

Inspiration : captures d'une app de détection de traceurs fournies par le porteur du projet. On reprend le **langage visuel**
(fond très clair, cartes en verre dépoli, rubans rose-rouge, pilules, onglets segmentés, bouton rond central), pas le contenu,
les images ni la marque de cette app. L'identité reste celle de Cercle Rouge : logo aux anneaux rouges, textes en français.

## Révision 2 (2026-11-18, demandes du porteur)

- **Fond gris uni** `#EDEDF0`, sans rubans ni lueurs. Cartes **identiques à la référence** : carte héros rayon 34
  (dégradé `#FFFFFF`→`#F4F6F8`, ombre longue douce), cartes de contenu rayon 22 blanches, liseré à peine visible, sans ombre.
- **Police** : la référence utilise SF Pro (Apple, interdite sur Android) ; on prend **Inter**, sa jumelle libre, avec les
  graisses légères de la référence (titres de carte 600, lignes 500, valeurs 600).
- **Logo officiel** (fichier du porteur) en haut de l'accueil, au démarrage et dans l'onboarding ; le symbole est redessiné
  (arcs + petits points aux extrémités) pour l'animation du bouton central.
- **SOS dans la carte héros** de l'accueil (pilule rouge « SOS · maintenir 1,5 s »), plus de barre SOS en bas.
- **Rouge non dominant** : accent principal bleu `#2F86F6` (boutons secondaires, carte, graphique, bulles, interrupteurs),
  lime `#84CC16` pour « calme », violet `#6D28D9` pour la pensée du veilleur. Rouge : logo, SOS, alerte.
- **Icônes en verre du porteur** (planches « icons » et « get complete set ») dans les cartes, listes et chiffres ;
  barre du bas au trait comme la référence.
- **Orbe d'écoute** : animation « AI Voice Assistant » fournie par le porteur (4 images clés) : fondu entre les images,
  respiration, rotation lente ; grisée et immobile quand la protection est coupée. En Compose : les 4 images en
  vectoriel/bitmap avec `Crossfade` + `graphicsLayer` (ou export Lottie depuis Figma, comme l'indique le fichier fourni).
- Nouvel écran **Réglages** (liste de la référence).

## Révision 3 (2026-11-18)

- Carte du haut de l'accueil : **même disposition** (texte à gauche), **bouclier 3D violet à droite** à la place des anneaux animés
  (flotte doucement ; grisé si coupée), **SOS fondu dans le bas de la carte** (pleine largeur, coins du bas de la carte).
- **Bouton central blanc et rouge** (couleurs du logo), sans noir : symbole animé et halo rouge quand actif, symbole rose pâle immobile quand coupé.
- Orbe d'écoute **sans halo bleu**, avec une petite ombre portée pour la détacher du fond.
- Alerte en cours : animation **Face ID** (fichier « Biometric Smart Animations ») : visage → cercle → scan bleu → coche.

## Principes

1. Clair et aéré : fond gris-bleu très clair, cartes blanches ou en verre, beaucoup d'espace.
2. Une action forte (sombre) par écran. Le rouge est réservé au logo, au SOS et à l'alerte.
3. **Le bouton d'activation est le bouton rond central de la barre du bas.** Coupé : logo gris immobile. Actif : les anneaux
   du logo tournent, le point rouge bat, un halo rouge pulse. Couper demande le code ou l'empreinte.
4. SOS toujours visible quand la protection est active : pilule rouge au-dessus de la barre, maintien 1,5 s.
5. Trois visualiseurs : **écoute** (orbe OrbMotion `WEAVING`), **carte** (claire, itinéraire rouge en pointillés, ma position en bleu),
   **veilleur IA** (chiffres sur 4 colonnes et journal en police à chasse fixe).
6. Accessibilité : texte ≥ 13 sp (corps 16 sp), contraste ≥ 4,5:1, cibles ≥ 48 dp, la couleur n'est jamais seule,
   animations coupées si « réduire les animations » est actif, la mise en page suit la taille de police d'Android.

## Jetons

### Couleurs

| Jeton | Valeur | Usage |
|---|---|---|
| `bg` | `#EEF2F6` | Fond des écrans |
| `surface` | `#FFFFFF` | Cartes pleines, barre du bas, écran des autorisations |
| `ink` | `#1D2731` | Titres, boutons principaux, bouton central |
| `ink7` | `#34414D` | Texte fort secondaire |
| `slate` | `#5D6E7E` | Texte secondaire |
| `slate4` | `#66768A` | Libellés, onglets inactifs (texte le plus clair autorisé) |
| `faint` | `#97A4B1` | Icônes inactives seulement (pas de texte) |
| `line` | `#DCE3EA` | Séparateurs |
| `track` | `#D9E2EA` | Fond des onglets segmentés, barres de progression |
| `chip` | `#E9EEF3` | Boutons gris |
| `brand` / `brandDeep` | `#E31B23` / `#A3161C` | Logo, SOS, rubans |
| `alert` | `#D91F36` | Titres et bandeaux d'alerte |
| `ribbon` | `#EE8C99` | Rubans décoratifs du fond |
| `blue` | `#2F86F6` | Ma position, interrupteurs activés, bouton secondaire bleu |
| `green` / `greenDot` | `#15803D` / `#22B455` | « Protégée », « calme » (texte / pastille) |
| `amber` / `amberDot` | `#B26B00` / `#F5B73B` | Attention, décision du veilleur |
| `thought` | `#5B47C9` | Ce que pense le veilleur (journal) |

### Typographie

Inter (titres et texte) et JetBrains Mono (journal du veilleur), toutes deux libres (OFL), embarquées dans l'app.

| Rôle | Taille / interligne | Graisse | Approche |
|---|---|---|---|
| Grand titre | 34 / 40 | 700 | −0,8 |
| Titre d'écran | 26 / 32 | 700 | −0,5 |
| Titre de carte | 22 / 26 | 700 | −0,4 |
| Titre d'onboarding | 30 / 36 | 700 | −0,8 |
| Ligne de liste | 17 | 600 | 0 |
| Corps | 16 / 23 | 400 | 0 |
| Valeur | 15 | 700 | 0 |
| Libellé de section | 13, MAJUSCULES | 500 | +0,5 |
| Journal | JetBrains Mono 12 / 18 | 400 | 0 |

### Formes, ombres, verre

- Rayons : 12 (bouton de carte), 14 (onglets, bandeaux), 22–26 (cartes), 32 (carte héros, fenêtre), 40 (aperçus d'onboarding), pilule.
- Carte pleine : blanc, ombre `0 8 24 −14` bleutée `rgba(52,86,120,0.22)` et `0 1 2` très légère.
- **Verre** : dégradé blanc 94 % → 72 % du haut vers le bas, flou d'arrière-plan 18, liseré blanc 1 px, reflet blanc en haut,
  ombre `0 18 40 −18 rgba(52,86,120,0.30)`. Sur Android 12+ : `Modifier.blur` sur une copie du fond ou RenderEffect ;
  en dessous d'Android 12, dégradé blanc opaque à 96 % sans flou (même rendu, sans transparence).
- Flottant sombre : `ink`, ombre `0 12 26 −6 rgba(29,39,49,0.45)` (bouton central, actions fortes).
- Fond décoratif : 2–3 rubans rose-rouge de 24 dp, inclinés de −18° à +16°, opacité ≤ 0,42, et deux lueurs bleues floues
  sur les bords. Décor seulement : ignoré par TalkBack, figé si « réduire les animations ».

## Composants

| Composant | Règles |
|---|---|
| Bouton pilule | Hauteur 48 (56–62 en bas d'écran). Variantes : sombre (principal), gris, contour 2 dp, blanc, bleu, rouge (SOS seulement) |
| Onglets segmentés | Piste `track` rayon 14, onglet choisi blanc rayon 11 avec ombre légère, libellés 13 MAJ |
| Ligne de chiffres | 4 colonnes : icône 22, valeur 15/700, libellé 13 `slate4` (ex. Voix / Musique / Marche / État) |
| Ligne de liste | Icône 26 trait 1,7, titre 17/600, sous-titre 13 `slate`, valeur à droite 18/700 |
| Bandeau | Rouge plein (dégradé `#EC2C40`→`#D11D31`, texte blanc) pour un danger ; vert clair pour « tout est calme » |
| Interrupteur | 52×30, activé = dégradé bleu, désactivé = `#A9B6C3` |
| Barre du bas | Blanche, 84 dp, 5 places : Accueil, Trajet, **bouton central**, Proches, Réglages. Actif = icône pleine + libellé `ink` gras ; inactif = `slate4` |
| Bouton central | 78 dp, `ink`, dépasse de 34 dp. Coupé : logo gris. Actif : anneaux qui tournent (6 s et 4 s), point qui bat (2,4 s), halo rouge |
| Logo en relief | Les anneaux du logo rendus en objet brillant (dégradés + reflet + ombre portée), rouge actif, blanc-gris coupé |
| Fenêtre d'alerte | Verre, rayon 32, titre rouge centré, journal des envois, « Je suis en sécurité » (blanc) puis « Appeler la police · 114 » (sombre) |
| Onboarding | Aperçu en verre légèrement incliné, titre 30, sous-titre 17, points d'étape, bouton sombre « Continuer », lien « Passer » |

## Écrans de la maquette

Démarrage · Onboarding 1–4 (protection, écoute locale, trajet, veilleur) · Autorisations (micro, position, notifications, SMS,
batterie, chacune avec sa raison) · Accueil coupé · Accueil actif · Le veilleur IA · Niveau sonore · Trajet en cours · Alerte en cours.

## Correspondance Compose (à l'implémentation)

- `CrTheme` : jetons ci-dessus en `CompositionLocal` (pas de couleur en dur dans les écrans) ; `Typography` Inter + JetBrains Mono.
- Composants : `GlassCard`, `SolidCard`, `PillButton(kind)`, `SegmentedTabs`, `StatRow`, `ListRow`, `Banner(kind)`, `CrSwitch`,
  `CrBottomBar(activeTab, protectionOn, onToggle)`, `ProtectionButton`, `Logo3D(on)`, `RibbonBackground`, `ListenOrb` (OrbMotion `WEAVING`),
  `LiveMap`, `WatcherCard`, `SoundChart`.
- Animations : `rememberInfiniteTransition`, désactivées si `Settings.Global.ANIMATOR_DURATION_SCALE == 0`.

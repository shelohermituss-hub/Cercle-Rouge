# Prompt — Maquettes Cercle Rouge (outil de design) · v2 « salle d'opérations »

> v2 du 2026-11-10 : direction demandée par le porteur du projet, à partir d'une image de référence (centre d'opérations nocturne, murs d'écrans vert phosphore, cartes tactiques, alertes rouges). La v1 était jugée trop plate.
> Copier le bloc entre les ``` dans l'outil de design.

## Ce qu'on reprend de l'image, et ce qu'on adapte

| Dans l'image | Dans Cercle Rouge | Pourquoi l'adapter |
|---|---|---|
| Mur d'écrans encadrés, vert phosphore, lueurs | ✅ Panneaux « moniteurs » à cadre biseauté, lueur verte, fond presque noir | — |
| Cartes tactiques avec repères rouges | ✅ Carte de Port-au-Prince en vert sur noir, grille, repères | Sans trajet ni alerte, la carte n'affiche **aucun point de position** (protection désactivée = aucune géolocalisation) |
| Lignes de terminal, coordonnées, données qui défilent | ✅ Journal en console, coordonnées, valeurs HUD | Lisibles : 14 dp minimum, pas de faux code illisible |
| Lignes de balayage, grain, vignettage d'écran CRT | ✅ Très légers (2 à 4 % d'opacité) | Lisibilité et batterie sur l'entrée de gamme |
| Rouge « SYSTEM COMPROMISED », « EXFILTRATION » | ✅ Rouge réservé au SOS et aux vraies alertes | Textes rassurants (« Alerte envoyée à 3 contacts »), jamais « compromis » ni « piraté » : le public a dès 12 ans |
| Réticules de visée, drone, soldat, caméras espion | ❌ Remplacés par le **radar** et les anneaux du cercle rouge | Pas d'imagerie d'arme ni de surveillance : l'app protège, elle n'espionne pas (N9) |
| Écrans qui clignotent | ⚠ Au plus 1 pulsation par seconde, jamais plus de 3 flashs par seconde | Risque d'épilepsie ; variante « réduire les animations » obligatoire |

```
Conçois les maquettes haute fidélité d'une application mobile Android nommée « Cercle Rouge ».
Format : écrans de téléphone 360 × 800 dp, Android (Material 3 comme base technique, mais un
style très personnalisé). Thème SOMBRE uniquement pour cette série. Langue : français (Haïti).
Un écran par artboard, nommé et numéroté, plus une planche de composants, une planche
d'effets et une planche de tokens.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. LE PRODUIT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Cercle Rouge protège une personne pendant un trajet, une sortie ou une urgence en Haïti
(Port-au-Prince, Delmas, Pétion-Ville). L'IA analyse les sons SUR le téléphone (rien n'est
envoyé), reconnaît des mots de détresse (« Anmwe ! », « Au secours ! ») ou une déviation de
trajet, demande « Es-tu en sécurité ? », puis prévient jusqu'à 5 contacts de confiance par SMS,
avec la position et un lien de suivi temporaire. Utilisateurs dès 12 ans, souvent sur des
téléphones d'entrée de gamme, parfois sous stress, d'une main, la nuit.

Sensation recherchée : « J'ai ma propre salle d'opérations dans la poche. » Puissant, précis,
en contrôle. Jamais menaçant : c'est l'utilisateur qui pilote, personne ne le surveille.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. DIRECTION ARTISTIQUE : « SALLE D'OPÉRATIONS NOCTURNE »
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Référence : un centre d'opérations de nuit, un mur d'écrans vert phosphore, des cartes
tactiques, des alertes rouges. Profondeur et matière, PAS de design plat.

Matière et profondeur :
- Fond : noir verdâtre profond avec un léger dégradé radial (plus clair au centre, vignettage
  sombre sur les bords), grain fin à 3 % d'opacité.
- Chaque bloc d'information est un « MONITEUR » : panneau rectangulaire à coins 12 dp, cadre
  (bezel) de 2 dp légèrement plus clair en haut qu'en bas (effet biseauté), surface intérieure
  très légèrement bombée (dégradé vertical subtil), reflet de verre en diagonale à 4 %,
  lignes de balayage horizontales (1 px tous les 3 px, 3 % d'opacité), lueur extérieure
  verte (glow, flou 16 dp, 25 % d'opacité) quand le moniteur est actif.
- En-tête de chaque moniteur : une barre fine avec un identifiant en capitales mono
  (« RADAR-01 », « CARTE // DELMAS », « JOURNAL // LOCAL »), un voyant (point lumineux) et
  l'heure à droite.
- Coins en équerre « ⌜ ⌝ ⌞ ⌟ » de 12 dp autour de la zone active de l'écran.
- Superposition des plans : panneaux qui se chevauchent légèrement, ombres portées vertes
  très diffuses, pour une vraie profondeur.
- Une carte tactique : fond noir, rues en vert sombre, grille de coordonnées, étiquettes de
  quartiers en mono (DELMAS, PÉTION-VILLE, CANAPÉ-VERT), repères ronds lumineux.
- Le motif signature est le CERCLE ROUGE : anneaux concentriques, radar, halo du bouton SOS.

Interdit : réticules de visée, drones, soldats, caméras espion, crânes, mots « compromis »,
« piraté », « exfiltration », faux code illisible, effets de glitch violents, emoji.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. TOKENS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Couleurs :
- bg/void #050807 (fond) · bg/panel #0B1310 (intérieur des moniteurs)
- bg/panel-raised #101C17 (feuilles, pavé PIN) · line/grid #13241C · line/bezel #1E3A2E
- phosphor/500 #3DDC84 (vert principal : état protégé, données actives, tracés)
- phosphor/300 #8CF5B8 (texte lumineux sur fond sombre, valeurs importantes)
- phosphor/glow rgba(61, 220, 132, 0.25) (lueurs)
- text/primary #D9F2E4 (texte courant, blanc teinté de vert) · text/secondary #7FA390
- brand/rouge #E5263B (logo, SOS, anneau de SOS) · alert/red #FF4155 (alertes)
- alert/glow rgba(255, 65, 85, 0.30)
- vigilance/amber #FFB020 (vérification, hors ligne)
- inactive #5E7768 (protection désactivée)
- focus #7DD3FC (anneau de focus, distinct du vert)
Contraste : texte ≥ 4,5:1 sur bg/panel (text/primary, text/secondary et phosphor/300 le
respectent) ; icônes et bordures actives ≥ 3:1. Chaque état = couleur + icône + libellé.

Typographie (doit supporter é è ê à ò ç du français et du créole) :
- Titres : Chakra Petch 600 (24 / 20 / 18), angles techniques, capitales possibles
- Texte : Inter 400 et 500 (16 de base ; 14 minimum)
- Données : JetBrains Mono 500, capitales, interlettrage +6 %, chiffres tabulaires
Espacements : 4, 8, 12, 16, 24, 32 dp. Marges latérales de 16 dp.
Icônes : Lucide, trait 1,75 dp, couleur phosphore, petite lueur quand actives.
Cibles tactiles ≥ 48 dp ; bouton SOS ≥ 88 dp (120 dp au centre du radar).

Effets (planche dédiée, avec les valeurs) : lueur verte, lueur rouge, lignes de balayage,
grain, vignettage, reflet de verre, cadre biseauté. Chacun avec une version « réduite »
(sans lueur ni balayage) pour le mode économie d'énergie.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. ANIMATIONS (durée et courbe sur chaque écran)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
- Démarrage : les moniteurs s'allument l'un après l'autre (ligne lumineuse horizontale qui
  s'ouvre en rectangle, 220 ms chacun, décalage 60 ms), puis 3 lignes de console s'écrivent
  (« CERCLE ROUGE · INITIALISATION », « ANALYSE LOCALE · PRÊTE », « 0 DONNÉE ENVOYÉE »).
  Total < 900 ms. Rejoué seulement au lancement à froid.
- Radar : balayage vert avec traînée qui s'estompe (rotation en 4 s, linéaire), seulement
  quand l'écoute IA est active.
- Onde du micro : 32 barres fines vert phosphore, sans aucun contenu audio affiché.
- Valeurs : chiffres qui « roulent » (200 ms) ; nouvelle ligne de journal tapée en 180 ms.
- Carte : repère de position avec halo pulsant (1,6 s) ; trace qui se dessine.
- SOS : appui long 1,5 s, anneau rouge lumineux qui se remplit, vibration ; puis compte à
  rebours de 5 s en très grands chiffres mono rouges avec lueur.
- Alerte : bordure rouge des moniteurs qui pulse (1 fois par seconde au plus), bandeau
  « ALERTE ENVOYÉE » qui défile lentement.
- Transitions : 250 ms entrée / 180 ms sortie ; pression des boutons : scale 0,97.
- Variantes obligatoires pour CHAQUE écran : « Réduire les animations » (tout statique) et
  « Mode discret » (aucune lueur rouge, aucun mot « alerte », interface sobre).
- Jamais plus de 3 flashs par seconde.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. ÉCRANS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Navigation basse à 4 onglets (icône + libellé) : Accueil · Trajet · Contacts · Journal,
dans une barre-moniteur. Le bouton SOS est accessible depuis TOUS les écrans principaux.

ÉCRAN 1 — Accueil « Console », protection DÉSACTIVÉE
Composition en mur de moniteurs (dans l'esprit de l'image de référence) :
- En haut, petit moniteur d'état : « PROTECTION DÉSACTIVÉE » (voyant gris), heure, batterie,
  réseau « 3G », « CONTACTS 3/5 ».
- Grand moniteur central « RADAR-01 » : radar à 3 anneaux, statique et gris ; au centre le
  bouton SOS rond 120 dp, rouge, halo rouge doux ; 4 boutons satellites autour (Écoute IA,
  Trajet, Contacts, Appeler le 114), chacun un petit cercle-moniteur avec icône et libellé.
- Deux petits moniteurs côte à côte en dessous :
  · « CARTE // PORT-AU-PRINCE » : carte tactique verte de la ville, SANS point de position,
    avec la mention « POSITION NON PARTAGÉE ».
  · « JOURNAL // LOCAL » : 3 lignes de console (« 20:12 DÉTECTION · NON CONFIRMÉE ·
    AUCUNE DONNÉE ENVOYÉE »).
- En bas : bouton large « Activer la protection » (contour vert lumineux).

ÉCRAN 2 — Accueil « Console », ÉCOUTE IA ACTIVE
- Même mur ; tous les moniteurs ont leur lueur verte ; voyant « ÉCOUTE IA ACTIVE » qui pulse.
- Radar avec balayage animé ; onde du micro sous le SOS.
- Ligne HUD : « ANALYSE LOCALE · 0 DONNÉE ENVOYÉE ».

ÉCRAN 3 — Accueil « Carte en direct », TRAJET PROTÉGÉ
- La carte tactique devient le grand moniteur plein écran : rues vertes, grille,
  quartiers ; corridor du trajet en bande verte translucide ; trace parcourue en vert vif ;
  repère de position avec halo ; drapeau de destination.
- Moniteur flottant en haut (coins en équerre) : « TRAJET PROTÉGÉ », « ARRIVÉE PRÉVUE 22:30 »,
  « RESTE 3,2 KM · 14 MIN », « GPS ±12 M », « SUIVI PAR : MAMAN, JO ».
- Colonne de 3 boutons ronds à droite : recentrer, écoute IA, « Je fais un arrêt ».
- En bas : SOS 88 dp à gauche, bouton « Je suis arrivé·e » à droite (demande le PIN).

ÉCRAN 4 — Vérification « Es-tu en sécurité ? »
- Tout l'écran s'assombrit, un moniteur ambre se pose au centre (lueur ambre).
- Anneau ambre qui se vide sur 20 s, chiffres mono 64 dp ; raison : « DÉVIATION DE TRAJET
  DÉTECTÉE ».
- Deux grands boutons de 64 dp : « Je vais bien » (vert, PIN ou empreinte), « J'ai besoin
  d'aide » (contour rouge lumineux). Texte : « Sans réponse, tes contacts seront prévenus. »

ÉCRAN 5 — SOS en cours
- Anneau rouge lumineux qui se remplit autour du bouton ; puis plein écran rouge sombre
  avec lignes de balayage : très grand « 5 », « ENVOI DE L'ALERTE À 3 CONTACTS », bouton
  « Annuler » très visible.

ÉCRAN 6 — Mode urgence
- Le mur de moniteurs passe en alerte : cadres rouges qui pulsent lentement, bandeau
  « ALERTE ENVOYÉE · 22:14 ».
- Moniteur « CONTACTS » : liste en temps réel (« MAMAN — SMS REÇU ✓ · A OUVERT LE LIEN »,
  « JO — NOTIFICATION ENVOYÉE », « TI PAUL — EN ATTENTE »).
- Moniteur « CARTE » : position partagée avec halo rouge.
- Moniteur « JOURNAL » : « 22:14:03 POSITION PARTAGÉE », « 22:14:05 ENREGISTREMENT CHIFFRÉ ».
- Boutons : « Appeler le 114 » (rouge plein), « Je vais bien » (PIN).

ÉCRAN 7 — Onboarding (3 écrans) en style « initialisation du système »
- 7a : séquence de démarrage (moniteurs qui s'allument), titre « Ta sécurité, sous ton
  contrôle. », bouton « Commencer », bouton SOS rouge.
- 7b : « L'IA écoute sur ton téléphone. Rien n'est envoyé. » Schéma en moniteur :
  micro → téléphone (cadenas lumineux) → aucune sortie, sauf alerte.
- 7c : choix de la conservation des preuves : 4 cartes-moniteurs SANS sélection par défaut
  (24 heures, 7 jours, 30 jours, effacer à la fin de l'alerte), un exemple sous chacune ;
  « Continuer » désactivé tant que rien n'est choisi.

ÉCRAN 8 — Consentement de l'adulte référent (12–15 ans), 2 écrans
- 8a côté jeune : « Un parent ou un adulte de confiance doit confirmer », champ +509,
  choix du lien (Parent, Tuteur, Autre adulte) en puces vertes, état d'attente avec un
  voyant ambre et « En attendant, le bouton SOS fonctionne ».
- 8b côté adulte : « Demande de Naïka (13 ans) », deux moniteurs : « TU VERRAS » (ses
  alertes) / « TU NE VERRAS JAMAIS » (position hors alerte, trajets, journal) ; boutons
  « Confirmer avec mon PIN » / « Refuser ».

ÉCRAN 9 — Contacts (5 maximum) : compteur « 3/5 », cartes-moniteurs avec rang, rôle,
canaux, statut (« ACCEPTÉ », « EN ATTENTE »), langue ; « Ajouter », « Tester l'alerte ».

ÉCRAN 10 — Démarrer un trajet : puces de modèles (« Je rentre chez moi », « Rendez-vous »,
« Transport »), destination, heure, mode (marche, voiture, tap-tap, taxi, moto), contacts
qui suivent, « Démarrer le trajet protégé » (pavé PIN en variante).

ÉCRAN 11 — Journal : grand moniteur console, lignes horodatées vertes, filtres, mention
« CE JOURNAL RESTE SUR TON TÉLÉPHONE · CHAÎNE INTACTE ✓ ».

ÉCRAN 12 — Coffre-fort des preuves : moniteur verrouillé (cadenas lumineux), liste
d'enregistrements chiffrés avec « suppression dans 18 h ».

ÉCRAN 13 — Page web pour un proche (mobile web, sans compte), même style : « Naïka a
peut-être besoin d'aide », carte tactique, chronologie, boutons « Appeler Naïka »,
« J'ai pris contact », « Appeler le 114 », « Installe Cercle Rouge pour écouter les
enregistrements ».

ÉCRAN 14 — Notification Android (« Cercle Rouge · Écoute IA active », actions SOS et
Désactiver) et widget 2×2 en forme de mini-moniteur avec le bouton SOS.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. COMPOSANTS (planche)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Moniteur (repos, actif avec lueur, alerte, ambre, désactivé) · en-tête de moniteur ·
bouton SOS (repos, appui, progression, désactivé) · satellite · voyant d'état · puce ·
ligne de console · carte tactique · carte-contact · pavé PIN · feuille de vérification ·
barre de navigation · champ +509 · carte de choix · bandeau hors ligne (« HORS LIGNE ·
L'ALERTE PARTIRA PAR SMS »). États pressé, focus, désactivé et chargement pour chacun.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. CONTRAINTES NON NÉGOCIABLES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
- Le SOS reste l'élément le plus visible de chaque écran principal, atteignable en 1 geste.
- Une seule action principale par écran malgré la densité du mur de moniteurs.
- Lisible avec une police à 200 % (variante de l'écran 1 où les moniteurs s'empilent).
- Aucune donnée sensible sur l'écran de verrouillage.
- Ton : tutoiement, phrases courtes, lisible par un enfant de 12 ans, jamais alarmiste ; ne
  jamais promettre « nous détectons tout danger ».
- Contenus fictifs uniquement (prénoms haïtiens inventés, numéros +509 fictifs).
```

## Après génération : vérifier

- [ ] L'écran 1 n'affiche **aucun** point de position sur la carte (protection désactivée).
- [ ] Le SOS domine visuellement chaque écran principal, malgré la densité.
- [ ] Les variantes « réduire les animations » et « mode discret » existent pour chaque écran.
- [ ] Contrastes vérifiés (texte ≥ 4,5:1), accents bien rendus (é, ò, ç), aucun réticule ni mot alarmiste.
- [ ] Revue `/review-animations` et `/emil-design-eng` sur la planche des animations et des effets.

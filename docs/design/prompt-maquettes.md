# Prompt — Maquettes Cercle Rouge (outil de design)

> À coller tel quel dans l'outil de design. Direction : **HUD tactique**, sobre et accessible, et non une « interface de hacker » (pas de vert Matrix ni de glitch).
> L'accueil est **adaptatif** : un radar quand aucun trajet n'est en cours, une carte en direct pendant un trajet ou une alerte (voir la justification dans `docs/design/README.md`).

```
Conçois les maquettes haute fidélité d'une application mobile Android nommée « Cercle Rouge ».
Format : écrans de téléphone 360 × 800 dp (Android, Material 3 comme base), thème SOMBRE en
priorité, plus une variante claire des écrans 1 et 3. Langue de l'interface : français
(Haïti). Livre un écran par artboard, nommé et numéroté, plus une planche de composants
et une planche de « design tokens ».

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. LE PRODUIT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Cercle Rouge protège une personne pendant un trajet, une sortie ou une urgence en Haïti
(Port-au-Prince, Delmas, Pétion-Ville). L'IA analyse les sons SUR le téléphone (rien
n'est envoyé), détecte des mots de détresse (« Anmwe ! », « Au secours ! ») ou une
déviation de trajet, demande « Es-tu en sécurité ? », puis alerte jusqu'à 5 contacts
de confiance par SMS et notification, avec la position et un lien de suivi temporaire.
Utilisateurs : de 12 ans à l'âge adulte, souvent sur des téléphones d'entrée de gamme,
parfois sous stress, d'une seule main, la nuit, dans un tap-tap ou à moto.

Promesse émotionnelle : « Je suis protégé·e et je garde le contrôle. » L'interface doit
RASSURER et inspirer la maîtrise, comme un tableau de bord tactique calme, jamais faire
peur ni donner l'impression d'être surveillé·e.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. DIRECTION ARTISTIQUE : « HUD TACTIQUE ÉPURÉ »
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Inspiration : interfaces de centre d'opérations, cockpits, radars, montres outdoor
haut de gamme. Données en temps réel, mais beaucoup d'espace négatif.
- Fond presque noir bleuté, fine grille de repérage (lignes à 4 % d'opacité, maille 24 dp).
- Lignes fines (1 dp), coins de cadrage en équerre « [ ] » autour des zones actives,
  petites étiquettes en capitales monospaces (ex. « GPS ±12 m », « RÉSEAU 3G »).
- Le motif signature est un CERCLE (le « cercle rouge ») : anneaux concentriques,
  radar, halo autour du bouton SOS.
- Surfaces : cartes à coins arrondis 16 dp, bordure 1 dp, pas d'ombres lourdes ;
  léger flou de fond uniquement pour les feuilles modales.
- Interdit : vert Matrix, effets glitch, texte qui défile illisible, faux code,
  crânes, cibles ou réticules d'arme, emoji comme icônes, néons saturés partout.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. DESIGN TOKENS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Couleurs (thème sombre) :
- bg/base #0B0F14 · bg/surface #121821 · bg/elevated #18212C · line/grid #1E2A36
- text/primary #E6EDF3 · text/secondary #8B9AAB · text/disabled #4A5868
- brand/rouge #E5263B : réservé au logo, au bouton SOS et aux alertes
- state/protégé #2DD4BF (cyan-turquoise) : protection active, écoute IA active
- state/vigilance #F5A524 (ambre) : vérification en cours, hors ligne, batterie faible
- state/alerte #FF3B4E : alerte envoyée, mode urgence
- state/inactif #5B6B7D : protection désactivée
Chaque état combine TOUJOURS couleur + icône + texte (jamais la couleur seule).
Contraste minimal 4,5:1 pour le texte et 3:1 pour les icônes et les bordures actives.
Thème clair (écrans 1 et 3) : bg #F5F7FA, surface #FFFFFF, texte #0F172A, mêmes
couleurs d'état assombries pour garder le contraste.

Typographie (doit supporter é è ê à ò ç — français et créole) :
- Titres : Space Grotesk 600 (24 / 20 / 18)
- Texte : Inter 400 et 500 (16 de base, interligne 1,5 ; minimum 14 pour les légendes)
- Données HUD : JetBrains Mono 500, capitales, interlettrage +4 %, chiffres tabulaires
  (heures, distances, minuteurs, coordonnées)
Espacements : multiples de 4 et 8 dp (8, 12, 16, 24, 32). Marges latérales de 16 dp.
Icônes : Lucide, trait de 1,75 dp, 24 dp, contour uniquement (rempli = état actif).
Cibles tactiles ≥ 48 dp ; bouton SOS ≥ 88 dp ; actions critiques dans la moitié basse.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. ANIMATIONS (à documenter sur chaque écran, avec durée et easing)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Style « temps réel » mais calme : 1 à 2 éléments animés par écran au maximum.
- Radar : balayage lent (une rotation en 4 s, linéaire) UNIQUEMENT quand l'écoute IA
  est active ; arrêté quand la protection est désactivée.
- Onde sonore : 24 barres fines réagissant au niveau du micro (sans aucun contenu
  audio affiché), 60 % d'opacité.
- Valeurs HUD : chiffres qui « roulent » à la mise à jour (200 ms, ease-out).
- Journal : nouvelle ligne qui apparaît par fondu + glissement de 8 dp (180 ms).
- Carte : point de position avec halo qui pulse (1,6 s, ease-in-out) ; la trace se
  dessine progressivement ; le corridor de trajet est une bande translucide.
- SOS : appui long de 1,5 s avec un anneau de progression qui se remplit autour du
  bouton, vibration à la fin ; puis compte à rebours de 5 s, chiffres de 64 dp.
- Vérification : anneau qui se vide sur 20 s autour de « Es-tu en sécurité ? ».
- Alerte : anneaux rouges qui s'élargissent depuis le centre (au plus 1 pulsation par
  seconde ; jamais plus de 3 flashs par seconde, risque d'épilepsie).
- Transitions d'écran : 250 ms entrée / 180 ms sortie, ease-out ; spring léger sur
  les boutons pressés (scale 0,97).
- Prévoir pour chaque écran la variante « Réduire les animations » (tout est statique,
  l'information reste identique) et la variante « Mode discret » (aucune animation
  visible, aucun mot « alerte » ni « danger », couleurs neutres).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5. ÉCRANS À PRODUIRE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Barre d'état Android en haut ; barre de navigation inférieure à 4 onglets avec
icône + libellé : Accueil · Trajet · Contacts · Journal (Réglages via l'avatar en haut
à droite). Le bouton SOS est accessible depuis TOUS les écrans principaux.

ÉCRAN 1 — Accueil « Radar », protection DÉSACTIVÉE (état par défaut)
- En haut : logo (cercle rouge fin + « Cercle Rouge »), avatar, puce d'état grise
  « PROTECTION DÉSACTIVÉE ».
- Centre : grand radar de 280 dp (3 anneaux concentriques fins, statique, gris).
  Au centre du radar : bouton SOS rond de 120 dp, rouge plein, texte « SOS » + légende
  « Appui long 1,5 s ».
- Autour du radar, 4 boutons satellites en orbite (cercles de 64 dp avec icône + libellé
  en dessous) : « Trajet protégé » (route), « Écoute IA » (micro), « Contacts » (3/5),
  « Appeler le 114 » (téléphone).
- Bas : grand bouton pleine largeur « Activer la protection » (contour cyan) et une
  ligne HUD : « BATTERIE 82 % · RÉSEAU 3G · CONTACTS JOIGNABLES 3/3 ».
- Aucune carte et aucune position : la localisation est coupée dans cet état (c'est voulu).

ÉCRAN 2 — Accueil « Radar », ÉCOUTE IA ACTIVE
- Même composition ; puce cyan « ÉCOUTE IA ACTIVE » avec un point qui pulse.
- Radar cyan avec balayage animé ; onde sonore discrète sous le bouton SOS.
- Ligne HUD : « ANALYSE LOCALE · 0 DONNÉE ENVOYÉE ».
- Bouton bas : « Désactiver » (demande le PIN).

ÉCRAN 3 — Accueil « Carte en direct », TRAJET ACTIF (la vue carte de l'accueil)
- Carte sombre stylisée plein écran (Port-au-Prince : Delmas → Pétion-Ville), routes
  gris-bleu, pas de couleurs vives ; corridor du trajet en bande cyan translucide ;
  trace parcourue en cyan plein ; point de position avec halo pulsant ; drapeau de
  destination.
- Panneau HUD flottant en haut (coins en équerre) : « TRAJET PROTÉGÉ », « ARRIVÉE
  PRÉVUE 22:30 », « RESTE 3,2 KM · 14 MIN », « GPS ±12 m », « SUIVI PAR : Maman, Jo ».
- À droite, colonne de 3 boutons ronds de 48 dp : recentrer, écoute IA on/off, « Je
  fais un arrêt ».
- Bas : bouton SOS rond de 88 dp à gauche et grand bouton « Je suis arrivé·e » à
  droite (cyan plein, demande le PIN).
- Petit indicateur « Écoute IA active » sous le panneau HUD.

ÉCRAN 4 — Vérification « Es-tu en sécurité ? » (feuille plein écran, priorité maximale)
- Fond assombri ; anneau ambre qui se vide (compte à rebours de 20 s, chiffres
  JetBrains Mono 64 dp) ; raison en clair : « Déviation de trajet détectée ».
- Deux boutons empilés pleine largeur de 64 dp : « Je vais bien » (cyan, demande le PIN
  ou l'empreinte) et « J'ai besoin d'aide » (rouge).
- Texte : « Sans réponse, tes contacts seront prévenus. »

ÉCRAN 5 — SOS en cours de déclenchement
- Anneau de progression rouge autour du bouton (appui long), puis plein écran : grand
  compte à rebours « 5 », texte « Envoi de l'alerte à 3 contacts », bouton « Annuler »
  très visible.

ÉCRAN 6 — Mode urgence (alerte envoyée)
- Bandeau rouge « ALERTE ENVOYÉE · 22:14 » avec une icône ; anneaux d'onde lents.
- Liste HUD en temps réel de chaque contact avec son état : « Maman — SMS reçu ✓ ·
  a ouvert le lien », « Jo — notification envoyée », « Ti Paul — en attente ».
- Lignes de journal qui apparaissent : « 22:14:03 POSITION PARTAGÉE », « 22:14:05
  ENREGISTREMENT AUDIO CHIFFRÉ ».
- Boutons : « Appeler le 114 » (composeur), « Je vais bien » (PIN).

ÉCRAN 7 — Onboarding (3 écrans)
- 7a Bienvenue : « Ta sécurité, sous ton contrôle. » + illustration du cercle.
- 7b « L'IA écoute sur ton téléphone. Rien n'est envoyé. » Schéma : micro → téléphone
  (cadenas) → rien ne sort, sauf alerte.
- 7c Choix de la conservation des preuves : 4 cartes radio SANS sélection par défaut
  (24 heures, 7 jours, 30 jours, supprimer à la fin de l'alerte), avec un exemple sous
  chaque option ; bouton « Continuer » désactivé tant que rien n'est choisi.

ÉCRAN 8 — Consentement de l'adulte référent (12–15 ans), 2 écrans
- 8a Côté jeune : « Un parent ou un adulte de confiance doit confirmer » ; champ numéro
  +509, choix du lien (parent, tuteur, autre adulte) ; état d'attente avec une animation
  lente et le rappel « En attendant, le bouton SOS fonctionne ».
- 8b Côté adulte : « Demande de Naïka (13 ans) » ; deux colonnes claires : « Tu verras »
  (ses alertes si elle a besoin d'aide) / « Tu ne verras jamais » (sa position hors
  alerte, ses trajets, son journal) ; boutons « Confirmer avec mon PIN » / « Refuser ».

ÉCRAN 9 — Contacts de confiance (5 maximum)
- Compteur HUD « 3/5 » ; cartes de contact avec rang (1, 2, 3, glissables), rôle
  (« Répondant principal »), canaux (icônes SMS, notification), statut de consentement
  (« Accepté », « En attente »), langue (FR, HT) ; bouton « Ajouter » ; bouton « Tester
  l'alerte ».

ÉCRAN 10 — Démarrer un trajet
- Modèles en puces : « Je rentre chez moi », « Rendez-vous », « Transport » ;
  destination ; heure d'arrivée estimée ; mode (marche, voiture, tap-tap, taxi, moto) ;
  contacts qui suivent ; bouton « Démarrer le trajet protégé » (exige un PIN : afficher
  le pavé de PIN dans une variante).

ÉCRAN 11 — Journal local (style console sobre)
- Lignes horodatées en JetBrains Mono : « 20:12 DÉTECTION · NON CONFIRMÉE · AUCUNE
  DONNÉE ENVOYÉE » ; filtres en puces ; mention « Ce journal reste sur ton téléphone ».

ÉCRAN 12 — Coffre-fort des preuves (après empreinte)
- Liste d'enregistrements chiffrés (icône cadenas, durée, date, « suppression dans
  18 h »), actions conserver / supprimer / exporter.

ÉCRAN 13 — Page web de suivi pour un proche (mobile web, sans compte)
- « Naïka a peut-être besoin d'aide » ; carte avec la dernière position et son âge
  (« il y a 2 min ») ; raison et heure ; chronologie ; boutons « Appeler Naïka »,
  « J'ai pris contact », « Appeler le 114 » ; mention « Installe Cercle Rouge pour
  écouter les enregistrements » ; lien valable jusqu'à 02:14.

ÉCRAN 14 — Notification persistante Android + widget d'écran d'accueil
- Notification : « Cercle Rouge · Écoute IA active » avec les actions [SOS] [Désactiver].
- Widget 2×2 : bouton SOS + état.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
6. COMPOSANTS (planche dédiée)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Bouton SOS (repos, pressé, progression, désactivé) · bouton satellite · puce d'état
(désactivé, protégé, vigilance, alerte, hors ligne) · panneau HUD à coins en équerre ·
ligne de journal · carte contact · pavé PIN · feuille de vérification · barre de
navigation · champ téléphone +509 · cartes radio · bandeau hors ligne (« Hors ligne —
l'alerte partira par SMS »). Chaque composant avec ses états pressé, focus,
désactivé et chargement.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
7. CONTRAINTES NON NÉGOCIABLES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
- Une seule action principale par écran ; le SOS est toujours atteignable en 1 geste.
- Lisible avec une police à 200 % (montrer une variante de l'écran 1) ; libellés sur
  toutes les icônes ; état jamais signalé par la couleur seule.
- Pas de texte sous 14 dp ; contraste AA vérifié ; zones sûres Android respectées.
- Aucune donnée visible sur l'écran de verrouillage (notification neutre).
- Ton des textes : tutoiement, phrases courtes, simple (lisible par un enfant de 12 ans),
  jamais alarmiste ; ne jamais promettre « nous détectons tout danger ».
- Contenus fictifs uniquement (prénoms haïtiens inventés, numéros +509 fictifs).
```

## Après génération : vérifier

- [ ] L'écran 1 n'affiche **aucune** carte ni position (protection désactivée).
- [ ] Le SOS est présent et atteignable sur les écrans 1, 2, 3, 9, 10 et 11.
- [ ] Les variantes « réduire les animations » et « discret » existent.
- [ ] Les contrastes sont vérifiés (plugin de contraste) et les accents sont bien rendus (é, ò).
- [ ] Revue `/review-animations` et `/emil-design-eng` sur la planche des animations.

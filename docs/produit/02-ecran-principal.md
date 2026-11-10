# Module produit 2 — Écran principal : mode protection

**Objectif** : donner un contrôle simple et immédiat, utilisable sous stress, d'une main, en moins de 2 secondes.

Modules logiciels : `ui-mobile`, `alert-engine`, `location-engine`, `sensor-engine`.

## Éléments

| Élément | Comportement | Règle |
|---|---|---|
| **Activer / Désactiver la protection** | Gros bouton principal. Passe à `ARMED_AUDIO_ONLY` ou `ARMED_FULL` selon les permissions. La désactivation exige le PIN si configuré | Cible tactile ≥ 72 dp ; libellé + icône + couleur (jamais la couleur seule) |
| **SOS** | Appui long de 1,5 s **ou** glissement, pour éviter les déclenchements accidentels en poche → `EMERGENCY_MODE`. Compte à rebours d'annulation de 5 s, configurable de 0 à 10 s | Toujours visible, quel que soit l'état ; fonctionne en `OFF` |
| **Fausse alerte / Je vais bien** | Visible seulement dans les états `ALERT_*` et `EMERGENCY_MODE`. PIN requis si configuré | Envoie un message « fausse alerte » aux proches déjà alertés |
| **Statut en temps réel** | Écoute IA · batterie · réseau · GPS · nombre de contacts joignables (consentis + canal disponible) | Mise à jour ≤ 1 s ; alerte visuelle si 0 contact joignable |
| **Minuteur de trajet** | Modèles : « Je rentre chez moi », « Je suis en rendez-vous », « Je suis en transport » → module 4 | Pré-remplit la destination et la durée à partir de l'historique **local** |
| **Je suis arrivé** | Termine `TRIP_ACTIVE`, arrête le suivi, envoie « Arrivé·e » aux proches qui suivaient | PIN si configuré (sinon un agresseur peut « terminer » le trajet) |
| **Historique** | Alertes et trajets, issus du journal local | Respecte la rétention ; aucune donnée au-delà |
| **Contacts d'urgence** | Accès en 1 tap, avec appel direct | — |
| **Appel secours** | Bouton vers le composeur avec le numéro local pré-rempli | Pays de lancement : **Haïti** (police 114, pompiers 115, ambulance 116, à confirmer auprès de la PNH et de la Protection civile avant la bêta). Les autres pays sont configurables (diaspora) : France (17, 15, 18, 112), Union européenne (112), États-Unis et Canada (911). Le pays est détecté via la carte SIM, puis modifiable |

## Règles

| ID | Règle |
|---|---|
| R-HOME-01 | **DOIT** : le SOS est atteignable en ≤ 1 geste depuis l'écran principal, le widget, la tuile de réglages rapides et la notification persistante. |
| R-HOME-02 | **DOIT** : l'état affiché est **dérivé** de l'état persistant d'`alert-engine` ; l'interface ne maintient aucun état parallèle. |
| R-HOME-03 | **DOIT** : en mode discret, aucun son, aucun texte « alerte » ou « danger » à l'écran. Seule une vibration codée est produite. |
| R-HOME-04 | **DOIT** : utilisable avec TalkBack, une police à 200 % et un fort contraste (WCAG 2.2 AA). |
| R-HOME-05 | **DOIT** : l'écran fonctionne hors ligne ; les éléments dépendants du réseau affichent « en attente d'envoi ». |
| R-HOME-06 | **DEVRAIT** : l'écran s'affiche en < 500 ms après un démarrage à froid sur un appareil d'entrée de gamme (cible : 2 Go de RAM, Android 10). |
| R-HOME-07 | **DOIT** : un appel au numéro d'urgence passe **toujours** par le composeur système (⚠ Android). |

## États visuels

Un composant par état (`OFF`, `ARMED_AUDIO_ONLY`, `ARMED_FULL`, `TRIP_ACTIVE`, `ALERT_PENDING`, `ALERT_SENT`, `EMERGENCY_MODE`), chacun avec ses variantes **normale**, **discrète** et **leurre** (module 9). Les couleurs et typographies seront fixées par le design system (❓ identité visuelle à définir).

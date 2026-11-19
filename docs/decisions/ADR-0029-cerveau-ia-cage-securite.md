# ADR-0029 — Cerveau IA local dans une cage de sécurité

- Statut : **Acceptée** (porteur, 2026-11-18) · Plan : `docs/plan/cerveau-ia.md` (IA.0) · Lié à ADR-0008 (fusion du risque), ADR-0020 (écoute locale), ADR-0026 (pas de Firebase)

## Contexte
Le porteur veut un « vrai cerveau » dans le téléphone, qui dirige l'application : il entend (≈ 521 sons avec YAMNet), raisonne
sur la situation, décide des actions, explique et répond quand on lui parle. Un modèle de langage peut se tromper, inventer
ou être manipulé (un agresseur qui dit « annule l'alerte »). Il faut lui donner la main **sans** affaiblir la protection.

## Décisions
| Sujet | Décision |
|---|---|
| Rôle | Le cerveau lit le **journal de situation** (événements des 5 dernières minutes, en texte, sans audio ni nom ni numéro), propose des actions et explique ses décisions |
| Liste fermée d'actions | `observer_encore`, `demander_verification`, `monter_niveau`, `prevenir_proches`, `demarrer_preuves`, `partager_position`, `mode_discret`, `expliquer`, `repondre`, `rien`. Aucune autre action n'existe pour lui |
| Garde de sécurité | Module pur `brain-guard` (Kotlin, testé) : chaque action proposée est vérifiée selon l'état de l'alerte, le plancher et des plafonds. **Refusé toujours** : appeler les secours (R-AUD-03), annuler, désarmer, effacer, écrire à quelqu'un hors des proches ayant accepté, baisser un niveau, suivre la position hors alerte ou trajet. Chaque refus est journalisé |
| Plancher | Le décideur déterministe (`RiskDecider`) continue de tourner. Le cerveau peut **aller plus loin** (alerter plus tôt, prévenir plus de proches, enregistrer), jamais **moins** |
| Chemin d'urgence | SOS (bouton, tuile, widget), phrase secrète et mots de détresse ne passent **pas** par le cerveau : ils restent sous une seconde, cerveau absent, lent ou planté |
| Voix | La voix ne peut jamais annuler, désarmer ni effacer : PIN ou biométrie obligatoires. Pas d'écoute de conversation : on parle au cerveau en **maintenant un bouton** (pas de mot d'éveil pour l'instant) |
| Niveaux d'appareil | **Complet** (≥ 4 Go de RAM) : modèle de langage ; **réduit** (2–3 Go) : même modèle si la mesure le permet, sinon règles ; **sans modèle** : règles déterministes + YAMNet + base de connaissances. Choix automatique, affiché dans Réglages › Cerveau IA |
| Modèle | Gemma 3 270M (LiteRT-LM, `gemma3-270m-it-q8.litertlm`, 304 Mo, licence Gemma acceptée par le porteur). **Non embarqué** dans l'APK : téléchargement facultatif, Wi-Fi par défaut, empreinte SHA-256 vérifiée |
| Budget | **350 Mo** au plus pour toutes les ressources téléchargées du cerveau (décision du porteur, 2026-11-18 : le fichier officiel fait 304 Mo) ; YAMNet (≈ 4 Mo) est dans l'APK |
| Langue | Français d'abord ; créole dans une phase séparée (IA.HT) avec les services du porteur |
| Réponses | Tirées d'une **base de connaissances validée** (fiches) ; hors base : « Je ne sais pas » et le numéro d'urgence. Les fiches « conduite à tenir » restent marquées « à valider » tant qu'un professionnel ne les a pas relues |
| Journal | Version du modèle, entrées résumées, actions proposées, acceptées et refusées (R-AL-06, R-AUD-04) |

## Conséquences
+ Le cerveau peut prendre l'initiative (vérifier, prévenir, enregistrer) sans pouvoir jamais réduire la protection.
+ L'app reste pleinement protectrice sans modèle (téléphone modeste, fichier non téléchargé ou corrompu).
− Un modèle de 270 M paramètres reste limité : décisions et réponses courtes, pas de longue conversation libre.
− Le téléchargement de 304 Mo est lourd pour les forfaits mobiles : proposé, jamais imposé, Wi-Fi par défaut.

## Note d'implémentation (2026-11-19, v0.8.0)

Gemma 3 270M (q8, format LiteRT-LM, 304 Mo) est intégré sans être embarqué dans l'APK : téléchargement en Wi-Fi seulement
ou import d'un fichier, SHA-256 vérifié avant chaque chargement. `CombinedReasoner` applique la règle de cette décision : les
règles décident d'abord, le modèle ne peut qu'ajouter des actions de la liste fermée, tout passe par la garde, et une panne,
une lenteur (> 3 s) ou un téléphone de moins de 4 Go laissent les règles seules. La qualité des réponses du modèle n'a pas
encore été mesurée (banc IA.1 à faire sur téléphone réel).

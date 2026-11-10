# Protocole de mesure des prototypes (S0.18 à S0.21)

Application : « CR Prototypes » (`spikes/`, fichier `app-debug.apk`). À faire sur **chaque téléphone de test** (au moins 4 : Android 10 d'entrée de gamme, Tecno ou Infinix, Samsung A, Pixel récent).

## Avant de commencer

1. Installer l'APK (autoriser l'installation depuis cette source).
2. **Ne pas** mettre l'application en liste blanche de la batterie au premier essai : on veut voir le comportement par défaut du fabricant.
3. Batterie entre 80 et 100 %, **chargeur débranché**, pas d'économiseur d'énergie.
4. Noter le modèle exact du téléphone (il s'affiche en haut de l'application).

## S0.18 · Micro écran éteint + IA (2 h)

1. Section S0.18 : laisser « wake lock » coché, appuyer sur **Démarrer**, accepter le micro et les notifications.
2. Vérifier que la notification « CR Prototypes » et l'indicateur du micro apparaissent.
3. Verrouiller l'écran, laisser le téléphone **2 h** dans une pièce avec des bruits ordinaires (conversation, radio, rue).
4. Rouvrir l'application, appuyer sur **Arrêter**.
5. Recommencer une 2ᵉ fois **sans** wake lock (case décochée), 1 h suffit.

| Mesure | Où la lire | Réussi si |
|---|---|---|
| Survie | Une ligne `battement` par minute, sans trou | ≥ 120 battements consécutifs |
| Batterie | `batterie=` au début et à la fin | ≤ 4 % par heure |
| Coût de l'IA | `ms_moy`, `ms_max` | `ms_moy` < 30 ms sur l'entrée de gamme |
| Micro coupé par le système | `blocs_muets_%` | 0 % |
| Détection | `derniere_classe` | Des classes plausibles (Speech, Music…) |

## S0.19 · Audio chiffré (2 minutes)

1. Section S0.19 : **Enregistrer 30 s et vérifier**, parler près du micro.
2. À la fin, l'enregistrement doit être **rejoué** dans le haut-parleur.
3. Si « ÉCHEC » apparaît : cocher « AAC au lieu d'Opus » et recommencer.

| Mesure | Réussi si |
|---|---|
| `fuite_en_clair` | `false` |
| `nouveaux_fichiers` | Un seul (le fichier chiffré) |
| `entropie` | > 7,9 bits/octet |
| Relecture | L'audio rejoué est bien celui qui a été dit |
| `cpu_%` | < 10 % |

## S0.20 · SMS avec accents (10 minutes)

1. Section S0.20 : saisir un numéro **Digicel** (+509…), **Envoyer le SMS de test**.
2. Noter l'heure de réception sur le téléphone destinataire.
3. Recommencer vers un numéro **Natcom**, puis depuis une SIM de l'autre opérateur.

| Mesure | Réussi si |
|---|---|
| « envoyé au réseau » | Tous les segments OK |
| « remis au destinataire » | < 10 s (l'accusé de remise dépend de l'opérateur ; noter s'il n'arrive jamais) |
| Message reçu | Accents et créole intacts (é, è, ò), segments réassemblés en un seul message |

## S0.21 · GPS en trajet réel (1 h)

1. Section S0.21 : **Démarrer**, accepter la position (« Lors de l'utilisation de l'appli »).
2. Faire un vrai trajet d'environ 1 h (tap-tap, moto, à pied), écran verrouillé, téléphone dans la poche.
3. À l'arrivée : **Arrêter**. Le résumé affiche le nombre de positions, la précision médiane et le pire écart.
4. Si possible, refaire un trajet en cochant « sans Google Play Services ».

| Mesure | Réussi si |
|---|---|
| Positions | Environ 360 pour 1 h (une toutes les 10 s) |
| Précision médiane | < 30 m |
| Pire écart | < 60 s |
| Batterie | ≤ 3 % par heure |

## Envoyer les résultats

1. Section Résultats : **Partager les résultats**, envoyer les fichiers CSV (e-mail, Drive, WhatsApp à toi-même).
2. ⚠ Le fichier `S0.21-gps.csv` contient tes positions du trajet : le partager seulement avec l'équipe.
3. **Effacer les mesures** ensuite.

Je compile les CSV de tous les téléphones dans `docs/plan/rapport-S0.md`, avec la décision go/no-go pour chaque question.

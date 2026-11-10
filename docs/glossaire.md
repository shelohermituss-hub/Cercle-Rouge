# Glossaire

| Terme | Définition |
|---|---|
| **Armé** | État où la protection est active (`ARMED_*`, `TRIP_ACTIVE`) |
| **Alerte** | Situation ouverte à partir du niveau 1, identifiée par un `alertId` |
| **Battement (heartbeat)** | Signal périodique envoyé au serveur pendant un trajet ; son absence déclenche « perte de contact » |
| **Canal** | Moyen d'envoi : push, SMS, appel, WhatsApp, e-mail |
| **Chaîne de hachages** | Suite d'entrées dont chacune contient le hachage de la précédente ; rend une suppression ou une modification détectable |
| **Contact de confiance / proche** | Personne qui a **accepté** de recevoir les alertes de l'utilisateur |
| **Corridor** | Zone tolérée autour de l'itinéraire prévu |
| **Crypto-shredding** | Suppression par destruction de la clé de chiffrement |
| **Fausse alerte** | Alerte déclarée sans danger par l'utilisateur (avec PIN) |
| **KWS** | Keyword spotting : détection de mots-clés |
| **LiteRT** | Nouveau nom de TensorFlow Lite, moteur d'inférence embarqué de Google |
| **Mode discret** | Présentation sans son ni texte d'alerte ; vibrations codées |
| **Mode leurre** | Écran neutre qui masque l'interface de Cercle Rouge |
| **Niveau (0 à 4)** | Degré d'escalade : information, vérification, proches, élargie, autorités |
| **Phrase secrète** | Phrase personnelle qui déclenche une alerte silencieuse |
| **PIN de contrainte** | PIN alternatif qui semble désarmer mais alerte en silence |
| **Pré-tampon** | Les 5 dernières secondes d'audio, gardées en mémoire et attachées à une alerte |
| **Service de premier plan** | Service Android visible (notification) qui peut fonctionner écran éteint ; typé (`microphone`, `location`, `camera`) |
| **Signal** | Sortie d'un moteur (probabilité d'un événement), pas une décision |
| **Score de risque (R)** | Fusion des signaux entre 0 et 1, calculée par `alert-engine` |
| **VAD** | Voice Activity Detection : détection d'activité vocale |
| **Vérification** | Niveau 1 : « Es-tu en sécurité ? » avec un délai |

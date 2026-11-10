# Module produit 4 — Détection de déviation d'itinéraire

**Objectif** : repérer un écart anormal par rapport au trajet prévu.

Modules logiciels : `location-engine` (calcul), `alert-engine` (décision), `backend-api` (perte de contact).

## Déclaration d'un trajet

L'utilisateur déclare : point de départ (position actuelle par défaut), destination, heure d'arrivée prévue (calculée puis modifiable), mode de transport (marche, voiture, bus, taxi ou moto-taxi, train), arrêts prévus (optionnels), et les contacts qui suivent ce trajet.

## Calcul de l'itinéraire normal

- **V1 (local, sans fournisseur de cartes)** : un **corridor** autour de la ligne droite départ → destination, élargi selon le mode de transport, avec une durée estimée à partir d'une vitesse moyenne par mode. Ça marche hors ligne et rien n'est envoyé à un tiers.
- **V1.1** : itinéraire routier via un fournisseur (Mapbox ou Google). ⚠ Cela envoie le départ et la destination au fournisseur : il faut le déclarer dans la politique de confidentialité et proposer une option « sans fournisseur ». ❓ Choix du fournisseur.
- Le corridor peut être appris localement à partir des trajets récurrents (domicile → travail), si l'utilisateur l'accepte.

## Événements détectés

| Événement | Règle de détection | Paramètre par défaut (marche / voiture / bus / taxi / train) |
|---|---|---|
| Arrêt inhabituel | Vitesse < 0,5 m/s depuis plus de X min, hors arrêt prévu et hors destination | X = 10 / 8 / 10 / 8 / 15 min |
| Écart au trajet | Distance au corridor > Y m pendant plus de 60 s | Y = 300 / 1 000 / 800 / 1 000 / 2 000 m |
| Direction opposée | Distance à la destination en hausse continue depuis plus de N min après le départ | N = 5 min |
| Retour au départ | Retour dans un rayon de 150 m du départ, après en être sorti de plus de 500 m, sans fin de trajet | — |
| Trajet trop long | Durée > durée prévue × 1,5 **et** > prévue + 15 min | — |
| Perte de signal | Pas de position ni de battement serveur depuis plus de T min | T = 10 min (côté serveur) |
| Zone non sûre | Entrée dans un géofence marqué par l'utilisateur | — |
| Arrivée non confirmée | Heure prévue + marge (15 min) sans « Je suis arrivé » ni arrivée détectée | — |

Les valeurs sont des **points de départ à calibrer** avec des données de terrain (❓ pilote à Port-au-Prince et dans d'autres contextes).

## Anti-faux positifs

| Mécanisme | Effet |
|---|---|
| Tolérance embouteillage | En voiture, bus ou taxi, un arrêt < X min avec des micro-mouvements (accéléromètre) n'est pas un arrêt inhabituel |
| Arrêt prévu | Bouton « Je fais un arrêt » (durée de 5 à 60 min) : suspend la détection d'arrêt, pas les autres |
| Déviation routière | Écart → niveau 1 seulement (vérification), jamais niveau 2 directement |
| Vérification avant alerte | Vibration, notification, PIN ou « Je vais bien » (module 5) |
| Seuils par mode de transport | Voir le tableau ci-dessus ; modifiables dans Réglages > Trajets |
| Précision GPS | Une position avec une précision > 100 m ne déclenche pas d'écart ; elle est seulement journalisée |

## Règles

| ID | Règle |
|---|---|
| R-TRIP-01 | **DOIT** : le suivi de position n'existe que pendant `TRIP_ACTIVE`, `ARMED_FULL` (basse fréquence) et les états d'alerte. |
| R-TRIP-02 | **DOIT** : la position en arrière-plan est obtenue via un service de premier plan de type `location`, démarré par l'utilisateur. Pas d'`ACCESS_BACKGROUND_LOCATION` en V1 (ADR-0006). |
| R-TRIP-03 | **DOIT** : fréquence GPS de 5 à 30 s selon le mode : 5 s en `EMERGENCY_MODE`, 10 s en trajet actif, 30 s en `ARMED_FULL`. Adaptée à la batterie (< 15 % → ×2). |
| R-TRIP-04 | **DOIT** : un événement de trajet produit au maximum le niveau 1, sauf « perte de contact » (niveau 2, envoyé par le serveur, car le téléphone ne peut plus répondre). |
| R-TRIP-05 | **DOIT** : la perte de contact côté serveur n'est activée que si l'utilisateur a choisi « Prévenir mes proches si mon téléphone ne répond plus ». Le battement envoie seulement : identifiant du trajet, horodatage, dernière position, batterie. |
| R-TRIP-06 | **DOIT** : à la fin du trajet, les positions détaillées sont supprimées du serveur dans l'heure. Sur le téléphone, la rétention de l'historique suit le réglage de l'utilisateur (défaut : résumé du trajet sans trace de points, 30 jours). |
| R-TRIP-07 | **DOIT** : les zones non sûres sont stockées **uniquement sur le téléphone**, chiffrées. |

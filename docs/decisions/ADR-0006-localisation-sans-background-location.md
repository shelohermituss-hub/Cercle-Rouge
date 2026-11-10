# ADR-0006 — Localisation sans ACCESS_BACKGROUND_LOCATION

- Statut : **Acceptée** · Date : 2026-11-10

## Contexte
Le suivi de trajet doit continuer écran éteint. `ACCESS_BACKGROUND_LOCATION` impose une déclaration Play, une justification forte et un parcours de réglages pénible.

## Décision
Le suivi se fait **uniquement** dans le service de premier plan (type `location`), démarré par une action de l'utilisateur (armer, démarrer un trajet). Le géofencing est calculé localement. Pas d'API Geofencing de Google.

## Conséquences
+ Pas de permission de position en arrière-plan ; un comportement transparent (notification visible).
− Pas de détection d'entrée en zone non sûre quand la protection est désarmée (c'est voulu : P4).

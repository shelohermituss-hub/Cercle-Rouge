# location-engine

## Responsabilité

Position, trajet, géofencing, détection de déviation, dernière position connue. Il produit des `TripEvent` et des `GeoPoint`.

Spécification produit : [module 4](../produit/04-deviation-itineraire.md).

## Hors périmètre

Décider d'une alerte ; envoyer la position (c'est `sync-engine`, sur ordre d'`alert-engine`).

## Interface publique

| Élément | Description |
|---|---|
| `startTracking(profile)` / `stopTracking()` | Profils : EMERGENCY (5 s, haute précision), TRIP (10 s, précision équilibrée), ARMED (30 s, basse consommation) |
| `positions: Flow<GeoPoint>` | Avec la précision et la source (GNSS, réseau, fusion) |
| `lastKnown(): GeoPoint?` | Dernière position valide, chiffrée au repos, conservée 24 h au maximum |
| `startTrip(trip)` / `endTrip()` / `declareStop(duration)` | Gestion du trajet |
| `tripEvents: Flow<TripEvent>` | `PROLONGED_STOP`, `OFF_ROUTE(distance)`, `WRONG_DIRECTION`, `RETURN_TO_ORIGIN`, `OVERDUE`, `UNSAFE_ZONE_ENTERED`, `ARRIVED` |
| `unsafeZones` | CRUD local (cercles ou polygones), chiffré |

## Implémentation

- Fournisseur : Fused Location Provider (Google Play Services) si disponible ; **repli sur `LocationManager`** (GPS + réseau) pour les appareils sans GMS. ❓ Appareils Huawei récents : à confirmer selon le marché.
- Arrière-plan : type de service de premier plan `location`, démarré par l'utilisateur. Pas d'`ACCESS_BACKGROUND_LOCATION` (ADR-0006).
- Géofencing : calculé **localement** à chaque position (point dans un cercle ou un polygone). L'API Geofencing de Google exigerait `ACCESS_BACKGROUND_LOCATION`.
- Algorithme de déviation (V1) : distance point-segment au corridor, filtrée (médiane sur 3 points, positions avec une précision > 100 m ignorées) ; les règles sont décrites dans le module produit 4.

## Règles

| ID | Règle |
|---|---|
| R-LE-01 | **DOIT** : le suivi n'est actif que dans les états qui l'autorisent (R-TRIP-01) ; `stopTracking()` est appelé dans tous les autres. |
| R-LE-02 | **DOIT** : si seule la position approximative est accordée, le suivi fonctionne en mode dégradé et les détections fines (écart < 1 km) sont désactivées, avec une indication à l'interface. |
| R-LE-03 | **DOIT** : aucun géocodage inverse (adresse) via un service tiers sans consentement ; par défaut, les coordonnées et le lien de carte suffisent. |
| R-LE-04 | **DOIT** : l'historique détaillé des points est supprimé à la fin du trajet, sauf s'il est rattaché à une alerte (il devient alors une preuve, soumise à la rétention des preuves). |

## Tests

Trajets GPX rejoués (normal, embouteillage, détour, demi-tour, perte de signal, GPS imprécis en zone urbaine dense) avec les événements attendus. Tests JVM purs sur l'algorithme.

# core-domain

## Responsabilité

Modèle de domaine partagé, **sans dépendance Android** : entités, objets-valeurs, événements, politiques. C'est le langage commun de tous les modules (❓ futur partage avec iOS via Kotlin Multiplatform).

## Hors périmètre

Persistance, réseau, UI, accès capteurs.

## Entités

| Entité | Champs principaux | Invariants |
|---|---|---|
| `User` | `userId`, `displayName`, `locale` (fr, ht, en), `country`, `vulnerabilityProfile` (enfant, ado, adulte, isolé), `emergencyNumbers` | `displayName` non vide ; pays ISO 3166-1 |
| `TrustedContact` | `contactId`, `displayName`, `phoneE164`, `email?`, `group`, `role` (PRIMARY, BACKUP, MEDICAL), `rank`, `channels` (ordonnés), `locale`, `consentStatus`, `anonymousMode` | Au plus 1 PRIMARY ; `rank` unique ; E.164 valide |
| `Trip` | `tripId`, `origin`, `destination`, `transportMode`, `plannedArrival`, `plannedStops`, `followers`, `status` | `plannedArrival` > départ ; destination ≠ origine (> 100 m) |
| `Alert` | `alertId`, `level` (0 à 4), `reason`, `confidence` (LOW, MEDIUM, HIGH), `trigger` (AUTO, SOS, SECRET_PHRASE, TRIP, HEARTBEAT_LOSS), `openedAt`, `closedAt?`, `closure` (USER_SAFE, FALSE_ALARM, EXPIRED…), `silent` | Le niveau ne descend que par une action de l'utilisateur ; `closedAt` ≥ `openedAt` |
| `Evidence` | `evidenceId`, `alertId`, `type` (AUDIO, VIDEO, LOCATION, SENSOR, LOG), `createdAt`, `sha256`, `prevHash`, `retentionUntil`, `uploadState` | `retentionUntil` ≤ `createdAt` + 30 j, sauf conservation manuelle (≤ 90 j) |
| `Event` | `eventId`, `seq`, `utc`, `monotonicNanos`, `type`, `payload` (typé), `prevHash` | `seq` strictement croissant ; ajout uniquement (append-only) |
| `ProtectionState` | `OFF`, `ARMED_AUDIO_ONLY`, `ARMED_FULL`, `TRIP_ACTIVE`, `ALERT_PENDING`, `ALERT_SENT`, `EMERGENCY_MODE` | Voir [01-etats-et-escalade.md](../01-etats-et-escalade.md) |
| `Consent` | `scope` (MICROPHONE, LOCATION, AUDIO_CLIP_SHARING, LIVE_STREAM, AUTHORITIES, MODEL_IMPROVEMENT…), `granted`, `textVersion`, `locale`, `at` | Historisé, jamais modifié en place |

## Objets-valeurs

`GeoPoint(lat, lon, accuracyM, utc, source)`, `RiskScore(value 0..1, components, contributingSignals)`, `AudioSignal(type, probability, windowStart)`, `SensorSignal`, `TripEvent`, `Sensitivity` (LOW, NORMAL, HIGH), `IgnoreContext(type, until)`, `AlertPolicy` (délais, canaux, autorités autorisées, urgence automatique).

## Interfaces (ports) définies ici, implémentées ailleurs

| Port | Implémenté par |
|---|---|
| `Clock` | plateforme |
| `EventLog` | privacy-engine (journal chaîné) |
| `StateStore` | alert-engine (adaptateur Room) |
| `AlertDispatcher` | sync-engine |
| `EvidenceRecorder` | evidence-engine |
| `ContactDirectory` | contacts-engine |
| `ConsentRepository` | privacy-engine |

## Règles

| ID | Règle |
|---|---|
| R-DOM-01 | **DOIT** : objets immuables (`data class` avec `val`) ; validation dans des fabriques qui renvoient un `Result` ou un type d'erreur scellé, pas d'exceptions pour les erreurs métier. |
| R-DOM-02 | **DOIT** : tous les horodatages sont en UTC (`Instant`) ; le fuseau sert uniquement à l'affichage. |
| R-DOM-03 | **DOIT** : identifiants UUIDv4 générés par un générateur aléatoire cryptographique ; aucun identifiant matériel (IMEI, Android ID) n'est utilisé. |
| R-DOM-04 | **DOIT** : les payloads d'événements sont des types scellés (`sealed interface`), sérialisables (kotlinx.serialization) et versionnés. |

## Tests

Tests de propriétés sur les invariants (ex. `rank` unique, `retentionUntil` bornée) ; tests de sérialisation aller-retour pour chaque version d'événement.

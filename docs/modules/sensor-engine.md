# sensor-engine

## Responsabilité

Accéléromètre, gyroscope, batterie, connectivité, détection de mouvement et de chute. Il produit des `SensorSignal` et un `DeviceStatus` (batterie, réseau, GPS activé).

## Hors périmètre

Position (c'est `location-engine`) ; décision (c'est `alert-engine`).

## Interface publique

| Élément | Description |
|---|---|
| `start(mode)` / `stop()` | Modes : LOW (détection de mouvement significatif seulement), NORMAL (50 Hz pendant les fenêtres d'intérêt), HIGH (50 Hz en continu, en alerte) |
| `signals: Flow<SensorSignal>` | `fall_detected`, `violent_motion`, `sudden_stop`, `no_motion(duration)`, `device_dropped`, `phone_thrown` (heuristique) |
| `deviceStatus: StateFlow<DeviceStatus>` | Batterie (%, charge), réseau (aucun, cellulaire, Wi-Fi, validé), GPS activé, mode avion, économie d'énergie |

## Détections (V1 par règles, sans ML)

| Signal | Heuristique initiale (à calibrer) |
|---|---|
| Chute | Chute libre (‖a‖ < 0,3 g pendant > 100 ms) → impact (> 2,5 g) → immobilité pendant > 2 s |
| Mouvement violent | Variance de ‖a‖ > seuil sur 2 s, avec rotations rapides (gyroscope > 5 rad/s) |
| Arrêt brusque | Décélération forte, cohérente avec le GPS si disponible |
| Immobilité | Variance quasi nulle pendant N min (le téléphone est posé ou retiré) |

## Règles

| ID | Règle |
|---|---|
| R-SE-01 | **DOIT** : 50 Hz maximum (`SENSOR_DELAY_GAME` ou période explicite). Pas de capteurs à haute fréquence → aucune permission `HIGH_SAMPLING_RATE_SENSORS`. |
| R-SE-02 | **DOIT** : utiliser le capteur de mouvement significatif ou `TYPE_STEP_DETECTOR` en mode LOW, pour la batterie. |
| R-SE-03 | **DOIT** : les données brutes des capteurs ne sont jamais persistées en continu ; seul un résumé de ±30 s autour d'une détection va aux preuves. |
| R-SE-04 | **DOIT** : un capteur absent sur l'appareil désactive la détection correspondante, sans erreur. |

## Tests

Traces d'accéléromètre enregistrées (marche, course, bus, moto, chute simulée sur un matelas, téléphone lancé) rejouées en tests JVM.

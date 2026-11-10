# alert-engine

## Responsabilité

Le **cerveau** de l'application : machine à états, fusion du risque, anti-faux positifs, escalade, choix des canaux et des destinataires. Il orchestre les autres moteurs par des **effets** explicites.

Références : [01-etats-et-escalade.md](../01-etats-et-escalade.md), [module 5](../produit/05-alerte-graduee.md), [score de risque](../ia/modele-ia-locale.md#score-de-risque).

## Structure

| Partie | Nature | Contenu |
|---|---|---|
| `alert-engine/logic` | Kotlin pur | `reduce(state, event, policy, now) → (newState, effects)`, `RiskFusion`, `EscalationPlanner`, `FalsePositiveGuard` |
| `alert-engine/android` | Adaptateur | `ProtectionService` (service de premier plan), persistance Room, minuteries (coroutines + `AlarmManager` exact pour les délais critiques), exécution des effets |

## Entrées (événements)

`UserIntent` (ARM, DISARM(pin), SOS, I_AM_SAFE(pin), NEED_HELP, START_TRIP, ARRIVED, DECLARE_STOP, FALSE_ALARM(pin)) · `AudioSignal` · `SensorSignal` · `TripEvent` · `TimerElapsed(id)` · `ContactAck(contactId, type)` · `DeliveryReport` · `PermissionChanged` · `ServiceKilledRecovered`.

## Sorties (effets)

`StartEngines(set)` · `StopEngines(set)` · `Vibrate(pattern)` · `ShowNotification(kind)` · `StartTimer(id, delay)` · `RecordEvidence(start/stop, types)` · `Dispatch(alertMessage, recipients, channels)` · `OpenDialer(number)` · `LogEvent(event)` · `PersistState`.

## Anti-faux positifs

| Mécanisme | Paramètre par défaut |
|---|---|
| Deux signaux indépendants (sources différentes, ou classes différentes à faible corrélation) dans une fenêtre glissante | Fenêtre de 10 s |
| Hystérésis : seuil d'entrée ≠ seuil de sortie | Entrée à 0,6, sortie à 0,4 (sensibilité normale) |
| Période réfractaire après une fausse alerte confirmée | 2 min, pour la même classe de signal |
| Contextes à ignorer actifs | Seuils relevés de +0,2 pour les classes concernées |
| Ajustement local appris (opt-in) | Le seuil d'une classe monte de 0,05 au maximum par fausse alerte répétée dans le même lieu ou à la même heure ; plafond à +0,15 ; remise à zéro possible |
| Sensibilité | Faible : +0,1 · Normale : 0 · Élevée : −0,1 sur les seuils |

## Règles

| ID | Règle |
|---|---|
| R-AL-01 | **DOIT** : `reduce` est déterministe et sans effet de bord ; 100 % de ses transitions sont couvertes par des tests. |
| R-AL-02 | **DOIT** : l'état est persisté avant l'exécution des effets (R-SM-01) ; les effets sont rejouables sans double envoi (idempotence via `sync-engine`). |
| R-AL-03 | **DOIT** : les minuteries de vérification et d'escalade survivent à la mise en veille (Doze) : `setExactAndAllowWhileIdle`, ou une coroutine dans le service de premier plan. ⚠ Sur Android 12+, `SCHEDULE_EXACT_ALARM` n'est pas nécessaire tant que le service de premier plan est actif ; ❓ `USE_EXACT_ALARM` est-il acceptable par Play pour une app de sécurité ? |
| R-AL-04 | **DOIT** : aucun effet `OpenDialer(emergency)` sans `UserIntent` ou sans consentement préalable de niveau 4 (R-ESC-02). |
| R-AL-05 | **DOIT** : la décision « silencieuse » (phrase secrète, mode discret) supprime tous les effets visibles et sonores sur le téléphone. |
| R-AL-06 | **DOIT** : chaque décision est inscrite au journal avec les composantes du score (A à E), les seuils utilisés et la version des modèles, pour l'explicabilité et le réglage. |

## Tests

- Tests de table sur `reduce` (état × événement).
- Scénarios de bout en bout simulés en JVM avec une horloge virtuelle : « cri puis choc, sans réponse en 20 s → niveau 2 envoyé », « SOS hors ligne → SMS direct », « fausse alerte → période réfractaire ».
- Tests de propriétés : jamais de niveau 4 sans consentement ; jamais de partage de position en `OFF`.

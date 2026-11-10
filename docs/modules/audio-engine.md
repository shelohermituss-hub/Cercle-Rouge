# audio-engine

## Responsabilité

Capture audio, VAD, classification des sons, détection de mots-clés et de la phrase secrète. Il produit des `AudioSignal` (probabilités) à destination d'`alert-engine`. Il fournit aussi le pré-tampon à `evidence-engine` sur demande.

Spécification produit : [module 3](../produit/03-ecoute-ia-locale.md). Modèles : [ia/modele-ia-locale.md](../ia/modele-ia-locale.md).

## Hors périmètre

Décider d'une alerte (c'est `alert-engine`) ; écrire sur disque (c'est `evidence-engine`) ; réseau.

## Interface publique

| Élément | Description |
|---|---|
| `start(config: AudioConfig)` / `stop()` | Ouverture et fermeture du micro. Appelé uniquement par `ProtectionService` |
| `signals: Flow<AudioSignal>` | `speech_detected`, `scream_probability`, `violence_probability`, `distress_probability`, `keyword_detected(word, confidence)`, `secret_phrase_detected(confidence)`, `silence_after_speech` |
| `status: StateFlow<AudioStatus>` | RUNNING, PAUSED_MIC_BUSY, STOPPED, ERROR(reason) |
| `snapshotPreBuffer(): EncryptedHandle` | Remet les 5 dernières secondes à `evidence-engine`, chiffrées immédiatement |
| `enrollSecretPhrase(samples)` / `deleteSecretPhrase()` | Enrôlement : produit une empreinte (embedding) chiffrée, pas d'audio conservé |

## Pipeline

| Étage | Fréquence | Modèle | Coût visé |
|---|---|---|---|
| Capture | `AudioRecord`, source `VOICE_RECOGNITION` (sans AGC), 16 kHz, mono, PCM 16 bits, blocs de 20 ms | — | — |
| Porte d'énergie + VAD | Chaque bloc | VAD léger (Silero ou WebRTC VAD) | < 1 ms par bloc |
| Classification | Si la porte est ouverte : fenêtres de 0,975 s, pas de 0,5 s | YAMNet (LiteRT, 521 classes) ou modèle distillé | < 30 ms par fenêtre |
| Mots-clés | Si de la parole est détectée | KWS par langue (FR, HT, EN) | < 20 ms par fenêtre |
| Phrase secrète | Si de la parole est détectée | Comparaison de l'empreinte de la fenêtre à l'empreinte enrôlée | < 20 ms |
| Détresse vocale | Si de la parole est détectée, **flag V2** | Classifieur expérimental | — |

## Cycle de vie et contraintes Android

- Tourne dans `ProtectionService`, avec le type de service de premier plan `microphone` (permission `FOREGROUND_SERVICE_MICROPHONE` à partir de l'API 34).
- ⚠ Le service doit être démarré **pendant que l'application est visible** ou suite à une action de l'utilisateur (notification, widget, tuile). Il ne peut pas l'être depuis l'arrière-plan ni au démarrage du téléphone.
- Inférence sur un thread dédié (`Dispatchers.Default` limité à 1). Le délégué GPU ou NNAPI est optionnel ; le CPU (XNNPACK) est la référence.
- Gestion de la concurrence micro : sur Android 10+, une autre application peut prendre le micro → `PAUSED_MIC_BUSY` (callback `AudioRecordingConfiguration`).

## Règles

| ID | Règle |
|---|---|
| R-AE-01 | **DOIT** : tampon circulaire en mémoire, de 10 s au maximum ; aucune écriture disque ; mis à zéro à l'arrêt. |
| R-AE-02 | **DOIT** : les modèles sont embarqués dans l'APK ou l'AAB (pas de téléchargement à l'exécution en V1). Leur hachage est vérifié au chargement. |
| R-AE-03 | **DOIT** : les sorties sont des probabilités calibrées (0 à 1) et la version du modèle est jointe à chaque signal. |
| R-AE-04 | **DOIT** : les seuils et la sensibilité ne sont **pas** appliqués ici, mais dans `alert-engine`. |
| R-AE-05 | **DEVRAIT** : en cas de surchauffe (`PowerManager.getThermalHeadroom`) ou de batterie < 10 %, passer en mode dégradé (VAD + mots-clés seulement) et le signaler à l'interface. |

## Tests

- Jeu de référence versionné de clips étiquetés : cris, vitres, alarmes, musique, foule, tap-tap, marché, pluie. Il inclut des enregistrements en Haïti (❓ collecte avec consentement).
- Métriques par classe : rappel et taux de faux positifs par heure d'audio ambiant. Un seuil de non-régression bloque la CI.
- Test instrumenté : `stop()` libère le micro en < 500 ms.

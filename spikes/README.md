# Prototypes du Sprint 0 (S0.18 à S0.21)

> **Code jetable.** Il sert à répondre à 4 questions sur de vrais téléphones avant d'écrire l'application. Rien ici n'est réutilisé tel quel dans `app/` (voir `docs/plan/sprints-mvp.md`).

Une seule application de test, « CR Prototypes » (`com.cerclerouge.spikes`, distincte de l'app réelle), avec une section par question.

| Étape | Question | Fichier |
|---|---|---|
| S0.18 | Le micro et l'IA tiennent-ils 2 h écran éteint, et à quel coût ? | `MicService.kt` |
| S0.19 | Peut-on enregistrer une preuve audio sans jamais écrire de fichier en clair ? | `EvidenceSpike.kt` |
| S0.20 | Un SMS d'alerte avec accents arrive-t-il vite chez Digicel et Natcom ? | `SmsSpike.kt` |
| S0.21 | Le GPS tient-il pendant un vrai trajet, avec quelle précision et quel coût ? | `GpsService.kt` |

## Construire

```bash
cd spikes
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
```

Prérequis : JDK 17 ou plus, SDK Android (plateforme 36). Versions : AGP 8.13.2, Kotlin 2.2.21, Gradle 8.14.3.

## Modèle d'IA

`app/src/main/assets/yamnet.tflite` : YAMNet (classification de 521 sons, AudioSet), Apache-2.0, téléchargé depuis
`storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/audio_classification/android/lite-model_yamnet_classification_tflite_1.tflite`
(SHA-256 `10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de`). `yamnet_labels.txt` est extrait de ses métadonnées.

## Ce que les prototypes ne font pas

- Aucune donnée ne quitte le téléphone, sauf si tu appuies sur « Partager les résultats » ou si tu envoies le SMS de test.
- S0.18 n'écrit **aucun** audio : seuls des compteurs (inférences, temps, batterie) sont journalisés.
- S0.19 supprime ses enregistrements de test juste après la vérification.
- S0.21 garde les positions dans le journal local du téléphone de test ; efface-les avec « Effacer les mesures ».

Protocole de mesure : [`docs/plan/protocole-prototypes-S0.md`](../docs/plan/protocole-prototypes-S0.md).

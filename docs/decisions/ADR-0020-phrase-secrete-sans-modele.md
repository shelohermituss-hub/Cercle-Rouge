# ADR-0020 — Écoute IA : phrase secrète d'abord, sans modèle entraîné

- Statut : **Acceptée** · Date : 2026-11-11 · Sprint 5 (S5.01 à S5.06, S5.09) · Lié à Q19, Q20

## Contexte
Un détecteur de mots-clés (KWS) en créole demande des enregistrements consentis (Q19 🔴) qui n'existent pas encore. Le porteur du projet choisit de commencer par la **phrase secrète personnelle**, qui n'a besoin d'aucune donnée d'entraînement et marche dans toutes les langues.

## Décisions
| Sujet | Décision |
|---|---|
| Capture | `AudioRecord`, source `VOICE_RECOGNITION`, 16 kHz mono, blocs de 20 ms, fil dédié ; dans `ProtectionService` de type `microphone` |
| Démarrage | Seulement quand la protection est active **et** qu'une phrase est enregistrée (sans détecteur, le micro ne servirait à rien) |
| Mémoire | Tampon circulaire de 6 s dans la VAD (≤ 10 s, R-AE-01), mis à zéro à l'arrêt ; chaque énoncé est écrasé après comparaison. Rien sur disque, rien sur le réseau (R-AUD-01) |
| VAD | Porte d'énergie, plancher de bruit adaptatif (≥ −75 dBFS) ; début à +10 dB pendant 60 ms ; fin à +6 dB du plancher ou −35 dB sous le pic pendant 300 ms ; énoncés de 0,3 à 5 s |
| Empreinte | MFCC 1 à 12 (log-Mel 64 bandes, plage limitée à 50 dB, moyenne retirée) des 3 répétitions ; **chiffrée** dans la base SQLCipher (R-AUD-09). Pas d'audio gardé |
| Comparaison | DTW ; seuil = 2,5 × plus grand écart entre répétitions, borné à [15, 20] ; enrôlement refusé si l'écart dépasse 12 ; détection si 2 répétitions sur 3 sont sous le seuil |
| Effet | `Input.SecretPhrase` → `EMERGENCY_MODE` **silencieux** ; jamais d'appel automatique aux secours (R-AUD-03). Journal : confiance arrondie et version `phrase-dtw-mfcc12-v1` (R-AUD-04, R-AE-03) |
| Micro pris | `AudioRecordingCallback` : état « Écoute IA en pause » affiché, reprise automatique (R-AUD-07) |
| Arrière-plan | Si Android refuse le type `microphone` (service relancé sans écran visible), le service continue sans micro |
| Mots-clés | Liste par défaut (Q20) affichée, **non détectée** : « Anmwe », « Ede m », « Lage m », « Sekou », « Au secours », « À l'aide », « Lâche-moi », à valider par des locuteurs natifs |
| YAMNet, détresse vocale | Non intégrés (S5.07 derrière flag, V2) |

## Limites et risques
- **Seuils fixés sur un banc synthétique** : à calibrer sur de vraies voix et dans le bruit d'Haïti (S5.10, S5.12). La confiance affichée est une ressemblance, pas une probabilité calibrée.
- DTW sur MFCC : sensible au bruit fort et à une prononciation très différente de l'enrôlement ; l'écran propose un test et un réenregistrement.
- L'empreinte (MFCC sans phase) ne permet pas de réécouter la phrase, mais reste une donnée biométrique faible : chiffrée, supprimable, jamais envoyée.

## Conséquences
- Play : type de service `microphone` à déclarer (`conformite/android-permissions-play.md`).
- Les étapes S5.04 (KWS entraîné), S5.10 (banc audio réel) et S5.12 (calibration terrain) attendent la collecte Q19.

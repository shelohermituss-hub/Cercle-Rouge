# Banc d'évaluation audio (S5.10)

Mesure la détection de la phrase secrète et des mots-clés (`audio-engine`, DTW-MFCC) et ses fausses alertes.
Lancé par `./gradlew :audio-engine:test` (classe `AudioEvalBench`) ; rapport : `android/audio-engine/build/reports/audio-eval.md`.

## Ce que le banc mesure
- **Détection** : la phrase enrôlée, dite avec des variations (tempo, hauteur, volume), dans un bruit de 20, 10 et 5 dB de SNR.
- **Fausses acceptations** : d'autres phrases prononcées par la « même voix ».
- **Fausses alertes par heure** sur des ambiances : bruit blanc, grondement de moteur, musique (accords), foule (syllabes
  mêlées), pluie sur tôle (impulsions), et **tous les fichiers `clips/*.wav`**.

## Ajouter des ambiances réelles (👤)
Déposer dans `clips/` des fichiers **WAV, mono, 16 kHz, PCM 16 bits** : tap-tap, marché, rara, pluie sur tôle, église, moto, cris
de jeu d'enfants… Ils sont comptés comme **ambiances négatives** (aucune alerte attendue). Un fichier dont le nom commence par
`positif_` est compté comme contenant la phrase de test.
⚠ Ne jamais déposer d'enregistrement de personnes sans leur accord écrit (Q19, programme de données 2.2) ; pas de voix
identifiable dans le dépôt public.

## Mesure de référence (2026-11-17, détecteur `phrase-dtw-mfcc12-v1`, sans modèle entraîné)
| Mesure | Résultat |
|---|---|
| Détection au calme (20 dB) | 75 % |
| Détection dans le bruit (10 dB, 5 dB) | **0 %** |
| Fausses acceptations (autres phrases) | 0 |
| Fausses alertes sur 5 ambiances synthétiques | 0 / heure |

**Limite connue** : la phrase secrète n'est fiable que dans un environnement calme. Dans la rue, le marché ou un tap-tap, le SOS
(bouton, tuile, widget, Android) reste le moyen sûr. À dire clairement aux utilisateurs. La phase 2 (2.1, 2.3) vise la robustesse
au bruit ; relever alors les seuils.

## Seuils de non-régression (le test échoue en dessous)
- Détection ≥ 70 % à 20 dB de SNR.
- 0 fausse acceptation des autres phrases synthétiques ; 0 alerte sur les ambiances synthétiques.

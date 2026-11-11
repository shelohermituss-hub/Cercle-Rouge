# Suivi du Sprint 5 — Écoute IA locale

Mis à jour le 2026-11-11. Légende : ✅ fait et vérifié · 🟡 fait, non vérifié sur téléphone · ⏳ à faire · ⏸ attend une décision ou des données · 👤 dépend d'une action humaine

## Décisions du sprint (ADR-0020)

- **Phrase secrète d'abord**, sans modèle entraîné (comparaison DTW de MFCC) : marche en créole sans données.
- **Mots-clés** : liste par défaut (Q20) affichée, détection reportée jusqu'à la collecte Q19.

## Étapes

| Étape | Statut | Note |
|---|---|---|
| S5.01 Capture `AudioRecord` dans le service `microphone` | 🟡 | 16 kHz mono, blocs de 20 ms ; démarre avec la protection si une phrase est enregistrée. **Indicateur micro et notification à vérifier sur téléphone** |
| S5.02 Tampon circulaire en mémoire, mis à zéro, arrêt < 500 ms | ✅ (logique) / 🟡 (arrêt) | `RingBuffer` testé (mise à zéro réelle, 10 s max) ; `stop()` attend le fil 400 ms au plus : **à mesurer sur téléphone** |
| S5.03 log-Mel 64 bandes, porte d'énergie + VAD | ✅ | Coût < 1 ms par bloc (mesuré sur la JVM) ; VAD testée (silence, bruit constant, clic, phrase dans le bruit) |
| S5.04 KWS FR + HT entraîné | ⏸ | Attend les enregistrements Q19 (👤) |
| S5.05 Intégration du KWS | ⏸ | Idem |
| S5.06 Phrase secrète : enrôlement 3 répétitions → empreinte chiffrée ; détection → urgence silencieuse | ✅ (banc synthétique) / 🟡 (vraie voix) | 19 tests : même phrase reconnue (tempo ±8 %, hauteur ±3 %, volume, bruit à 20 dB), autres phrases, bruit et son pur refusés. **Seuils à calibrer sur de vraies voix** |
| S5.07 YAMNet derrière un flag | ⏳ | V2 |
| S5.08 Signaux → `RiskFusion` | ⏳ | La phrase secrète passe directement à l'urgence (règle du modèle) ; la fusion attend d'autres signaux |
| S5.09 Interrupteur, sensibilité, contextes, mots-clés perso | 🟡 (partiel) | Écran Écoute IA : état, phrase (enregistrer, tester, supprimer), liste des mots-clés. Sensibilité et contextes : plus tard |
| S5.10 Banc `tools/audio-eval/` (ambiances d'Haïti) | ⏸ | Q19 |
| S5.11 Test « aucun audio ne sort » | 🟡 | Par construction (aucun accès réseau ni fichier dans la chaîne) ; **mesure de 10 min à faire sur téléphone** |
| S5.12 Calibration terrain (20 h) | ⏸ | 👤 |
| S5.13 Mode dégradé, pause micro pris | 🟡 (pause) / ⏳ (dégradé) | Pause « micro pris » branchée ; surchauffe et batterie < 10 % plus tard |

## À vérifier sur un vrai téléphone (👤)

1. Enregistrer une phrase (3 fois), puis la tester : reconnue ; une autre phrase : pas reconnue.
2. Activer la protection, dire la phrase : alerte silencieuse (rien à l'écran), entrée au journal.
3. Pendant un appel téléphonique : « Écoute IA en pause », puis reprise.
4. Une heure de conversation normale protection active : aucune fausse alerte.

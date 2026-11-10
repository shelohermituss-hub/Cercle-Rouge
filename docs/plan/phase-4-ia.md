# Phase 4 — Écoute IA locale (S8 à S10)

> En parallèle depuis S0 : collecte des données audio consenties (S0.5). Sans données de terrain, S10 ne peut pas calibrer les seuils.

---

## S8 — Capture audio, VAD et classification des sons (semaines 17–18)

**Objectif** : en mode armé, l'application détecte localement cris, vitres brisées et alarmes, sans jamais écrire d'audio, et l'affiche dans le journal.

**Prérequis** : S1.1 validé ; S4 (service).

| # | Tâche | Détail | Réf. |
|---|---|---|---|
| S8.1 | Capture | `AudioRecord` (`VOICE_RECOGNITION`, 16 kHz, mono, PCM 16 bits, blocs de 20 ms) dans `ProtectionService` (type `microphone`) ; démarrage uniquement sur action visible ; arrêt < 500 ms | R-AUD-05/06, R-AE |
| S8.2 | Tampon circulaire | 10 s en mémoire, mis à zéro à l'arrêt ; `snapshotPreBuffer` (5 s) | R-AE-01, R-AUD-01 |
| S8.3 | Porte d'énergie + VAD | WebRTC VAD ou Silero (selon S1) ; mesure du coût | modèle 1 |
| S8.4 | Classification | YAMNet int8 LiteRT ; fenêtres de 0,975 s, pas de 0,5 s ; correspondance AudioSet → nos classes (`ia/modele-ia-locale.md`) ; classes inhibitrices (musique, foule) | modèle 2 |
| S8.5 | Chargement sûr | Modèles dans les assets ; vérification du hachage ; version jointe à chaque signal | R-AE-02/03 |
| S8.6 | Concurrence micro et mode dégradé | `PAUSED_MIC_BUSY` ; reprise ; surchauffe et batterie < 10 % → mode dégradé | R-AUD-07, R-AE-05 |
| S8.7 | Banc d'évaluation | `tools/audio-eval/` : jeu de clips étiquetés versionné (hors dépôt si volumineux, ❓ stockage), script de métriques (rappel, faux positifs par heure) ; rejeu dans les tests | tests audio-engine |
| S8.8 | Réglages et journal | Interrupteur d'écoute, sons détectés, sensibilité ; entrée de journal « Détection à 20 h 12 — non confirmée — aucune donnée envoyée » | R-AUD-04, module 3 |
| S8.9 | Test « aucun audio ne sort » | Test instrumenté : trafic réseau et fichiers pendant 10 min d'écoute → aucun octet audio | R-AUD-01, P2 |

**Démo** : écoute active → on joue un cri et une vitre brisée → entrées au journal avec leurs scores ; musique forte → pas de détection.
**Skills** : `/performance-optimization`, `/test-driven-development`, `/source-driven-development` (doc LiteRT actuelle).

---

## S9 — Mots-clés, phrase secrète et fusion du risque (semaines 19–20)

**Objectif** : « au secours » (FR) et la phrase secrète sont reconnus ; le score fusionné déclenche une vérification avec deux signaux indépendants.

**Prérequis** : S8 ; Q1 (formule du score) tranchée ; données FR collectées.

| # | Tâche | Détail | Réf. |
|---|---|---|---|
| S9.1 | KWS FR | Entraînement hors application (`ml/` : Python, notebooks reproductibles) d'un DS-CNN sur la liste FR + données négatives ; export LiteRT int8 ; carte du modèle | modèle 3 |
| S9.2 | Phrase secrète | Enrôlement (3 à 5 répétitions) → empreinte moyenne chiffrée ; similarité cosinus ; test et suppression ; aucun audio conservé | R-AUD-09, modèle 3b |
| S9.3 | Fusion | `RiskFusion` pur : composantes A à E, poids par profil, seuils, sensibilité, hystérésis, deux signaux indépendants, période réfractaire, contextes à ignorer | R-ESC-07, alert-engine anti-FP |
| S9.4 | Décision | `recommended_action` → événements de la machine à états ; phrase secrète → `EMERGENCY_MODE` silencieux ; urgence automatique (opt-in) | R-AL-05, R-AUD-03 |
| S9.5 | Explicabilité | Chaque décision est journalisée avec ses composantes, seuils et versions de modèles | R-AL-06 |
| S9.6 | Contextes à ignorer | Concert, sport, conduite, personnalisé, avec une durée ; jamais appliqués à la phrase secrète ni au SOS | module 3 |

**Tests** : tests de table sur la fusion (combinaisons de signaux → action attendue) ; non-régression du KWS sur le jeu de référence.
**Démo** : dire « au secours » → vérification ; ne pas répondre → alerte niveau 2 ; dire la phrase secrète → alerte silencieuse sans rien à l'écran.
**Skills** : `/test-driven-development`, `/doubt-driven-development` (décisions de seuil).

---

## S10 — Capteurs, calibration de terrain, créole et anglais (semaines 21–22)

**Objectif** : chute et mouvement violent renforcent le score ; les seuils sont calibrés sur des données réelles ; le KWS HT et EN est ajouté si les données suffisent.

**Prérequis** : S9 ; données de terrain.

| # | Tâche | Détail | Réf. |
|---|---|---|---|
| S10.1 | `sensor-engine` | Modes LOW, NORMAL, HIGH ; chute, mouvement violent, arrêt brusque, immobilité ; mouvement significatif en mode LOW | R-SE-01..04 |
| S10.2 | Fusion multimodale | Composante B renforcée par les capteurs ; D par l'immobilité et la perte de réseau ; tests | ia/ score |
| S10.3 | Calibration | Sessions de terrain : 40 h d'écoute ambiante (marché, tap-tap, rue, maison, église, rara) ; ajustement des seuils pour ≤ 1 vérification injustifiée par 8 h | ia/ métriques |
| S10.4 | Équité | Rappel par langue, genre et âge ; écart > 10 points → blocage | ia/ équité |
| S10.5 | KWS HT et EN | Si les données suffisent ; sinon reporté en V2, avec mention dans l'interface | ADR-0004 |
| S10.6 | Apprentissage local des seuils (opt-in) | +0,05 par fausse alerte répétée, plafond +0,15, remise à zéro | alert-engine anti-FP |
| S10.7 | Batterie | Mesure sur la matrice d'appareils ; profils ; cible ≤ 4 %/h | R-AUD-08 |

**Démo** : rapport de calibration (faux positifs par heure, rappel par classe et par langue) ; démonstration sur le terrain.
**Jalon go/no-go IA** : si les cibles ne sont pas atteintes, l'écoute sort en « bêta » désactivée par défaut.
**Skills** : `/performance-optimization`, `/observability-and-instrumentation`, `/doubt-driven-development`.

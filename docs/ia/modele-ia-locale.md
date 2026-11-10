# Modèle d'IA locale

## Principes

1. **Tout sur le téléphone.** Aucun audio d'analyse ne sort (P2).
2. **Probabilités, pas certitudes.** Chaque sortie est une probabilité calibrée, avec la version du modèle (N1).
3. **Les modèles signalent, `alert-engine` décide.** Les seuils, la sensibilité et les contextes sont appliqués dans la fusion, pas dans les modèles.
4. **Règles avant ML pour la fusion** en V1 : explicables, réglables, testables. Un modèle de fusion appris n'arrivera (V2) qu'avec des données réelles consenties.

## Données d'entrée

| Source | Format | Fréquence |
|---|---|---|
| Audio | Mono, 16 kHz, PCM 16 bits → log-Mel-spectrogramme (64 bandes, fenêtre de 25 ms, pas de 10 ms) | Fenêtres de 0,5 à 1 s (0,975 s pour YAMNet), pas de 0,5 s |
| Accéléromètre | x, y, z | 50 Hz (fenêtres d'intérêt) |
| Gyroscope | x, y, z | 50 Hz (si disponible) |
| GPS | lat, lon, précision, vitesse | 5 à 30 s selon le mode |
| Réseau et batterie | État, niveau | Sur changement |
| Contexte | Trajet, mode de transport, heure locale, profil, contextes à ignorer | Sur changement |

## Modèles

| # | Modèle | Rôle | Candidat V1 | Taille et latence visées | Statut |
|---|---|---|---|---|---|
| 1 | VAD | Détecte qu'une personne parle ; ouvre la porte des étages suivants | Silero VAD (ONNX ou conversion LiteRT) ou WebRTC VAD (sans ML) | < 2 Mo · < 1 ms par bloc de 20 ms | V1 |
| 2 | Classification audio | Voix, cri, musique, bruit, choc, vitre, alarme, klaxon, freinage, pleurs | **YAMNet** (AudioSet, 521 classes, entrée 16 kHz, 0,975 s), puis réapprentissage d'une tête sur nos classes | ≈ 4 Mo (int8) · < 30 ms | V1 |
| 3 | Mots-clés (KWS) | « au secours », « aide-moi », « appelle la police » ; « anmwe », « ede m », « rele lapolis » (❓ liste à valider par des locuteurs) ; « help », « call the police » | Petit réseau CNN ou DS-CNN par langue, entraîné sur des données collectées avec consentement ; ❓ Vosk avec grammaire restreinte comme prototype | < 1 Mo par langue · < 20 ms | V1 FR ; HT et EN selon les données |
| 3b | Phrase secrète | Phrase personnelle | Empreinte de mots-clés (KWS few-shot) : moyenne de 3 à 5 enrôlements, similarité cosinus | Partage le modèle 3 | V1 |
| 4 | Détresse vocale | Peur, panique, agression | Classifieur sur des empreintes de parole | — | **V2 expérimental**, jamais déclencheur seul |
| 5 | Fusion multimodale | Score de risque | **Règles pondérées** (ci-dessous) | — | V1 règles ; V2 éventuellement un modèle |

### Correspondance YAMNet → nos classes (point de départ)

| Notre classe | Classes AudioSet (exemples) |
|---|---|
| `scream` | Screaming, Shout, Yell, Children shouting |
| `crying` | Crying, sobbing ; Baby cry, infant cry |
| `glass_break` | Glass, Shatter |
| `impact` | Slap, smack ; Thump, thud ; Bang |
| `alarm` | Alarm, Smoke detector, Car alarm, Siren |
| `horn_brake` | Vehicle horn ; Skidding ; Tire squeal |
| `gunshot` | Gunshot, gunfire (❓ à inclure : contexte haïtien) |
| `music_crowd` (classes d'inhibition) | Music, Singing, Crowd, Cheering, Television |

## Sorties

| Sortie | Type | Produit par |
|---|---|---|
| `speech_detected` | booléen + probabilité | 1 |
| `scream_probability` | 0..1 | 2 |
| `violence_probability` | 0..1 (max de `impact`, `glass_break` et `gunshot`, pondérés) | 2 |
| `distress_probability` | 0..1 | 4 (V2) ; en V1 = `scream` + `crying` |
| `keyword_detected` | {mot, langue, confiance} | 3 |
| `secret_phrase_detected` | confiance | 3b |
| `silence_after_speech` | booléen + durée | 1 + règle |
| `risk_score` | 0..1 | 5 |
| `recommended_action` | NONE, LOG, VERIFY, ALERT_CONTACTS, EMERGENCY | 5 |

## Score de risque

> ❓ **La formule de la spécification d'origine n'a pas été transmise.** Ce qui suit est une **proposition** à valider ; les composantes A à E reprennent la spécification.

### Composantes (toutes normalisées entre 0 et 1)

| Composante | Définition proposée |
|---|---|
| **A** — cri ou détresse vocale | max(`scream`, `distress`, confiance d'un mot-clé) sur les 10 dernières secondes |
| **B** — bruit de violence | max(`violence_probability`) sur les 10 dernières secondes, renforcé si le capteur signale un mouvement violent ou une chute (+0,2, plafonné à 1) |
| **C** — gravité de la déviation | 0 sans trajet ; sinon une fonction croissante de l'écart normalisé par le seuil du mode de transport (ex. 0,5 au seuil, 1 au double), maximum avec l'arrêt prolongé et le retard |
| **D** — perte de réseau ou de mouvement | 0,5 si immobilité anormale ou perte de réseau ; 1 si les deux, ou après une activité vocale forte |
| **E** — contexte de vulnérabilité | Fixé par le profil et le moment : trajet nocturne (22 h – 5 h) + 0,3 ; profil enfant ou personne isolée + 0,3 ; zone non sûre + 0,4 ; plafonné à 1 |

### Formule proposée

```
R_signaux = wA·A + wB·B + wC·C + wD·D        avec wA + wB + wC + wD = 1
R         = min(1, R_signaux · (1 + wE·E))
```

E **amplifie** des signaux existants mais ne crée jamais d'alerte seul : un trajet nocturne sans aucun signal donne R = 0.

### Poids par profil (valeurs initiales à calibrer)

| Profil | wA | wB | wC | wD | wE |
|---|---|---|---|---|---|
| Adulte | 0,40 | 0,30 | 0,20 | 0,10 | 0,25 |
| Adolescent·e | 0,35 | 0,30 | 0,25 | 0,10 | 0,35 |
| Enfant | 0,30 | 0,30 | 0,30 | 0,10 | 0,50 |
| Personne isolée | 0,35 | 0,30 | 0,15 | 0,20 | 0,40 |
| Trajet nocturne | Profil de base, avec wC + 0,05 et wA − 0,05 | | | | |

### Seuils et décision (sensibilité normale)

| R | Action recommandée |
|---|---|
| < 0,30 | NONE |
| 0,30 – 0,60 | LOG (niveau 0) |
| ≥ 0,60 **et** ≥ 2 signaux indépendants | VERIFY (niveau 1) |
| ≥ 0,85 **et** ≥ 2 signaux indépendants **et** urgence automatique activée | ALERT_CONTACTS (niveau 2, sans vérification) |
| Phrase secrète ≥ seuil, ou SOS | EMERGENCY (direct, R ignoré) |

## Anti-faux positifs (rappel)

Deux détections indépendantes minimum · fenêtre de confirmation · vibration et notification avant l'alerte · code de désactivation · historique court · ajustement local des seuils pour les contextes récurrents (opt-in, borné) · **aucun entraînement sur les données personnelles sans consentement explicite** (N7). Détails : [alert-engine](../modules/alert-engine.md#anti-faux-positifs).

## Évaluation et cycle de vie des modèles

| Exigence | Détail |
|---|---|
| Jeux de données | Publics (AudioSet, ESC-50, FSD50K, Speech Commands) + **collecte consentie** : locuteurs FR, HT et EN, bruit ambiant d'Haïti (tap-tap, marché, rara, pluie sur tôle, génératrices). ❓ Partenaire de collecte ; contrat et consentement écrits |
| Métriques | Par classe et par langue : rappel à un taux de faux positifs fixé (cible : ≤ 1 vérification injustifiée par 8 h d'écoute en sensibilité normale) ; latence p95 ; batterie |
| Équité | Résultats ventilés par langue, genre et tranche d'âge des locuteurs ; un écart > 10 points de rappel bloque la mise en production |
| Versionnage | Fichier modèle + carte du modèle (données, métriques, limites) + hachage ; version jointe à chaque signal |
| Mise à jour | Via une mise à jour de l'application en V1 (pas de téléchargement dynamique) |
| Données d'amélioration | Uniquement opt-in, séparé, révocable ; envoi d'**exemples choisis par l'utilisateur** après une alerte (ex. « envoyer ce faux positif pour améliorer »), jamais automatique |
| Limites à afficher | « La détection peut manquer un danger ou se tromper. Le bouton SOS reste le moyen le plus fiable. » |

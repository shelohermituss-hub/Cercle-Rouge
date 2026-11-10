# Module produit 3 — Moteur d'écoute IA locale

**Objectif** : détecter des signaux de détresse **sans envoyer l'audio brut**.

Modules logiciels : `audio-engine` (capture, modèles), `alert-engine` (fusion, décision). Détails techniques : [ia/modele-ia-locale.md](../ia/modele-ia-locale.md).

## Fonctionnement (vue utilisateur)

1. L'utilisateur active le mode protection.
2. L'application capture l'audio via `AudioRecord` (mono, 16 kHz), dans un service de premier plan de type `microphone`.
3. Un premier modèle très léger (VAD et détection d'énergie) tourne en continu.
4. Si un signal potentiel apparaît, les modèles plus lourds analysent une fenêtre courte.
5. `alert-engine` calcule un score de risque en combinant audio, capteurs et trajet.
6. Si le score dépasse le seuil, une confirmation discrète est demandée (niveau 1).
7. Sans réponse, l'application applique la politique d'alerte choisie.

## Types de détection

| Signal | Modèle | Fiabilité attendue en V1 | Statut |
|---|---|---|---|
| Cri | Classification audio (YAMNet : *Screaming*, *Shout*, *Yell*) | Moyenne | V1 |
| Appels à l'aide, mots de détresse | Détection de mots-clés (KWS) | Moyenne ; dépend de la langue | V1 (FR) ; ❓ créole et anglais selon les données disponibles |
| Phrase secrète personnalisée | KWS par enrôlement (3 à 5 répétitions) | À mesurer | V1 |
| Chocs, vitre brisée, alarme | Classification audio | Bonne pour vitre et alarme ; faible pour les chocs | V1 |
| Silence brutal après activité vocale | VAD + règle temporelle | Faible seul ; utile en combinaison | V1 |
| Klaxon, freinage, chute | Classification audio + accéléromètre | Moyenne | V1 |
| Voix tremblante, panique, détresse émotionnelle | Classification de détresse | **Faible, expérimental** | V2 derrière un flag, jamais déclencheur seul |

⚠ La reconnaissance d'émotions dans la voix est peu fiable et sensible aux biais (accent, langue, genre, âge). Elle ne peut **jamais** déclencher une alerte seule et doit être évaluée séparément par langue avant d'être activée.

## Contrôle utilisateur

| Réglage | Valeurs | Défaut |
|---|---|---|
| Écoute IA | activée / désactivée | désactivée |
| Sons détectés | cris, chocs, alarmes, pleurs, mots-clés (cases indépendantes) | cris, alarmes, mots-clés |
| Sensibilité | faible / normale / élevée (modifie les seuils, pas les modèles) | normale |
| Phrase secrète | enrôlement vocal, test, suppression | aucune |
| Mots-clés de détresse | liste par défaut selon la langue + ajouts | liste par défaut |
| Contextes à ignorer | « Je suis en concert », « Je fais du sport », « Je conduis », personnalisé ; avec une durée (1 h, 3 h, jusqu'à demain) | aucun |
| Journal local | consultation et effacement | — |

### Contextes à ignorer (« liste noire »)

Un contexte actif **augmente les seuils** des classes concernées (ex. concert → cris et musique ; sport → cris et chocs ; conduite → klaxon et freinage). Il ne coupe **jamais** la phrase secrète, les mots-clés à confiance élevée ni le SOS. Il expire automatiquement.

## Règles

| ID | Règle |
|---|---|
| R-AUD-01 | **DOIT** : aucun échantillon audio brut n'est écrit sur disque ni envoyé sur le réseau pendant l'analyse. Les tampons sont circulaires, en mémoire, et écrasés. |
| R-AUD-02 | **DOIT** : à partir du niveau 2 seulement, et si l'option est activée, un extrait de ≤ 10 s (pré-tampon de 5 s + 5 s) peut être chiffré et joint aux preuves. |
| R-AUD-03 | **DOIT** : l'IA ne décide jamais seule d'appeler la police : elle déclenche une vérification, sauf si l'urgence automatique a été explicitement choisie (et même alors, pas d'appel automatique aux secours : R-ESC-02). |
| R-AUD-04 | **DOIT** : chaque détection au-dessus du niveau 0 est inscrite au journal local : heure, type, score arrondi, décision, « aucune donnée envoyée » ou liste des destinataires. |
| R-AUD-05 | **DOIT** : quand l'écoute est active, la notification persistante est visible et l'indicateur système du micro (API 31+) est présent. Aucun contournement. |
| R-AUD-06 | **DOIT** : l'écoute s'arrête dans les 500 ms suivant la désactivation (micro libéré, vérifié par test). |
| R-AUD-07 | **DOIT** : si une autre application prend le micro (appel téléphonique, enregistreur), l'état « Écoute IA en pause » est affiché ; l'écoute reprend automatiquement ensuite. |
| R-AUD-08 | **DEVRAIT** : consommation batterie de l'écoute ≤ 4 %/h sur l'appareil de référence (❓ à mesurer sur le terrain). |
| R-AUD-09 | **DOIT** : la phrase secrète est stockée comme empreinte (embedding) chiffrée, jamais comme enregistrement audio. |
| R-AUD-10 | **DOIT** : aucun entraînement ni ajustement de modèle sur les données de l'utilisateur sans consentement explicite et séparé (N7). L'« apprentissage local des contextes récurrents » se limite à ajuster des seuils localement, sans quitter le téléphone. |

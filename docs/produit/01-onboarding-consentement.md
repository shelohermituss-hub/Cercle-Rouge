# Module produit 1 — Onboarding et consentement

**Objectif** : expliquer clairement ce que fait l'application, ce qu'elle écoute, ce qu'elle enregistre et qui reçoit les alertes, **avant** de demander quoi que ce soit.

Modules logiciels : `ui-mobile`, `privacy-engine`, `contacts-engine`.

## Parcours

| # | Écran | Contenu | Obligatoire ? |
|---|---|---|---|
| 1 | Accueil | Mission de sécurité en une phrase, et ce que l'app **ne fait pas** (pas de prédiction, pas d'écoute cachée) | Oui |
| 2 | Écoute IA | « L'IA analyse les sons sur ton téléphone. Elle ne transmet pas ton audio en continu. » Schéma : micro → téléphone → *rien ne sort* sauf alerte | Oui |
| 3 | Niveau de protection | Désactivé · Trajet protégé · Mode discret · Urgence permanente (voir tableau ci-dessous) | Oui (« Désactivé » possible) |
| 4 | Contacts de confiance | Ajout depuis le répertoire ou saisie manuelle ; envoi d'une demande de consentement au contact | Non : peut être fait plus tard, mais rappelé |
| 5 | Canaux d'alerte | Notification · SMS · Appel automatisé · WhatsApp · E-mail, avec pour chacun ce qu'il exige (réseau, permission) | Non |
| 6 | Permissions | Demandes **séparées**, une par besoin, au moment où la fonction est activée (voir ci-dessous) | Non |
| 6b | Conservation des preuves | Choix entre 24 h, 7 j, 30 j et « supprimer à la clôture », **sans présélection** ; exemple concret de chaque option (ADR-0017) | Oui |
| 7 | Confidentialité | Politique lisible, exemples concrets, lien vers la version complète | Oui (lecture, pas d'acceptation globale forcée) |
| 8 | PIN | Création du PIN de désactivation ; biométrie autorisée | Recommandé ; **obligatoire avant le premier trajet protégé** (ADR-0015) |
| 9 | Test | Proposition d'une alerte de test (voir module 7) | Non |

### Niveaux de protection proposés

| Niveau | État cible | Usage | Par défaut ? |
|---|---|---|---|
| Désactivé | `OFF` | SOS manuel seul ; l'app propose « Trajet protégé » au premier trajet | **Oui** (ADR-0015) |
| Trajet protégé | `TRIP_ACTIVE` | Le temps d'un trajet déclaré | — |
| Mode discret | `ARMED_*` + présentation discrète | Rendez-vous, situation tendue | — |
| Urgence permanente | `ARMED_FULL` prolongé | Menace connue (ex. harcèlement) | — |

⚠ « Urgence permanente » coûte de la batterie et maintient une notification persistante. L'écran doit l'afficher (estimation en heures d'autonomie) et proposer un rappel de réévaluation tous les 7 jours.

## Consentements séparés

Chaque permission est demandée **en contexte** (au moment où l'utilisateur active la fonction), précédée d'un écran explicatif. Elle n'est jamais demandée en rafale au premier lancement.

| Permission Android | Demandée quand | Si refusée | Indispensable ? |
|---|---|---|---|
| `RECORD_AUDIO` | Activation de l'écoute IA | Pas d'écoute IA ; SOS, trajet et alertes manuelles restent disponibles | Non |
| `ACCESS_FINE_LOCATION` / `COARSE` | Activation du mode Full ou d'un trajet | Alertes sans position (« position indisponible ») ; pas de détection de déviation | Non |
| `POST_NOTIFICATIONS` (API 33+) | Première activation de la protection | ⚠ Le service de premier plan reste visible dans le gestionnaire de tâches, mais la vérification devient plus difficile → la vibration seule reste possible. Avertissement fort | Fortement recommandée |
| `READ_CONTACTS` | Bouton « Choisir depuis le répertoire » | Saisie manuelle. **Préférer le sélecteur système** (`ACTION_PICK`), qui ne demande aucune permission | Non |
| `SEND_SMS` | Choix du canal « SMS automatique » | SMS pré-rempli (1 tap) ou SMS via le serveur | Non (voir ADR-0005) |
| `CALL_PHONE` | Choix du canal « Appel automatique » | Composeur pré-rempli | Non |
| `CAMERA` | Activation de la vidéo de preuve | Preuves audio et position seulement | Non |
| Stockage | — | **Aucune permission** : stockage interne privé de l'application | — |
| `ACCESS_BACKGROUND_LOCATION` | **Jamais demandée en V1** (ADR-0006) | — | — |

## Règles

| ID | Règle |
|---|---|
| R-ONB-01 | **DOIT** : l'utilisateur peut refuser la localisation et utiliser quand même les alertes sonores et le SOS. |
| R-ONB-02 | **DOIT** : aucune fonctionnalité essentielle (SOS, alerte des proches par au moins un canal) n'est bloquée par le refus d'une permission non indispensable. |
| R-ONB-03 | **DOIT** : chaque consentement est enregistré par `privacy-engine` avec la date, la version du texte affiché, la langue et le choix. Il est révocable depuis Réglages > Confidentialité. |
| R-ONB-04 | **DOIT** : aucune case pré-cochée pour les options qui partagent des données (extrait audio, direct, autorités automatiques, amélioration des modèles). |
| R-ONB-05 | **DOIT** : textes disponibles en français, créole haïtien et anglais, rédigés en langage simple (niveau de lecture visé : fin de primaire). Relecture par un locuteur natif pour le créole. |
| R-ONB-06 | **DOIT** : l'onboarding peut être interrompu et repris. Le SOS manuel est utilisable dès le premier écran. |
| R-ONB-07 | **DEVRAIT** : afficher un résumé final « Voici ce qui est actif / inactif », modifiable. |
| R-ONB-08 | **DOIT** : l'utilisateur indique sa tranche d'âge ; **minimum 12 ans**. Pour les 12–15 ans, un parent ou tuteur confirme son consentement (ADR-0009). L'utilisateur atteste aussi être le détenteur du téléphone protégé (anti-stalkerware, N9). |

## Indicateur d'état permanent

Visible sur l'écran principal, dans la notification persistante et dans le widget :

| Libellé (FR) | Condition |
|---|---|
| « Écoute IA active » | Micro ouvert par le service |
| « Trajet protégé » | `TRIP_ACTIVE` |
| « Mode discret » | Présentation discrète activée |
| « Hors ligne » | Pas de connectivité validée |
| « Protection désactivée » | `OFF` |

## Critères d'acceptation

- Un test d'interface parcourt l'onboarding en refusant **toutes** les permissions et vérifie que le SOS envoie au moins un SMS pré-rempli.
- Un test vérifie qu'aucune permission n'est demandée avant l'écran explicatif correspondant.
- Les textes des trois langues existent pour chaque écran (le contrôle i18n en CI échoue s'il manque une clé).

# Module produit 5 — Système d'alerte graduée

**Objectif** : alerter sans créer de panique inutile.

Modules logiciels : `alert-engine`, `contacts-engine`, `sync-engine`, `backend-api`. Les niveaux et transitions sont définis dans [01-etats-et-escalade.md](../01-etats-et-escalade.md) ; ce document précise le **contenu** et les **canaux**.

## Niveaux (rappel)

| Niveau | Ce que voit l'utilisateur | Ce que voient les proches |
|---|---|---|
| 0 Information | Rien, ou une notification locale discrète ; entrée au journal | Rien |
| 1 Vérification | Vibration codée + notification silencieuse « Es-tu en sécurité ? » [Je vais bien] [J'ai besoin d'aide]. Délai de 10, 20 ou 30 s | Rien |
| 2 Alerte proches | Notification « Tes proches ont été prévenus » (sauf mode discret ou phrase secrète : rien de visible) | Message d'alerte (ci-dessous) |
| 3 Alerte élargie | Idem + enregistrement actif | Appel automatique du contact n° 1, SMS à tous, notifications répétées, direct si autorisé |
| 4 Autorités | Écran « Appeler les secours » avec un résumé à lire | Bouton « Transmettre aux secours » dans le tableau de bord |

### Vibration codée

- Vérification : 3 impulsions courtes, répétées toutes les 5 s.
- Alerte envoyée : 1 impulsion longue.
- Fausse alerte confirmée : 2 impulsions courtes.

Ces motifs sont expliqués à l'onboarding et testables depuis les réglages.

## Contenu d'une alerte

Gabarit (FR, adapté pour HT et EN) :

> **[Prénom] a peut-être besoin d'aide.**
> Raison : Cri de détresse détecté · Confiance : élevée
> Heure : 22 h 14 (heure locale)
> Position : [adresse approximative] — précision 20 m (ou « dernière position connue à 22 h 09 »)
> Suivre : [lien de suivi, valable 4 h]
> [Appeler Prénom] [Appeler les secours]

| Champ | Source | Règle |
|---|---|---|
| Prénom | Profil (nom affiché choisi par l'utilisateur) | Jamais le nom complet par défaut |
| Raison | `alert-engine` | Libellé humain, pas de score brut. Liste fermée : « Cri de détresse détecté », « Bruit de violence détecté », « Mot de détresse prononcé », « Déviation de trajet », « Arrêt prolongé », « Perte de contact », « Arrivée non confirmée », « SOS déclenché » |
| Confiance | Score fusionné | 3 paliers : faible, moyenne, élevée. Le SOS et la phrase secrète affichent « déclenché par Prénom » |
| Position | `location-engine` | Avec sa précision et son âge. Si elle est refusée : « position non partagée » |
| Lien de suivi | `backend-api` | Jeton aléatoire ≥ 128 bits, valable 2 à 6 h (réglable, 4 h par défaut), révocable, sans données dans l'URL |
| Phrase secrète | — | Le message ne révèle **jamais** que la phrase secrète a été utilisée si l'utilisateur a choisi le mode silencieux (le proche voit « SOS déclenché ») |

En SMS : version courte ≤ 160 caractères GSM-7 si possible (les accents imposent l'UCS-2, donc 70 caractères par segment ; prévoir 2 ou 3 segments), avec le lien et des coordonnées brutes (`18.5392,-72.3364`), utilisables sans Internet par le destinataire.

## Canaux

| Canal | Mécanisme | Réseau requis | Automatique ? | Contraintes |
|---|---|---|---|---|
| Notification push | FCM vers l'application « proche », ou lien web | Données (émetteur et récepteur) | Oui | Le proche doit avoir installé l'app ; sinon on bascule sur un autre canal |
| SMS automatique | `SmsManager` depuis le téléphone | Réseau GSM seulement | Oui | ⚠ `SEND_SMS` est soumise à la politique Play SMS/Call Log : exception non garantie (ADR-0005). Plan B : SMS envoyé par le serveur (passerelle SMS) |
| SMS de secours serveur | Passerelle SMS côté backend | Données (émetteur) | Oui | Coût par SMS ; ❓ fournisseur couvrant Digicel et Natcom en Haïti |
| Appel automatique au proche | `CALL_PHONE` + `ACTION_CALL` vers le **numéro du proche** | GSM | Oui (niveau 3) | Le téléphone ne peut pas « dire » un message pendant l'appel : le proche entend l'ambiance. Un appel avec message vocal synthétisé nécessite une téléphonie serveur (❓) |
| Appel automatisé serveur | API de téléphonie (message vocal TTS en FR, HT ou EN) | Données (émetteur) | Oui | Coût ; ❓ fournisseur |
| WhatsApp | Intent de partage vers WhatsApp (message pré-rempli) | Données | ⚠ **Non** : WhatsApp exige un tap de l'utilisateur. L'envoi automatique n'est possible que via la WhatsApp Business Platform côté serveur, avec des modèles de messages approuvés (❓) | — |
| E-mail | Envoi côté serveur | Données (émetteur) | Oui | Faible réactivité ; canal complémentaire seulement |
| Secours (niveau 4) | Composeur avec numéro pré-rempli + écran de résumé | GSM | **Non** (R-ESC-02) | — |

## Règles

| ID | Règle |
|---|---|
| R-ALR-01 | **DOIT** : chaque alerte part par **au moins deux canaux indépendants** quand ils existent (ex. push + SMS), dans l'ordre choisi par l'utilisateur. |
| R-ALR-02 | **DOIT** : chaque message porte l'`alertId`, pour que le proche et le serveur dédupliquent les envois multiples. |
| R-ALR-03 | **DOIT** : les proches qui ont reçu une alerte reçoivent aussi sa clôture (« Prénom va bien », « Fausse alerte », « Alerte terminée »). |
| R-ALR-04 | **DOIT** : l'envoi est **local d'abord**. Le SMS depuis le téléphone ne dépend pas du serveur ; le serveur est un canal supplémentaire, pas un point unique de défaillance. |
| R-ALR-05 | **DOIT** : un « test d'alerte » utilise exactement le même chemin, avec un marqueur « TEST » visible en tête de chaque message. |
| R-ALR-06 | **DOIT** : limitation anti-spam : au maximum 1 alerte réelle par contact toutes les 2 min (hors mises à jour de la même alerte), et au maximum 20 SMS par alerte. |
| R-ALR-07 | **DOIT** : les gabarits de messages sont traduits dans les 3 langues ; chaque proche reçoit la langue qu'il a choisie au consentement. |

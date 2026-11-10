# contacts-engine

## Responsabilité

Contacts de confiance, groupes, rôles, consentement du contact, ordre d'alerte, résolution des **destinataires et canaux** pour une alerte donnée.

Spécification produit : [module 7](../produit/07-contacts-confiance.md).

## Interface publique

| Élément | Description |
|---|---|
| `contacts: Flow<List<TrustedContact>>` | Liste locale chiffrée |
| `add / update / remove / reorder` | CRUD ; `add` déclenche l'invitation (via `sync-engine`) |
| `pickFromSystem()` | Contrat `ActivityResult` sur `ACTION_PICK` (aucune permission) |
| `resolveRecipients(alert, policy, deviceStatus): List<Delivery>` | Destinataires + canal ordonnés selon le niveau, les rôles, les consentements et la connectivité |
| `sendTest(contactId)` | Alerte de test |
| `consentUpdates` | Mise à jour des statuts (via le serveur) |

## Règles de résolution

1. Seuls les contacts `ACCEPTED`, sauf pour le SOS manuel, qui part aussi vers les contacts non encore consentis (ADR-0015).
2. Niveau 2 : répondant principal + backups (3 au maximum, ADR-0017), avec le 1er canal disponible de chacun, plus un 2e canal indépendant (R-ALR-01).
3. Niveau 3 : les 5 contacts (SMS + push) ; appels automatiques en V2 (ADR-0010).
4. Contact médical : reçoit les infos médicales seulement si l'utilisateur les a renseignées **et** autorisées.
5. Sans données : les canaux SMS et appel GSM passent en premier.

## Règles

| ID | Règle |
|---|---|
| R-CE-01 | **DOIT** : `resolveRecipients` est une fonction pure (testée en JVM). |
| R-CE-02 | **DOIT** : le serveur ne reçoit que les contacts de confiance, jamais le répertoire. |
| R-CE-03 | **DOIT** : les numéros sont normalisés en E.164 ; un numéro invalide bloque l'ajout avec un message clair. |

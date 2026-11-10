# backend-api

## Responsabilité

Comptes et appareils, relais des alertes, liens de suivi, canaux serveur (push, SMS, appel automatisé, e-mail, ❓ WhatsApp Business), détection de perte de contact, stockage temporaire des preuves partagées, page web du tableau de bord des proches.

## Hors périmètre

Analyse audio (toujours sur le téléphone) ; décision d'alerte (sauf « perte de contact », qui ne peut venir que du serveur).

## Stack (❓ à confirmer)

| Élément | Proposition | Raison |
|---|---|---|
| Langage | Go **ou** Node.js (TypeScript) | Go : binaire unique, faible mémoire, robuste en concurrence (WebSocket). Node : plus proche de l'équipe si elle vient de Konvwa |
| Base | PostgreSQL (chiffrement des colonnes sensibles au niveau applicatif) | — |
| Cache, file, minuteries | Redis (TTL des liens, battements, files d'envoi) | — |
| Objets | Stockage compatible S3, chiffrement côté client (déjà fait par l'app) + côté serveur | — |
| Temps réel | WebSocket | — |
| Hébergement | ❓ Deux régions ou fournisseurs (R-NET serveur de secours) ; ❓ localisation des données (UE ? Amériques ?) | — |

## Ressources (API REST versionnée `/v1`)

| Ressource | Opérations | Notes |
|---|---|---|
| `/devices` | enregistrer, faire tourner la clé, révoquer | Pas de compte e-mail obligatoire : l'appareil est l'identité (❓ récupération de compte par numéro) |
| `/contacts` | créer (invitation), mettre à jour, supprimer ; `/consent/{token}` pour le contact | — |
| `/alerts` | créer (idempotent), mettre à jour (niveau, position), clôturer | `Idempotency-Key` obligatoire |
| `/alerts/{id}/evidence` | envoi par morceaux reprenables | Données déjà chiffrées par le client |
| `/trips` | démarrer, battement, terminer | Perte de contact = minuterie Redis réarmée à chaque battement |
| `/track/{token}` | page web + WebSocket du proche | Jeton ≥ 128 bits, TTL 2 à 6 h |
| `/track/{token}/actions` | « J'ai pris contact », « Alerte fausse », « Transmettre aux secours » | Limitation de débit |

## Règles

| ID | Règle |
|---|---|
| R-BE-01 | **DOIT** : toutes les écritures sont idempotentes et vérifient que l'appareil est propriétaire de la ressource (autorisation au niveau de la ligne). |
| R-BE-02 | **DOIT** : aucun audio brut d'analyse n'est reçu ; seules des preuves explicitement partagées (niveau ≥ 2) le sont. |
| R-BE-03 | **DOIT** : purge automatique conforme à la rétention (tâche planifiée + test d'intégration). |
| R-BE-04 | **DOIT** : secrets (clés SMS, FCM, téléphonie) dans un gestionnaire de secrets, jamais dans le dépôt ni dans l'application. |
| R-BE-05 | **DOIT** : limitation de débit par appareil, par contact et par IP ; protection contre l'énumération des jetons de suivi. |
| R-BE-06 | **DOIT** : journaux sans contenu personnel (pas de numéro, de position ni de message) ; identifiants pseudonymisés. |
| R-BE-07 | **DOIT** : l'envoi des alertes est surveillé (taux de livraison, latence p95 < 5 s entre la réception et l'envoi), avec une astreinte (❓ qui ?). |
| R-BE-08 | **DOIT** : la perte de contact est envoyée au plus 1 fois par trajet et par palier ; elle est annulée si l'appareil revient. |

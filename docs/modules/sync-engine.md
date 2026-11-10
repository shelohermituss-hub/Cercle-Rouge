# sync-engine

## Responsabilité

Synchronisation sécurisée avec le serveur, file d'attente persistante, envoi sur les canaux locaux (SMS, appel) et serveur, réessais, reprise après panne, basculement entre serveurs.

Spécification produit : [module 8](../produit/08-reseau-hors-ligne.md).

## Interface publique

| Élément | Description |
|---|---|
| `enqueue(job: OutboundJob)` | Types : `AlertMessage`, `AlertUpdate`, `Heartbeat`, `EvidenceChunk`, `ConsentInvite`, `ChainRoot`. Chaque job a une `idempotencyKey` et une priorité |
| `queueState: StateFlow<QueueSummary>` | Pour l'interface (« 3 envois en attente ») |
| `liveSession(alertId)` | Abonnement Supabase Realtime (actions des proches) |
| `onPushReceived(data)` | Point d'entrée FCM : le message ne contient qu'un identifiant ; le contenu est récupéré en HTTPS |

## Canaux

| Canal | Implémentation | Confirmation |
|---|---|---|
| Serveur | `supabase-kt` (RPC, Storage TUS, Functions) sur HTTPS | Réponse de la RPC (identifiant d'alerte = accusé de réception) |
| SMS direct | `SmsManager.sendMultipartTextMessage` avec des `PendingIntent` d'envoi et de remise | Rapport d'envoi ; rapport de remise selon l'opérateur |
| Appel direct | `ACTION_CALL` (proche) / `ACTION_DIAL` (secours) | Aucune (l'interface affiche « appel lancé ») |
| WhatsApp | Intent de partage, **avec interaction utilisateur** | Aucune |

## Ordonnancement

- **Pendant une alerte** : la file est consommée dans `ProtectionService` (pas de différé WorkManager) ; backoff de 2 s à 60 s.
- **Hors alerte** : WorkManager, avec les contraintes réseau et batterie.
- Priorités : `AlertMessage` > `AlertUpdate` > `Heartbeat` > `ChainRoot` > `EvidenceChunk(position)` > audio > vidéo.
- Pas de second serveur (ADR-0011) : après 2 échecs réseau, ou sans accusé de réception en 20 s, on bascule sur le **SMS direct**.

## Règles

| ID | Règle |
|---|---|
| R-SY-01 | **DOIT** : la file est dans Room + SQLCipher ; un job n'est retiré qu'après confirmation. |
| R-SY-02 | **DOIT** : authentification Supabase (OTP téléphone) ; jetons de session stockés chiffrés (Tink) ; renouvellement automatique ; ❓ Play Integrity en V2. |
| R-SY-03 | **DOIT** : les messages FCM ne contiennent aucune donnée personnelle. |
| R-SY-04 | **DOIT** : les médias sont envoyés en morceaux reprenables (protocole tus, ou URL pré-signées par morceau), déjà chiffrés côté client. |
| R-SY-05 | **DOIT** : SMS de secours si le serveur n'a pas accusé réception en 20 s (R-NET-03 pour la déduplication). |

## Tests

Faux serveur (MockWebServer) : coupures, 500, latences, réponses dupliquées ; redémarrage du processus au milieu d'un envoi → pas de doublon ni de perte.

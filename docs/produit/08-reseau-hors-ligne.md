# Module produit 8 — Réseau, hors-ligne et résilience

**Objectif** : que l'alerte fonctionne même si la connexion est mauvaise ou absente.

Modules logiciels : `sync-engine`, `alert-engine`, `evidence-engine`, `backend-api`.

## Principe : local d'abord, serveur en renfort

```
Détection ──► alert-engine ──► File locale persistante (chiffrée)
                                   │
         ┌─────────────────────────┼─────────────────────────┐
         ▼                         ▼                         ▼
   SMS direct (GSM)        Serveur (HTTPS)            Appel direct (GSM)
   sans données            push, SMS serveur,
                           e-mail, lien de suivi
```

L'alerte ne dépend **jamais** du serveur seul. Sans données mobiles, le SMS et l'appel via le réseau GSM restent possibles.

## Fonctionnalités

| Fonction | Comportement |
|---|---|
| File d'attente locale | Chaque envoi est une tâche persistante (base chiffrée) avec un identifiant d'idempotence ; elle survit au redémarrage de l'app et du téléphone |
| Réessai avec backoff | Exponentiel avec jitter : 2 s, 4 s, 8 s… plafonné à 60 s pendant une alerte active (au lieu de 5 min hors alerte) ; pas d'abandon tant que l'alerte est ouverte |
| SMS de secours | Si le serveur n'a pas accusé réception dans les 20 s → SMS direct, si le canal est autorisé |
| Envoi progressif | 1) événement + position (≈ 1 Ko) → 2) métadonnées et trace → 3) audio → 4) vidéo. Chaque étape est indépendante |
| Compression | Audio Opus, vidéo H.264 basse résolution, positions en lot compressé |
| Détection de perte de réseau | `ConnectivityManager.NetworkCallback` + vérification de la capacité « validated » |
| Mode avion | Preuves conservées localement ; la file reprend dès le retour du réseau |
| Reprise automatique | WorkManager avec la contrainte « réseau connecté » pour les envois volumineux ; envoi immédiat (dans le service de premier plan) pour les alertes |
| Serveur de secours | Avec Supabase (ADR-0011) : un seul projet. Le canal indépendant est le **SMS direct depuis le téléphone** ; PITR + sauvegardes ; page de statut |
| Chronologie | Chaque événement porte l'horodatage UTC, l'horloge monotone et un numéro de séquence local, pour reconstituer l'ordre même avec une horloge faussée |

## Règles

| ID | Règle |
|---|---|
| R-NET-01 | **DOIT** : toute alerte est d'abord écrite dans la file locale, puis envoyée ; jamais « envoyée puis enregistrée ». |
| R-NET-02 | **DOIT** : les envois sont idempotents (`Idempotency-Key` = `alertId` + type + séquence). Le serveur déduplique. |
| R-NET-03 | **DOIT** : un SMS de secours n'est pas renvoyé si le serveur confirme ensuite avoir déjà notifié le même contact par SMS (déduplication par `alertId`). |
| R-NET-04 | **DOIT** : la vidéo n'est envoyée en données mobiles que si l'utilisateur l'a autorisé (par défaut : Wi-Fi seulement, sauf en `EMERGENCY_MODE`, où l'audio est autorisé sur réseau mobile). |
| R-NET-05 | **DOIT** : un battement (« heartbeat ») est envoyé toutes les 2 min pendant un trajet actif, si la perte de contact est activée (R-TRIP-05). |
| R-NET-06 | **DEVRAIT** : la file locale se vide en < 10 s après le retour du réseau pour les messages d'alerte (hors médias). |
| R-NET-07 | **DOIT** : TLS 1.2 minimum (1.3 préféré), épinglage des clés publiques des autorités de certification utilisées par Supabase, avec des broches de secours et une date d'expiration (Network Security Config). ❓ Ou pas d'épinglage : le domaine `*.supabase.co` renouvelle ses certificats, donc un épinglage mal fait peut couper toutes les alertes. À décider en S0 (Q28), pas de HTTP en clair. |

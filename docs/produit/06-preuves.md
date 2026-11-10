# Module produit 6 — Enregistrement de preuves

**Objectif** : conserver des éléments utiles **sans surveiller la personne en permanence**.

Modules logiciels : `evidence-engine`, `sync-engine`, `privacy-engine`. Cryptographie : [securite/cryptographie.md](../securite/cryptographie.md).

## Ce qui est enregistré, et quand

| Type | Déclenchement | Durée | Défaut |
|---|---|---|---|
| Audio | Automatique au niveau ≥ 2, ou bouton « Enregistrer » | Segments de 30 s, jusqu'à la clôture (plafond configurable, 60 min par défaut) | Activé à partir du niveau 2 |
| Pré-tampon audio | Les 5 s précédant la détection, gardées en mémoire, jamais sur disque avant ce déclenchement | 5 s | Activé |
| Vidéo courte | Bouton dédié, ou automatique au niveau 3 si l'utilisateur l'a activé | Clips de 15 s, au maximum 2 min | Désactivé |
| Position | À chaque point GPS pendant l'alerte | Toute l'alerte | Activé si la localisation est accordée |
| Capteurs | Résumé (pics d'accélération, chute) autour de la détection | ±30 s | Activé |
| Journal d'événements | Toujours (états, détections, envois) | Selon la rétention | Activé |

⚠ La vidéo exige que l'application soit au premier plan, ou un service de premier plan de type `camera` démarré pendant que l'app est visible (restrictions « while-in-use » d'Android). Pas de capture vidéo cachée.

## Intégrité et horodatage

- Chaque segment est haché (SHA-256) **avant** chiffrement. Les hachages forment une **chaîne** (chaque entrée contient le hachage de la précédente) : supprimer ou modifier un segment devient détectable.
- Horodatage : heure UTC du téléphone + horloge monotone + heure GNSS si disponible.
- Dès que le réseau le permet, la racine de la chaîne est envoyée au serveur, qui la contresigne et peut demander un jeton d'horodatage RFC 3161 à une autorité tierce (❓ fournisseur). Cela prouve que la preuve existait à cette heure.
- ⚠ Cela ne garantit pas la recevabilité en justice, qui dépend du pays et du juge. Ne jamais promettre « preuve légale » dans l'interface.

## Conservation

| Option | Effet |
|---|---|
| **24 heures (défaut)** | Suppression automatique 24 h après la **clôture** de l'alerte |
| 7 jours | — |
| 30 jours | Maximum |
| Suppression à la clôture | Supprimé dès que l'utilisateur clôt l'alerte |
| « Conserver cette preuve » | Action manuelle, par preuve, avec une durée explicite (au maximum 90 jours, renouvelable, avec rappel) : utile pour une plainte |

Jamais de conservation illimitée par défaut (N6).

## Coffre-fort de preuves

- Accès par PIN ou biométrie (`BiometricPrompt`, classe Strong ou identifiants de l'appareil).
- Liste des preuves : date, type, durée, statut d'envoi, date de suppression prévue.
- Actions : écouter ou voir, conserver plus longtemps, supprimer, **exporter chiffré** vers un contact de confiance.
- Partage aux proches : **chiffrement de bout en bout vers l'application** du proche (ADR-0016). Un proche sans l'app ne peut pas écouter.
- Export chiffré : archive chiffrée avec une clé dérivée d'un code à usage unique communiqué séparément (oralement ou par SMS), pour un avocat ou la police.

## Règles

| ID | Règle |
|---|---|
| R-EVD-01 | **DOIT** : aucune donnée de preuve n'est écrite en clair sur le disque, même temporairement. Le chiffrement se fait en flux, pendant l'écriture. |
| R-EVD-02 | **DOIT** : les preuves sont dans le stockage interne privé de l'application, exclues des sauvegardes (`android:allowBackup="false"`, règles d'extraction de données). |
| R-EVD-03 | **DOIT** : la suppression automatique est exécutée par une tâche planifiée (WorkManager) **et** vérifiée à chaque démarrage de l'app (rattrapage). Elle supprime la clé de chiffrement de la preuve (crypto-shredding) puis le fichier. |
| R-EVD-04 | **DOIT** : l'envoi au serveur est progressif : position → métadonnées → audio → vidéo, sur un réseau limité (module 8). |
| R-EVD-05 | **DOIT** : côté serveur, les preuves sont stockées chiffrées, avec la même rétention que celle choisie par l'utilisateur, et supprimées automatiquement. |
| R-EVD-06 | **DOIT** : l'effacement d'urgence (module 9) supprime toutes les clés de preuves en < 2 s. |
| R-EVD-07 | **DEVRAIT** : compression audio Opus 24 kbit/s mono (≈ 180 Ko/min) ; vidéo H.264 en 480p. |
| R-EVD-08 | **DOIT** : l'espace de stockage est vérifié ; si < 200 Mo, la vidéo est désactivée et l'audio est privilégié. |

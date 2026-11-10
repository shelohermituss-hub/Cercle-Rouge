# evidence-engine

## Responsabilité

Enregistrement des preuves (audio, vidéo, position, capteurs, journal), chiffrement en flux, chaîne d'intégrité, rétention et suppression, préparation de l'envoi différé.

Spécification produit : [module 6](../produit/06-preuves.md). Crypto : [securite/cryptographie.md](../securite/cryptographie.md).

## Interface publique

| Élément | Description |
|---|---|
| `startRecording(alertId, types)` / `stopRecording(alertId)` | Démarre ou arrête les enregistreurs ; segments de 30 s pour l'audio, de 15 s pour la vidéo |
| `attachPreBuffer(alertId, handle)` | Ajoute le pré-tampon audio fourni par `audio-engine` |
| `list(): Flow<List<EvidenceSummary>>` | Pour le coffre-fort (après authentification) |
| `open(evidenceId): DecryptedStream` | Lecture, après `BiometricPrompt` ou PIN |
| `extend(evidenceId, until)` / `delete(evidenceId)` | Conservation manuelle ou suppression |
| `exportEncrypted(evidenceIds, recipient)` | Archive chiffrée |
| `purgeExpired()` | Appelé par WorkManager (toutes les heures) et au démarrage de l'app |
| `wipeAll()` | Effacement d'urgence |

## Stockage

- Fichiers dans `filesDir/evidence/<alertId>/<seq>.bin` (stockage interne privé de l'app).
- Format : Tink `StreamingAead` (AES-256-GCM-HKDF, segments de 1 Mo), une **clé de données par alerte**, enveloppée par une clé maîtresse du Keystore (ADR-0007).
- Métadonnées (hachage, `prevHash`, horodatages, état d'envoi, `retentionUntil`) dans Room + SQLCipher.
- Encodage : audio Opus dans un conteneur Ogg (`MediaRecorder` ou `MediaCodec`), écrit **dans le flux chiffrant**, sans fichier temporaire en clair. ⚠ `MediaRecorder` écrit dans un fichier : il faut un descripteur vers un pipe chiffrant, ou un encodage `MediaCodec` manuel. À prototyper en priorité.

## Règles

| ID | Règle |
|---|---|
| R-EE-01 | **DOIT** : aucune donnée en clair sur disque (R-EVD-01), ce que vérifie un test qui inspecte le répertoire pendant un enregistrement. |
| R-EE-02 | **DOIT** : supprimer une preuve = détruire sa clé de données (crypto-shredding), puis supprimer le fichier, puis ses métadonnées, dans cet ordre. |
| R-EE-03 | **DOIT** : la chaîne de hachages est vérifiable hors ligne par un outil fourni (❓ outil de vérification pour les avocats et la police). |
| R-EE-04 | **DOIT** : l'envoi est délégué à `sync-engine` (priorités : position > métadonnées > audio > vidéo). |
| R-EE-05 | **DOIT** : `purgeExpired` est idempotent et tolère une interruption au milieu (reprise sûre). |

## Tests

Chiffrement aller-retour ; détection d'altération (modifier 1 octet → échec) ; purge à l'heure exacte avec une horloge virtuelle ; `wipeAll` < 2 s avec 500 Mo de preuves.

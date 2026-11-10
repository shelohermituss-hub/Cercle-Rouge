# Cryptographie

> Règle : on n'invente rien. On utilise des primitives et des bibliothèques éprouvées (Android Keystore, Google Tink, libsodium ou Signal côté échange), avec leurs paramètres par défaut sûrs.

## Hiérarchie des clés (téléphone)

```
Android Keystore (matériel : TEE, ou StrongBox si disponible), non exportable
 ├── KEK_db      AES-256-GCM   → enveloppe la phrase de passe SQLCipher (Room)
 ├── KEK_evid    AES-256-GCM   → enveloppe les clés de données des preuves (une par alerte)
 ├── KEK_prefs   AES-256-GCM   → enveloppe le keyset Tink des réglages sensibles (DataStore chiffré)
 └── K_device    ECDSA P-256   → signe les racines de chaîne de preuves
```

| Élément | Choix | Raison |
|---|---|---|
| Fichiers de preuves | Tink `StreamingAead` AES256_GCM_HKDF_1MB | Chiffrement en flux de gros fichiers, authentifié |
| Base locale | Room + SQLCipher (AES-256) | Métadonnées, file d'attente, journal |
| Réglages sensibles | Tink `Aead` + DataStore | ⚠ `androidx.security:security-crypto` (EncryptedFile, EncryptedSharedPreferences) est déprécié : ne pas l'utiliser |
| PIN | Argon2id (m = 64 Mo, t = 3, p = 1) ou PBKDF2-HMAC-SHA256 ≥ 310 000 itérations, avec sel de 16 octets ; résultat enveloppé par une clé du Keystore | Résiste à l'extraction hors ligne |
| Accès au coffre | Clé du Keystore avec `setUserAuthenticationRequired(true)` et biométrie forte ou identifiants de l'appareil | Liée à l'authentification |
| Intégrité des preuves | SHA-256 + chaîne de hachages ; racine signée par `K_device` | Non-altération |
| Horodatage tiers | RFC 3161 via le serveur (❓ autorité d'horodatage) | Antériorité |

## Transport

- TLS 1.3 (1.2 minimum) ; épinglage des **clés publiques** (SPKI) du serveur avec au moins 2 broches (primaire + secours), dans la Network Security Config ; `cleartextTrafficPermitted=false`.
- Authentification par JWT Supabase (OTP téléphone) ; les jetons de session sont stockés chiffrés par Tink.

## Partage des preuves avec les proches

| Option | Description | Statut |
|---|---|---|
| A — Chiffrement serveur | L'app envoie les preuves chiffrées avec une clé partagée avec le serveur ; le serveur les déchiffre pour le lien du proche | Simple, mais le serveur voit le contenu |
| B — Clé dans le lien | Clé de lecture dans le **fragment** de l'URL (`#k=…`, jamais envoyé au serveur) ; la page web déchiffre localement (WebCrypto) | **Recommandée en V1** : le serveur ne voit jamais le contenu |
| C — Signal Protocol | Chiffrement de bout en bout entre applications (libsignal) | V2, si les proches utilisent l'app |

❓ Décision à prendre ; la recommandation est **B** (le SMS du lien porte alors la clé : risque assumé et documenté, compensé par la durée limitée).

## Suppression

Crypto-shredding : on détruit la clé de données, puis le fichier. Effacement d'urgence = suppression des clés du Keystore (`deleteEntry`) + des fichiers. Rapide, et irréversible même si le stockage flash conserve des blocs.

## Interdits

- Aucune clé, aucun secret dans le code, les ressources ou `BuildConfig`.
- Pas de mode ECB, pas d'IV réutilisé (Tink les gère), pas de MD5 ni de SHA-1.
- Pas de « crypto maison ».

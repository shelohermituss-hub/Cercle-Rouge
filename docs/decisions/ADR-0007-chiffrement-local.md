# ADR-0007 — Chiffrement local : Android Keystore + Tink + SQLCipher

- Statut : **Acceptée** · Date : 2026-11-10

## Décision
- Clés maîtresses dans l'Android Keystore (StrongBox si disponible), non exportables.
- Fichiers : Tink `StreamingAead` AES-256-GCM-HKDF, une clé de données par alerte (crypto-shredding).
- Base : Room + SQLCipher, phrase de passe enveloppée par le Keystore.
- **Pas** de `androidx.security:security-crypto` (déprécié).

## Amendement (S1, implémentation)
- Les réglages sensibles (PIN haché, conservation, consentements) sont dans la **même base SQLCipher**, et non dans un DataStore chiffré à part : une brique de moins, même protection.
- La phrase de passe SQLCipher (32 octets aléatoires) est chiffrée par un keyset Tink (`AES256_GCM`) enveloppé par une clé du Keystore, puis passée à SQLCipher en **clé brute** (`x'…'`), sans dérivation PBKDF2 inutile à chaque ouverture.
- `K_device` (signature) et `KEK_evid` (preuves) seront créées quand elles serviront (S3 et S6).

Détails : [securite/cryptographie.md](../securite/cryptographie.md).

## Conséquences
+ Suppression rapide et fiable ; résistance au vol de l'appareil.
− Perte des clés en cas de réinitialisation ou de changement d'appareil → les preuves locales sont perdues (c'est voulu) ; celles envoyées au serveur restent accessibles aux proches.

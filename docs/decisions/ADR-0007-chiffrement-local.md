# ADR-0007 — Chiffrement local : Android Keystore + Tink + SQLCipher

- Statut : **Acceptée** · Date : 2026-11-10

## Décision
- Clés maîtresses dans l'Android Keystore (StrongBox si disponible), non exportables.
- Fichiers : Tink `StreamingAead` AES-256-GCM-HKDF, une clé de données par alerte (crypto-shredding).
- Base : Room + SQLCipher, phrase de passe enveloppée par le Keystore.
- **Pas** de `androidx.security:security-crypto` (déprécié).

Détails : [securite/cryptographie.md](../securite/cryptographie.md).

## Conséquences
+ Suppression rapide et fiable ; résistance au vol de l'appareil.
− Perte des clés en cas de réinitialisation ou de changement d'appareil → les preuves locales sont perdues (c'est voulu) ; celles envoyées au serveur restent accessibles aux proches.

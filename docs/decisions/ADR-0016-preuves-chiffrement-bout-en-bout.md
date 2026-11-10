# ADR-0016 — Preuves : chiffrement de bout en bout, app obligatoire pour écouter

- Statut : **Acceptée** · Date : 2026-11-10 (Q12, option C)

## Décision
Les preuves audio ne sont lisibles **que dans l'application Cercle Rouge** des proches destinataires. Ni Supabase ni la page web n'ont jamais la clé.

## Mécanisme (MVP, sans Signal Protocol complet)
1. À l'inscription, chaque application génère une paire de clés de **chiffrement hybride Tink** (ECIES P-256 + HKDF + AES-256-GCM, compatible API 29). La clé privée est dans un keyset Tink enveloppé par une clé du Keystore ; la clé publique est publiée dans `user_public_keys` (signée par `K_device`).
2. Pour chaque alerte, l'app de l'utilisateur chiffre les preuves avec une **clé de données par alerte** (Tink `StreamingAead`, ADR-0007), puis **enveloppe** cette clé pour chaque contact destinataire qui a l'app (`HybridEncrypt` vers sa clé publique). Les enveloppes sont stockées dans `evidence_key_wraps`.
3. L'app du proche télécharge les morceaux chiffrés et son enveloppe, déchiffre localement, puis lit en streaming. Rien n'est conservé en clair ; le cache est chiffré et effacé à la fermeture.
4. Authenticité des clés : confiance au premier usage (TOFU), plus une **empreinte de sécurité** comparable entre les deux téléphones (écran Contact > Vérifier). Un changement de clé est signalé à l'utilisateur.

## Conséquences
+ Une fuite de Supabase ou de la page web n'expose aucun audio.
− Un proche **sans l'application** reçoit l'alerte, la position et le statut (SMS + lien), mais **ne peut pas écouter** l'audio. L'interface le dit : « Installe Cercle Rouge pour écouter les enregistrements ».
− Pas de confidentialité persistante (forward secrecy) dans le MVP ; Signal Protocol reste une piste V2.
− Environ 3 à 4 jours de plus au S6, plus l'enregistrement des clés au S1 et au S3.

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

## Mise en œuvre (étape 1.16, 2026-11-16)
- **Paire de clés** : Tink **HPKE** (DHKEM P-256, HKDF-SHA256, AES-256-GCM) plutôt qu'ECIES, même famille et disponible sur API 29 ; clé privée dans un keyset chiffré par une clé maîtresse du Keystore (`ShareKeys`). Clé publique publiée par `publish_public_key`, **signée par `K_device`** (ECDSA P-256, Keystore), et seulement avec un numéro vérifié (c'est lui qui relie l'app à l'invitation acceptée).
- **Destinataires** : les proches dont le numéro vérifié correspond à une invitation **acceptée** (`evidence_recipients`). L'enveloppe (`share_evidence_key`, table `evidence_key_wraps`) contient l'identifiant local de l'alerte (données associées des segments) et son keyset ; elle ne change plus une fois déposée.
- **Lecture** : écran « Ceux qui m'ont choisi », section « Enregistrements confiés » : téléchargement des objets chiffrés, ouverture de l'enveloppe, déchiffrement et lecture **en mémoire**, `FLAG_SECURE`. Une invitation retirée, une preuve effacée ou échue coupent l'accès (`my_shared_evidence`, règle Storage via `evidence_readable`).
- **Page web** : « Les enregistrements sont chiffrés : installe Cercle Rouge pour les écouter » (alertes de niveau 2 ou plus).
- **Vérifié** : tests JVM (`ShareKeysTest` : le proche lit, un tiers non, enveloppe modifiée refusée), `security.sql`, essai réel sur le projet avec trois comptes jetables supprimés ensuite. L'essai a révélé deux défauts, corrigés par `s6_evidence_share_fix` : `current_phone_hash()` rendait un haché pour un compte sans numéro, et la règle de lecture Storage ne laissait rien passer (RLS d'`evidence_uploads`).
- **Limites connues** : pas encore d'empreinte de sécurité à comparer entre les deux téléphones (TOFU seul). Le CDN de Supabase peut resservir **au même proche**, pendant quelques minutes, une copie qu'il vient déjà de lire, même après effacement ; un tiers ou une nouvelle requête est refusé. Une app réinstallée (nouvelle clé) ne peut pas ouvrir les enveloppes reçues avant. L'ordre et la complétude des segments partagés ne sont pas recontrôlés par la chaîne côté proche (chaque segment est authentifié par AES-GCM et lié à son numéro).

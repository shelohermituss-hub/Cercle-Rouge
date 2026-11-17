# Audit de sécurité du MVP (étape 1.21)

Date : 2026-11-17. Périmètre : application Android (`android/`), serveur Supabase (`supabase/`), pages web (`web/track`, `web/admin`).
Méthode : revue du modèle de menace T1–T14, grille OWASP MASVS v2 niveau L2 (lecture du code et des réglages), tests d'intrusion
automatisés sur le vrai projet, audit des dépendances. **Ce n'est pas un test d'intrusion par un tiers** : il reste à faire avant le
lancement public (voir « À faire »).

## 1. Modèle de menace : état des mesures

| # | Menace | État | Ce qui est en place / ce qui manque |
|---|---|---|---|
| T1 | L'agresseur désarme | 🟡 | PIN ou biométrie pour désarmer ; proches prévenus au désarmement pendant un trajet ; journal chaîné dont la racine part au serveur. **Manque** : PIN de contrainte (V2, 2.9) |
| T2 | Téléphone éteint, app tuée | ✅ | Surveillance serveur de la protection et des trajets (SMS après 10 min de silence), SMS à l'extinction (opt-in), « protection interrompue à HH:MM » (1.17) |
| T3 | Preuves locales supprimées | ✅ | Copie de secours chiffrée segment par segment pendant l'alerte, racine signée et horodatée par le serveur (1.15) |
| T4 | Détournement en stalkerware | ✅ | Armement par le détenteur ; micro et notification toujours visibles ; position seulement en alerte ou trajet ; vue « proche » limitée aux alertes de niveau 2 et aux sessions suivies |
| T5 | Révéler l'usage de l'app | 🟡 | Notifications neutres, `FLAG_SECURE`, mode discret (1.11). **Risque accepté** : nom et icône visibles (mode leurre en V2) |
| T6 | Proche malveillant | ✅ | Liens limités dans le temps et révocables ; « fausse alerte » d'un proche ne clôt rien ; retrait d'invitation = fin d'accès immédiate (preuves partagées comprises, testé) |
| T7 | Lien de suivi fuité | ✅ | Jeton ≥ 128 bits, TTL ≤ 6 h, `noindex`, `no-referrer`, CSP stricte ; jeton inventé refusé (test d'intrusion) |
| T8 | Interception réseau | ✅ | TLS + épinglage SPKI (contrôlé en CI) ; racines et clés signées par `K_device`. Limite connue : le SMS est en clair |
| T9 | Fuite du serveur | ✅ | Preuves chiffrées de bout en bout (le serveur n'a ni la clé d'alerte ni l'audio) ; numéros chiffrés (pgcrypto, clé du coffre) ; rétention 30 j au plus |
| T10 | Abus, spam SMS | 🟡 | Consentement des proches, STOP, plafonds par jour, console « abus ». **Manque** : Play Integrity (à l'ouverture du Play Store) |
| T11 | Initié | ✅ | Console admin : rôle + MFA, agrégats seulement, aucun contenu, aucune position, aucun numéro, aucun nom ; chaque consultation journalisée ; journal d'audit en ajout seul (trigger) |
| T12 | Faux positifs répétés | ✅ | Deux signaux indépendants, hystérésis, période réfractaire, contextes à ignorer (1.1, 1.3) |
| T13 | Faux négatifs | ✅ | Jamais présenté comme une détection garantie ; SOS toujours disponible ; avertissement « ne remplace pas les secours » (1.18) |
| T14 | Téléphone volé hors alerte | 🟡 | Base SQLCipher, clés dans le Keystore, coffre derrière PIN/biométrie, déconnexion à distance impossible. **Manque** : révocation d'un appareil depuis un autre |

## 2. OWASP MASVS v2, niveau L2

| Groupe | Exigence (résumé) | État | Preuve |
|---|---|---|---|
| STORAGE-1 | Données sensibles stockées de façon sûre | ✅ | Room + SQLCipher (clé 256 bits dans le Keystore) ; preuves en flux Tink ; rien en clair sur le disque (test `nothingIsWrittenInClear`) |
| STORAGE-2 | Pas de fuite de données sensibles | ✅ | Sauvegarde Android désactivée (`allowBackup=false`, règles d'extraction) ; journal sans contenu ; `FLAG_SECURE` sur l'urgence, le coffre et les preuves confiées |
| CRYPTO-1 | Cryptographie forte et actuelle | ✅ | Tink (AES-256-GCM-HKDF, HPKE P-256, ECDSA P-256), PBKDF2 210 000 tours pour l'export |
| CRYPTO-2 | Gestion des clés conforme | ✅ | Clés maîtresses non exportables (Keystore) ; une clé par alerte ; crypto-shredding |
| AUTH-1 | Authentification et autorisation sûres | ✅ | Supabase Auth (anonyme puis numéro vérifié) ; RLS sur toutes les tables ; écritures sensibles par RPC `SECURITY DEFINER` |
| AUTH-2 | Authentification locale sûre | ✅ | PIN haché PBKDF2-HMAC-SHA256, 310 000 itérations, sel de 16 octets ; 5 échecs → proches prévenus ; biométrie `BIOMETRIC_STRONG` |
| AUTH-3 | Opérations sensibles protégées | ✅ | Désarmer, coffre, effacement, déconnexion : PIN ou biométrie |
| NETWORK-1 | Trafic chiffré | ✅ | HTTPS seulement (pas de trafic en clair autorisé) |
| NETWORK-2 | Identité du serveur vérifiée | ✅ | Épinglage SPKI avec date d'expiration contrôlée en CI (`tools/check-pins.sh`) |
| PLATFORM-1 | IPC sûr | ✅ | Composants non exportés sauf tuiles (permission système), widget, démarrage ; récepteurs dynamiques `NOT_EXPORTED` |
| PLATFORM-2 | WebView sûre | ✅ | Aucune WebView |
| PLATFORM-3 | Interface sûre | ✅ | `FLAG_SECURE`, notifications publiques neutres |
| CODE-1 | Plateforme à jour | ✅ | `targetSdk` récent, `minSdk 29` |
| CODE-2 | Mécanisme de mise à jour | 🟡 | GitHub Releases ; Play Store à venir |
| CODE-3 | Dépendances sans vulnérabilité connue | 🟡 | Audit OSV automatisé (`tools/osv-check.py`, CI hebdomadaire) : **premier résultat à lire dans GitHub Actions** (l'API OSV est bloquée dans l'environnement de développement) |
| CODE-4 | Entrées validées | ✅ | Validation côté serveur (CHECK, regex des numéros, tailles bornées) en plus de l'app |
| RESILIENCE-1..4 | Anti-altération, anti-débogage, obfuscation | 🟡 | R8 en version publiée ; pas d'anti-débogage ni d'attestation d'intégrité. **Manque** : Play Integrity (T10) |
| PRIVACY-1..4 | Minimisation, transparence, contrôle | ✅ | Position seulement en alerte/trajet ; sauvegarde des contacts opt-in ; export et suppression de compte ; politique de confidentialité publiée |

## 3. Tests d'intrusion automatisés

`supabase/tests/pentest.sh`, lancé sur le vrai projet le 2026-11-17 : **94 vérifications, 0 échec**.

- 17 tables exposées : aucune ligne lisible en anonyme, aucune ligne d'autrui lisible par un compte, aucune écriture directe.
- 32 fonctions RPC : toutes refusées en anonyme ; les 3 fonctions d'administration refusées à un compte ordinaire (`forbidden`).
- Edge Functions planifiées (`trip-watchdog`, `notify-alert`, `evidence-purge`) : 401 sans secret et avec un faux secret.
- Page de suivi (`track`) : jeton inventé refusé, en lecture comme en action.
- Bucket `evidence` : ni liste ni lecture publique.

Compléments déjà en place : `supabase/tests/security.sql` (impersonation de chaque rôle, transaction annulée), essais réels avec
comptes jetables pour les preuves et leur partage (1.15, 1.16), Security Advisor sans alerte hors catégories acceptées.

## 4. À faire avant le lancement public (👤)

1. Test d'intrusion par un tiers indépendant (application + serveur), sur l'environnement de staging.
2. Play Integrity (T10, RESILIENCE) dès la publication sur le Play Store.
3. Lire le premier résultat de l'audit OSV dans GitHub Actions et mettre à jour les dépendances signalées.
4. Révocation d'un appareil à distance (T14) : à décider (V2 ou avant).
5. Créer les comptes administrateurs (e-mail + mot de passe, rôle `admin` posé en base par le propriétaire du projet, MFA TOTP
   enrôlé à la première connexion sur `web/admin`).

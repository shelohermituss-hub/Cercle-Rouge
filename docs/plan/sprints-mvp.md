# Plan du MVP — étape par étape (version 3 : Supabase + consentement parental)

> Chaque étape a un identifiant (`S2.07`), une action (**Faire**) et une preuve que c'est fini (**Vérifier**). On ne passe pas à l'étape suivante tant que le « Vérifier » n'est pas acquis.
> Marquage : ➕ ajouté · ↪ déplacé · V2 reporté.
> Chaque session Claude Code commence par le **prompt maître**, puis le prompt du sprint ([prompts.md](prompts.md)).

## Vue d'ensemble

| Sprint | Thème | Démo de fin | Durée |
|---|---|---|---|
| S0 | Cadrage, comptes, prototypes | Décisions, maquettes testées, Supabase prêt, 4 prototypes mesurés | 3 sem. |
| S1 | Socle Android + socle Supabase | Inscription OTP, onboarding, **consentement parental**, PIN, machine à états | 2 sem. |
| S2 | Alertes locales | **Mode avion : SOS → SMS aux proches avec la position** | 2 sem. |
| S3 | Alertes serveur + page des proches | Notification push, SMS serveur, lien de suivi, « J'ai pris contact » | 2 sem. |
| S4 | Trajet protégé | Détour → vérification → alerte avec carte ; téléphone éteint → perte de contact | 2 sem. |
| S5 | Écoute IA locale | « Anmwe ! » → vérification ; phrase secrète → alerte silencieuse | 2 sem. |
| S6 | Preuves | Audio chiffré après alerte, écouté par le proche, supprimé à 24 h | 2 sem. |
| S7 | Sécurité | Biométrie, leurre, effacement ; audit sans faille haute | 2 sem. |
| S8 | Tests terrain et lancement | 20 à 50 téléphones, bêta fermée, pilote Haïti | 3 sem. |

**≈ 20 semaines** avec 2 développeurs + Claude Code (≈ 7 mois avec 1 seul développeur).

---

# Sprint 0 — Cadrage, comptes et prototypes (3 semaines)

### Semaine 1 — Décisions restantes

| Étape | Faire | Vérifier |
|---|---|---|
| S0.01 | Choisir le **nom définitif** (vérifier la marque à Haïti et la disponibilité sur Play) | Nom inscrit dans un ADR |
| S0.02 | ✅ **Niveau de protection par défaut** (ADR-0015) : recommandé « Désactivé » + SOS toujours visible + proposition « Trajet protégé » au 1er trajet | ADR |
| S0.03 | Contacts et canaux : 10 contacts maximum, 5 alertés à la fois ; push + SMS ; Q6 (SOS vers un contact non consenti) ; Q3c ✅ (adulte référent ajouté par défaut) | `questions-ouvertes.md` à jour |
| S0.04 | Conservation : confirmer 24 h par défaut (7 j, 30 j, à la clôture) ; côté serveur, identique | ✅ déjà décidé, à relire |
| S0.05 | ✅ Partage des preuves (Q12) : de bout en bout, app obligatoire (ADR-0016) | ADR |
| S0.06 | ✅ Épinglage TLS (Q28) → ADR-0014 ; relever la chaîne réelle de `<projet>.supabase.co` après S0.08 | Broches listées |
| S0.07 | Relire et signer ADR-0008 à 0013 (formule, Haïti 12 ans et +, MVP, Supabase, MVVM, consentement parental) | Statut « Acceptée » |

### Semaine 1 et 2 — Comptes, démarches longues

| Étape | Faire | Vérifier |
|---|---|---|
| S0.08 | **Supabase** : créer l'organisation, 2 projets (`cercle-rouge-staging`, `cercle-rouge-prod`) en plan **Pro**, région `us-east-1` ; activer PITR sur la prod ; activer les extensions `pg_cron` et `pgcrypto` | Projets visibles ; pas de mise en pause possible |
| S0.09 | **Fournisseur SMS : Vonage** (décidé) : compte, numéro expéditeur ou alphanumérique autorisé en Haïti ; brancher l'OTP de Supabase Auth dessus | 20 OTP envoyés vers Digicel et Natcom : taux de réception et délai mesurés |
| S0.10 | **Firebase** : projet, FCM activé, compte de service pour l'API HTTP v1 (clé destinée aux secrets Supabase) | Notification de test reçue |
| S0.11 | **Google Play Console** : compte, nom réservé ; dossier d'exception `SEND_SMS` ; déclaration du public cible incluant les moins de 13 ans (« Familles ») | Dossiers prêts ou déposés |
| S0.12 | **Juriste** : enregistrement de tiers, mineurs de 12 ans et +, consentement parental, conditions d'utilisation, réquisitions | Questions envoyées, date de retour |
| S0.13 | **Numéros d'urgence** 114, 115 et 116 confirmés (PNH, Protection civile) | Tableau officiel dans `docs/` |
| S0.14 | **Atelier mots-clés créole** (5 à 10 locuteurs, dont des 12–17 ans) + protocole de collecte audio consentie (formulaire parental pour les mineurs) | `docs/ia/mots-cles.md`, `docs/ia/collecte-donnees.md` |
| S0.15 | **Maquettes Figma** : inscription OTP, âge, consentement parental (côté jeune **et** côté parent), onboarding, accueil (7 états × normal, discret, leurre), SOS, vérification, trajet, contacts, coffre, page des proches | Prototype cliquable |
| S0.16 | Tester les maquettes avec 5 personnes : 2 jeunes de 12 à 17 ans, 1 parent, 2 adultes | Retours intégrés dans Figma |
| S0.17 | Réunir les appareils de test : au moins 4 (entrée de gamme Android 10, Tecno ou Infinix, Samsung A, Pixel récent) | Matrice dans `docs/plan/appareils.md` |

### Semaine 3 — Prototypes de faisabilité (`spikes/`, code jetable)

| Étape | Faire | Vérifier |
|---|---|---|
| S0.18 | Service de premier plan micro + `AudioRecord` 16 kHz + VAD + un modèle LiteRT, écran éteint | ≥ 2 h sans arrêt sur 3 appareils sur 4 ; ≤ 4 %/h ; inférence < 30 ms |
| S0.19 | Audio Opus chiffré en flux (Tink) | Aucun fichier en clair ; relecture OK |
| S0.20 | `SmsManager` multipart avec accents | SMS reçu < 10 s sur Digicel et Natcom |
| S0.21 | GPS en service de premier plan, 1 h de trajet réel | ≤ 3 %/h ; précision médiane < 30 m |
| S0.22 | `supabase-kt` : OTP téléphone + appel d'une RPC + abonnement Realtime depuis un téléphone Android 10 | Connexion OK en 3G ; latence de la RPC < 1 s |

**Livrables S0** : ADR à jour, `docs/plan/rapport-S0.md` (mesures + go/no-go), Figma, comptes prêts. **Go/no-go** : si S0.18 ou S0.20 échouent, on revoit la spécification.

---

# Sprint 1 — Socle Android et socle Supabase (2 semaines)

### Bloc A — Projet et qualité

| Étape | Faire | Vérifier |
|---|---|---|
| S1.01 | Créer le projet : Gradle Kotlin DSL, `libs.versions.toml`, `build-logic/` ; modules `:app`, `:core-domain`, `:alert-engine:logic`, `:privacy-engine` ; `com.cerclerouge.app`, minSdk 29 | `./gradlew assembleDebug` OK |
| S1.02 | ktlint, detekt (`!!` interdit), Lint en erreur ; vérification que `core-domain` n'importe rien d'Android | `./gradlew check` OK |
| S1.03 | CI GitHub Actions : `check`, build AAB, script i18n (FR, HT, EN), gitleaks, audit des dépendances | CI verte en < 10 min |
| S1.04 | Build release : R8, `allowBackup=false`, `dataExtractionRules`, Network Security Config, `Log.*` retirés | APK release inspecté : pas de log, pas de secret |

### Bloc B — Domaine et machine à états (TDD)

| Étape | Faire | Vérifier |
|---|---|---|
| S1.05 | `core-domain` : entités (User avec `ageBand`, TrustedContact, Trip, Alert, Evidence, Event, Consent, **GuardianLink**), objets-valeurs, ports | Tests des invariants |
| S1.06 | ↪ Écrire **d'abord** les tests de la table des transitions (`01-etats-et-escalade.md`) | Tests rouges |
| S1.07 | Implémenter `reduce(state, event, policy, now)` | 100 % des transitions vertes |
| S1.08 | Tests de propriétés : SOS toujours possible ; jamais de position partagée en `OFF` ; jamais de niveau 4 sans consentement ; mineur sans lien confirmé → seulement le SOS manuel (R-PAR-01) | Verts |
| S1.09 | `RiskFusion` pur : `Risk = Σ wᵢ·Xᵢ`, poids par profil, R-RISK-01..03 | Tests de table verts |

### Bloc C — Stockage local chiffré

| Étape | Faire | Vérifier |
|---|---|---|
| S1.10 | Clés Keystore (KEK_db, KEK_evid, KEK_prefs, K_device), StrongBox si disponible | Test instrumenté sur 2 appareils |
| S1.11 | Room + SQLCipher ; DataStore + Tink ; stockage chiffré de la session Supabase | Base illisible sans clé (test) |
| S1.12 | Journal local chaîné (append-only) + écran « Journal » | Rupture de chaîne détectée (test) |
| S1.13 | ↪ PIN : Argon2id, temporisation progressive ; **obligatoire avant le 1er trajet** ; biométrie autorisée (ADR-0015) | Tests ; PIN jamais envoyé au serveur |
| S1.13b | ➕ Paire de clés de partage (Tink hybride), keyset enveloppé par KEK_share, publication signée via `publish_public_key` (ADR-0016) | Clé publique visible côté serveur, clé privée jamais exportée |

### Bloc D — Supabase (socle serveur)

| Étape | Faire | Vérifier |
|---|---|---|
| S1.14 | `supabase/` initialisé (CLI), migration 0001 : `profiles`, `consents`, `audit_logs`, `guardian_links`, helpers `is_admin()`, trigger anti-élévation de rôle, contraintes CHECK | `supabase db reset` OK en local |
| S1.15 | RLS sur chaque table + RPC `request_guardian_consent`, `confirm_guardian_consent`, `revoke_guardian_consent` (`SECURITY DEFINER`, `search_path`, `REVOKE` à `PUBLIC` et `anon`) | — |
| S1.16 | `supabase/tests/security.sql` : impersonation `anon`, jeune, parent, tiers, admin pour chaque table et chaque RPC | Script vert ; Security Advisor sans alerte |
| S1.17 | Edge Function `send-invite` (SMS d'invitation au parent) + Database Webhook | SMS reçu sur un numéro de test |
| S1.18 | CI serveur : migrations + tests SQL + lint et tests Deno des fonctions ; déploiement automatique sur staging | CI verte |

### Bloc E — Application : inscription, onboarding, consentement parental

| Étape | Faire | Vérifier |
|---|---|---|
| S1.19 | Navigation Compose, thème clair et sombre (issu de Figma), `values`/`values-ht`/`values-en`, MVVM + flux unidirectionnel (ADR-0012), Hilt | App dans les 3 langues |
| S1.20 | Inscription par **OTP téléphone** (supabase-kt), +509 par défaut | Connexion sur un vrai numéro Digicel |
| S1.21 | Saisie de la date de naissance (mois et année) → tranche d'âge ; < 12 ans → refus poli + SOS vers le composeur | Tests UI |
| S1.22 | **Parcours jeune 12–15 ans** : saisie du numéro d'un parent ou adulte de confiance + lien déclaré → `request_guardian_consent` → écran d'attente en mode restreint (SOS manuel seulement) [Renvoyer] [Changer] | Démo |
| S1.23 | **Parcours adulte référent** : inscription OTP avec le numéro invité → attestation 18 ans et + → écran « Demande de Prénom » (ce que tu verras / ne verras jamais) → confirmation par PIN ou biométrie → `confirm_guardian_consent` | Le jeune est débloqué en < 5 s (Realtime + push) |
| S1.24 | Retrait du consentement (parent ou jeune) → retour en mode restreint, notification aux deux | Test |
| S1.25 | Onboarding : écrans explicatifs, consentements séparés et historisés (local + table `consents`), permissions en contexte, tout refuser → l'app reste utilisable | Test UI avec tout refusé |
| S1.26 | Écran d'accueil (états, statut), bouton SOS (appui long, annulation de 5 s → `EMERGENCY_MODE` + journal), notifications locales (3 canaux, contenu neutre sur l'écran de verrouillage) | Démo |

**Démo S1** : un jeune de 13 ans s'inscrit → il est en mode restreint → son parent s'inscrit sur un autre téléphone et confirme → le jeune est débloqué ; SOS → l'état d'urgence est inscrit au journal.
**Skills** : `/spec-driven-development`, `/test-driven-development`, `/security-and-hardening`, `/frontend-ui-engineering`, `/ui-ux-pro-max`, `/ci-cd-and-automation`.

---

# Sprint 2 — Alertes locales, hors ligne (2 semaines)

| Étape | Faire | Vérifier |
|---|---|---|
| S2.01 | `contacts-engine` : sélecteur système (aucune permission), E.164 (+509), groupes, rôles (1 répondant principal), langue, canaux | Tests ; numéro invalide refusé |
| S2.02a | ➕ SOS manuel aussi vers les contacts **non encore consentis**, avec mention et lien STOP (ADR-0015) ; alertes automatiques réservées aux consentis | Tests de table |
| S2.02 | Ordre d'alerte : glisser-déposer ; `resolveRecipients` pur (niveau 2 : principal + backups ≤ 5 ; deux canaux indépendants ; GSM d'abord si pas de données) | Tests de table |
| S2.03 | Adulte référent ajouté par défaut comme contact (Q3c) ; retrait silencieux, sans retrait du consentement (R-PAR-07) | Test : aucune notification envoyée à l'adulte |
| S2.04 | `ProtectionService` unique : service de premier plan, notification persistante [SOS] [Désactiver] ; état persisté **avant** les effets ; restauration après la mort du processus ; « Réarmer » après un redémarrage | Survit 30 min écran éteint ; restauration testée |
| S2.05 | Exécuteur d'effets : vibrations codées, notifications, minuteries robustes en veille (`setExactAndAllowWhileIdle` + coroutine) | Vérification déclenchée à l'heure en mode Doze (test adb) |
| S2.06 | ↪ File d'attente hors ligne : jobs Room persistants, idempotents, priorités, backoff de 2 à 60 s pendant une alerte | Mort du processus pendant un envoi → ni doublon ni perte |
| S2.07 | Canal **SMS direct** : `SmsManager` multipart, rapports d'envoi et de remise ; SMS pré-rempli si `SEND_SMS` est refusée | SMS reçu ; rapport visible |
| S2.08 | Gabarits de messages FR, HT et EN (version SMS courte + coordonnées brutes) ; langue du destinataire | Relecture créole |
| S2.09 | Position ponctuelle pour l'alerte (dernière connue + fraîche en 10 s) ; « position non partagée » si refusée | Tests |
| S2.10 | **Vérification** « Es-tu en sécurité ? » (10, 20 ou 30 s) ; [Je vais bien] + PIN ; [J'ai besoin d'aide] | Pas de réponse → niveau 2 envoyé |
| S2.11 | Anti-spam : 1 alerte par contact toutes les 2 min, 20 SMS maximum par alerte | Tests |
| S2.12 | Test d'alerte (« TEST », 3 par jour et par contact) | Démo |
| S2.13 | SOS en 1 geste : widget Glance, tuile de réglages rapides, action dans la notification | Démo écran verrouillé |
| S2.14 | « Appeler les secours » → composeur avec le 114 | Démo |

**Démo S2** : **mode avion** → SOS depuis le widget → le proche reçoit le SMS avec la position en < 15 s.
**Hors sprint** : appel automatisé → V2 ; push et lien de suivi → S3.

---

# Sprint 3 — Alertes via Supabase et page des proches (2 semaines)

| Étape | Faire | Vérifier |
|---|---|---|
| S3.01 | Migration : `devices`, `trusted_contacts` (numéro chiffré + hachage), `contact_invites`, `alerts`, `alert_events`, `alert_positions`, `deliveries`, `tracking_links`, `link_views`, `contact_actions`, `rate_limits` + RLS + CHECK | `security.sql` étendu et vert |
| S3.02 | RPC `invite_contact`, `create_alert` (idempotente ; mineur non confirmé → SOS seulement), `update_alert` (le niveau ne descend pas, `FOR UPDATE`), `close_alert`, `create_tracking_link` | Tests SQL : idempotence, propriétaire, mineur |
| S3.03 | Edge Function `contact-consent` + page web légère : accepter ou refuser, langue, canaux ; STOP | Contact accepté en < 1 min |
| S3.04 | Edge Function `notify-alert` (webhook sur `alerts`) : destinataires consentis → FCM (réveil seulement) + SMS + e-mail ; `deliveries` ; réessais | p95 < 5 s entre l'insertion et l'envoi (mesuré) |
| S3.05 | Enregistrement du jeton FCM (`devices`) ; réception du push → lecture du contenu par RPC | Push reçu, sans données personnelles dans le message |
| S3.06 | `sync-engine` v2 : jobs serveur via supabase-kt ; **SMS direct si pas d'accusé de réception en 20 s** ; déduplication par `alertId` | Coupure du serveur → SMS direct, sans doublon au retour |
| S3.07 | Edge Function `track` (GET statut, positions, chronologie ; POST actions) : jeton haché, expiration de 2 à 6 h, révocation, limites, journal des consultations | Jeton invalide ou expiré → 404 ; tests |
| S3.08 | **Page des proches** (`web/track`) : < 200 Ko, polling toutes les 5 s, [Appeler] [J'ai pris contact] [Alerte fausse] [Secours 114] ; CSP, `noindex`, `no-referrer` ; FR, HT, EN | Lighthouse en 3G : utilisable < 3 s |
| S3.09 | Vue « proche » dans l'application (pour un proche inscrit, ex. le parent) via Realtime | Mise à jour < 2 s |
| S3.10 | « J'ai pris contact » arrête l'escalade ; « Alerte fausse » informe sans clôturer ; clôture par l'utilisateur → message à tous | Tests de bout en bout |
| S3.11 | `pg_cron` : `expire_alerts`, `purge_expired` (liens, positions) | Test avec une date simulée |
| S3.12 | Suppression de compte (`delete_my_account`) + export de mes données | Tout disparaît (vérifié en SQL) |

**Démo S3** : SOS avec données → push + SMS → le proche ouvre le lien, voit la position et clique « J'ai pris contact » ; l'utilisateur le voit.
**Jalon** : bêta interne (10 personnes).

---

# Sprint 4 — Localisation et trajet protégé (2 semaines)

| Étape | Faire | Vérifier |
|---|---|---|
| S4.01 | `location-engine` : profils EMERGENCY (5 s), TRIP (10 s), ARMED (30 s) ; Fused Location + repli `LocationManager` ; précise ou approximative | Tests sur un appareil sans Play Services (si disponible) |
| S4.02 | Service de premier plan type `location` ajouté au `ProtectionService` (sans `ACCESS_BACKGROUND_LOCATION`) | Suivi écran éteint 1 h |
| S4.03 | Algorithmes purs : corridor local, écart, direction opposée, retour au départ, arrêt prolongé, retard, zones non sûres ; seuils par mode | Tests JVM |
| S4.04 | Banc de rejeu GPX (traces de S0.21 + scénarios) | Événements attendus = obtenus |
| S4.05 | Écran Trajet : modèles, destination (favoris locaux), heure prévue, mode (marche, voiture, tap-tap, taxi, moto), contacts qui suivent, « Je fais un arrêt », « Je suis arrivé » (PIN) | Test UI |
| S4.06 | Événement de trajet → vérification (niveau 1 au maximum) → niveau 2 sans réponse | Scénarios |
| S4.07 | Migration `trips` + RPC `start_trip`, `heartbeat`, `end_trip` ; battement toutes les 2 min (opt-in) | Tests SQL |
| S4.08 | `pg_cron` `heartbeat_watchdog()` chaque minute → alerte « perte de contact » via `notify-alert` (une fois par palier, annulée au retour) | Téléphone éteint → alerte en ≤ 11 min |
| S4.09 | Dernier message à l'extinction (`ACTION_SHUTDOWN`) | Test sur appareil |
| S4.10 | Carte dans la page des proches (❓ tuiles OSM ou MapLibre légères) + trace de l'alerte | Utilisable en 3G |
| S4.11 | Fin de trajet : suppression des points (serveur ≤ 1 h, local) ; résumé conservé | Vérifié en SQL |

**Démo S4** : trajet réel → détour de 500 m → vérification → pas de réponse → les proches voient la carte ; téléphone éteint → « perte de contact ».

---

# Sprint 5 — Écoute IA locale (2 semaines)

| Étape | Faire | Vérifier |
|---|---|---|
| S5.01 | `audio-engine` : `AudioRecord` (`VOICE_RECOGNITION`, 16 kHz, mono, blocs de 20 ms) dans le service de type `microphone` ; démarrage uniquement sur action visible | Indicateur micro + notification visibles |
| S5.02 | Tampon circulaire de 10 s en mémoire, mis à zéro à l'arrêt ; arrêt < 500 ms | Test instrumenté |
| S5.03 | Log-Mel 64 bandes ; porte d'énergie + VAD | Coût mesuré < 1 ms par bloc |
| S5.04 | Entraîner le KWS FR + HT dans `ml/` (notebooks reproductibles) → LiteRT int8 + carte du modèle | Rappel et faux positifs mesurés sur le banc |
| S5.05 | Intégrer le KWS (hachage du modèle vérifié, version jointe à chaque signal) | Tests |
| S5.06 | Phrase secrète : enrôlement en 3 à 5 répétitions → empreinte chiffrée ; détection → `EMERGENCY_MODE` silencieux | Démo sans rien de visible à l'écran |
| S5.07 | Classification audio YAMNet **derrière un flag désactivé par défaut** (IA avancée en V2) | Flag désactivé en release |
| S5.08 | Brancher les signaux sur `RiskFusion` (A = mot-clé ; B = 0 sauf chute ; C, D, E) ; deux signaux, hystérésis, période réfractaire | Scénarios |
| S5.09 | Interrupteur (écran, tuile, notification, widget), sensibilité, contextes à ignorer, mots-clés personnalisés | Test UI |
| S5.10 | Banc `tools/audio-eval/` : bruit, musique, conversation, cri simulé, ambiances d'Haïti (tap-tap, marché, rara, pluie sur tôle, église) | Rapport de métriques en CI |
| S5.11 | Test « aucun audio ne sort » (réseau et fichiers pendant 10 min) | 0 octet |
| S5.12 | Calibration terrain : 20 h d'écoute en Haïti ; ajustement des seuils et des poids | ≤ 1 vérification injustifiée par 8 h |
| S5.13 | Mode dégradé (surchauffe, batterie < 10 %) ; pause si le micro est pris par un appel | Tests |

**Jalon** : si la cible de faux positifs n'est pas atteinte → l'écoute sort « bêta », désactivée par défaut.

---

# Sprint 6 — Preuves chiffrées (2 semaines)

| Étape | Faire | Vérifier |
|---|---|---|
| S6.01 | Enregistrement audio **à partir du niveau 2 ou du SOS seulement** : Opus 24 kbit/s, segments de 30 s, pré-tampon de 5 s, plafond de 60 min | Démo |
| S6.02 | Chiffrement AES-256-GCM en flux (Tink `StreamingAead`), une clé par alerte | Aucun fichier en clair (test qui inspecte le répertoire) |
| S6.03 | Chaîne SHA-256, racine signée par `K_device` → RPC `register_chain_root` | Altération d'1 octet détectée |
| S6.04 | Storage : bucket privé `evidence`, politiques RLS sur `storage.objects` (propriétaire ; contacts si partagée), envoi reprenable TUS | Coupure en plein envoi → reprise |
| S6.05 | Priorités d'envoi : position > métadonnées > audio ; Wi-Fi seulement hors urgence | Tests |
| S6.06 | Partage de bout en bout (ADR-0016) : clé de l'alerte enveloppée pour chaque proche qui a l'app (`evidence_key_wraps`) ; lecture déchiffrée dans l'app du proche ; page web : « Installe Cercle Rouge pour écouter » ; écran « Vérifier l'empreinte de sécurité » | Le proche (avec l'app) écoute ; un tiers avec le lien ne peut pas ; le serveur ne voit que des données chiffrées |
| S6.07 | Coffre-fort : biométrie ou PIN, liste, lecture, conserver (≤ 90 j), supprimer, `FLAG_SECURE` | Test UI |
| S6.08 | Rétention 24 h, 7 j, 30 j ou à la clôture : WorkManager toutes les heures + au démarrage ; crypto-shredding ; `purge_expired` sur le Storage | Horloge accélérée → plus rien nulle part |
| S6.09 | Export chiffré + code à usage unique ; outil de vérification de la chaîne | Vérification d'un export OK |
| — | Vidéo | V2 |

---

# Sprint 7 — Sécurité (2 semaines)

| Étape | Faire | Vérifier |
|---|---|---|
| S7.01 | 5 PIN erronés pendant une alerte → message « tentatives de désactivation » aux proches ; désarmement pendant un trajet → notification (PIN de contrainte : V2, ADR-0015) | Démo « agresseur » |
| S7.02 | Biométrie (classe Strong) ; désactivable pour annuler une alerte | Tests |
| S7.03 | Fermeture forcée : détection serveur (S4.08) + « Protection interrompue à HH:MM » au redémarrage | Test |
| S7.04 | Mode leurre (écran neutre), écran de verrouillage neutre, ❓ alias d'icône (Q8) | Captures d'écran vérifiées |
| S7.05 | Effacement d'urgence (geste + PIN) | < 2 s avec 500 Mo |
| S7.06 | Guides système : Urgence SOS d'Android ; liste blanche batterie par fabricant (Tecno, Infinix, Itel, Samsung, Xiaomi) | Testés sur la matrice |
| S7.07 | Console admin minimale (`web/admin`) : santé des envois, abus, support par code ; rôle `admin` ou `support` via `is_admin()` ; aucun accès au contenu ; MFA | Tests RLS admin |
| S7.08 | Revue du modèle de menace T1 à T14 ; OWASP MASVS L2 ; test d'intrusion des Edge Functions et de la RLS (tentatives d'accès entre utilisateurs, entre parent et jeune) | 0 faille haute ou critique |
| S7.09 | Audit des permissions et des SDK (compatibles « Familles ») ; Security Advisor Supabase | Rapport |

---

# Sprint 8 — Tests terrain et lancement (3 semaines)

| Étape | Faire | Vérifier |
|---|---|---|
| S8.01 | Couverture : ≥ 90 % sur la logique ; trous comblés | Rapport |
| S8.02 | Tests d'intégration de bout en bout (SOS, parent, trajet, IA, preuves) sur staging | Verts |
| S8.03 | Campagne **20 à 50 téléphones** : Firebase Test Lab + 20 téléphones physiques à Haïti (Android 10 à 15) ; grille : service tenu, batterie, SMS, GPS, OTP | Grille remplie |
| S8.04 | Réseau faible (2G/EDGE, pertes de paquets), Digicel et Natcom réels | Alertes livrées |
| S8.05 | Mode avion : SOS, vérification, file d'attente, envoi au retour sans doublon | OK |
| S8.06 | Batterie par mode : ≤ 4 %/h (écoute), ≤ 3 %/h (trajet) | Rapport |
| S8.07 | Faux positifs : 40 h d'écoute réelle ; ajustement final des poids | Rapport |
| S8.08 | Accessibilité (TalkBack, police à 200 %) ; relecture native du créole | OK |
| S8.09 | Dossier Play : Sécurité des données, types de service de premier plan + vidéos, `SEND_SMS`, « Familles », fiche honnête | Déposé |
| S8.10 | Juridique : conditions d'utilisation, confidentialité (FR, HT, EN), consentement parental validés | Signé |
| S8.11 | Bêta fermée (50 à 200 personnes), support en 3 langues | Retours triés |
| S8.12 | Lancement pilote : déploiement progressif 5 → 20 → 50 → 100 %, seuils d'arrêt, astreinte, retour arrière | 1.0 publiée |

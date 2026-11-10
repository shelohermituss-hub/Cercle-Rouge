# Sprints du MVP — détail étape par étape

Marquage : **➕** ajouté · **↪** déplacé · **V2** reporté. Chaque tâche cite les exigences de `docs/`.
Chaque sprint commence par le **prompt maître** puis le prompt du sprint ([prompts.md](prompts.md)).

---

## Sprint 0 — Cadrage (3 semaines)

**Objectif** : toutes les décisions sont prises, les maquettes sont validées par de vrais utilisateurs, les risques techniques sont mesurés sur de vrais téléphones.

### Semaine 1 — Décisions

| # | Étape | Statut / Résultat attendu |
|---|---|---|
| 0.1 | Nom, cible, pays, langues | Pays ✅ Haïti · langues ✅ FR, HT, EN · âge ✅ 12 ans et + · **nom définitif** à trancher (« Cercle Rouge » : vérifier la marque et la disponibilité sur Play) |
| 0.2 | Android ou iOS en premier | ✅ Android (ADR-0001) |
| 0.3 | Niveau de protection par défaut | À trancher. **Recommandé** : « Désactivé » + SOS toujours disponible ; l'app propose « Trajet protégé » au premier trajet. Jamais d'écoute active par défaut (P1) |
| 0.4 | Contacts et canaux d'alerte | MVP : push + SMS (direct et serveur). Limites : 10 contacts, 5 alertés à la fois (Q7). SOS vers des contacts non consentis (Q6) |
| 0.5 | Politique de conservation | ✅ 24 h par défaut ; 7 j, 30 j ou à la clôture en option. Rétention serveur (Q10) |
| 0.6 | Choix du backend | **Go** recommandé (binaire unique, WebSocket, faible coût) ou Node.js ; hébergement sur 2 régions ; localisation des données (Q10) → ADR-0011 |
| 0.7 | Définition du MVP | ✅ ADR-0010, à relire et signer |
| 0.8 | ➕ Q3b : consentement du parent ou tuteur pour les 12–15 ans | Mécanisme validé (SMS de confirmation au parent recommandé) |
| 0.9 | ➕ Q12 : partage des preuves avec les proches | Option B recommandée (clé dans le fragment du lien) |

### Semaine 2 — Démarches longues et design

| # | Étape | Résultat |
|---|---|---|
| 0.10 | ➕ Compte Play Console ; demande d'exception `SEND_SMS` (Q11) ; déclaration « Familles » (public de moins de 13 ans) | Dossiers déposés ou prêts |
| 0.11 | ➕ Juriste : enregistrement de tiers, mineurs de 12 ans et +, conditions d'utilisation, réquisitions (Q23–Q26) | Questions envoyées, date de retour |
| 0.12 | ➕ Fournisseurs : passerelle SMS (Digicel et Natcom), Firebase, hébergeur | Comptes de test + coût estimé par alerte |
| 0.13 | ➕ Numéros d'urgence 114, 115 et 116 confirmés auprès de la PNH et de la Protection civile | Tableau officiel |
| 0.14 | ➕ Atelier mots-clés en créole (5 à 10 locuteurs, dont des jeunes de 12 à 17 ans) ; protocole de collecte audio consentie | `docs/ia/mots-cles.md`, `docs/ia/collecte-donnees.md` |
| 0.15 | **Maquettes Figma** : onboarding, accueil (7 états × normal, discret, leurre), SOS, vérification, trajet, contacts, coffre, page proche | Prototype cliquable (`/ui-ux-pro-max`, `/design`, `/brand`) |
| 0.16 | ➕ Tests des maquettes avec 5 utilisateurs cibles (dont 2 de 12 à 17 ans et 1 parent) | Retours intégrés |

### Semaine 3 — ➕ Prototypes de faisabilité (code jetable dans `spikes/`)

| # | Prototype | Mesure de succès |
|---|---|---|
| 0.17 | Service de premier plan micro + `AudioRecord` 16 kHz + VAD + un modèle LiteRT, 2 h écran éteint | ≥ 2 h sans arrêt sur 3 téléphones sur 4 ; ≤ 4 %/h ; inférence < 30 ms sur l'entrée de gamme |
| 0.18 | Audio Opus chiffré en flux (Tink) | Aucun fichier en clair ; relecture OK |
| 0.19 | `SmsManager` multipart avec accents, Digicel et Natcom | Reçu < 10 s, rapports d'envoi reçus |
| 0.20 | GPS en service de premier plan, 1 h de trajet réel (tap-tap, moto) | ≤ 3 %/h ; précision médiane < 30 m |

**Livrables** : ADR 0011 et suivants, `docs/plan/rapport-S0.md` (mesures par téléphone + go/no-go), maquettes Figma, matrice d'appareils (au moins 4 téléphones).
**Skills** : `/interview-me`, `/idea-refine`, `/design`, `/brand`, `/ui-ux-pro-max`, `/source-driven-development`, `/documentation-and-adrs`.

---

## Sprint 1 — Socle Android (2 semaines)

**Objectif** : une application installable, sécurisée dès le départ, avec onboarding, consentements, PIN et une machine à états testée.

| # | Étape | Détail | Réf. |
|---|---|---|---|
| 1.1 | Projet Kotlin + Compose | Modules Gradle (`app`, `core-domain`, `alert-engine:logic`, `privacy-engine`… ajoutés au fil des besoins) ; catalogue de versions ; `com.cerclerouge.app` ; minSdk 29 | ADR-0001/0002 |
| 1.2 | ➕ CI | GitHub Actions : `./gradlew check`, build AAB, i18n, scan des secrets, audit des dépendances | Définition de terminé |
| 1.3 | Navigation | Navigation Compose, une activité unique | — |
| 1.4 | Thème sombre et clair | Design system issu de Figma (couleurs AA, typographie, composants de base) ; jamais la couleur seule | R-UI-01/04 |
| 1.5 | Architecture **MVVM avec flux unidirectionnel** | Décision : MVVM + `StateFlow<UiState>` + intentions (MVI léger). La machine à états du domaine est déjà de type MVI ; un MVI complet dans l'UI ajouterait du code sans bénéfice → ADR-0012 | R-ARCH-03 |
| 1.6 | ↪ **Machine à états** (était au Sprint 4) | `reduce(state, event, policy, now)` pur ; 7 états ; niveaux 0 à 3 ; minuteries en effets ; TDD : table de toutes les transitions + tests de propriétés | R-SM-*, R-ESC-*, R-AL-01 |
| 1.7 | Base locale Room **chiffrée** | ➕ Room + SQLCipher ; clés Android Keystore ; Tink pour les réglages | ADR-0007 |
| 1.8 | ➕ Journal local chaîné | « Détection à 20 h 12 — aucune donnée envoyée » ; append-only | R-PE-03, P7 |
| 1.9 | ↪ **PIN de base** (était au Sprint 7) | Création, vérification (Argon2id), temporisation ; requis pour « Je vais bien » et pour désarmer | R-SM-04, R-PE-02 |
| 1.10 | Gestion des permissions | Écran explicatif avant chaque demande ; état relu à chaque `onResume` ; tout refuser → l'app reste utilisable | R-ONB-01/02, R-PE-01 |
| 1.11 | ➕ Onboarding et consentements | Écrans du module produit 1 ; tranche d'âge (12 ans et +) ; consentement parental pour les 12–15 ans (selon Q3b) ; consentements séparés et historisés | R-ONB-* |
| 1.12 | Écran d'accueil | États, statut (batterie, réseau, contacts), bouton Activer | R-HOME-02 |
| 1.13 | Bouton SOS | Appui long de 1,5 s, compte à rebours d'annulation de 5 s → `EMERGENCY_MODE` ; journal (l'envoi arrive au Sprint 2) | R-HOME-01, R-SM-05 |
| 1.14 | Notifications locales | Canaux : armé, vérification, alerte ; contenu neutre sur l'écran de verrouillage | R-SEC-06 |
| 1.15 | ➕ Langues | `values` (fr), `values-ht`, `values-en` ; contrôle CI des clés manquantes | ADR-0004 |

**Tests** : 100 % des transitions de la machine à états ; Keystore et Room (instrumentés) ; onboarding avec tout refusé.
**Démo** : installation → onboarding en créole → PIN → SOS → l'écran passe en urgence, le journal le montre.
**Skills** : `/spec-driven-development`, `/test-driven-development`, `/frontend-ui-engineering`, `/ui-ux-pro-max`, `/security-and-hardening`, `/ci-cd-and-automation`.

---

## Sprint 2 — Alertes locales (2 semaines)

**Objectif** : sans Internet, un SOS ou une vérification sans réponse prévient les proches par SMS, avec la position.

| # | Étape | Détail | Réf. |
|---|---|---|---|
| 2.1 | Contacts de confiance | Sélecteur système (aucune permission), E.164 (+509 par défaut), groupes, rôles, langue | R-CON-*, R-CE-03 |
| 2.2 | Ordre d'alerte | Glisser-déposer ; répondant principal ; `resolveRecipients` pur et testé | R-CE-01 |
| 2.3 | ➕ `ProtectionService` | Service de premier plan unique ; état persisté avant les effets ; restauration ; « Réarmer » après redémarrage | R-ARCH-04, R-SM-01/08 |
| 2.4 | SMS de secours (direct) | `SmsManager` multipart + rapports ; SMS pré-rempli si `SEND_SMS` est refusée ; gabarits FR, HT et EN avec coordonnées brutes | ADR-0005, module 5 |
| 2.5 | ↪ **File d'attente hors ligne** (était au Sprint 5) | Jobs persistants idempotents, backoff ; alertes prioritaires | R-NET-01/02, R-SY-01 |
| 2.6 | Machine à états d'alerte (suite) | Branchement des effets réels : vibrations codées, notifications, minuteries robustes en veille | R-AL-02/03 |
| 2.7 | Anti-faux positifs (base) | **Vérification** « Es-tu en sécurité ? » (10, 20 ou 30 s) ; « Je vais bien » + PIN ; limites anti-spam | R-ESC-04, R-ALR-06 |
| 2.8 | Test d'alerte | Message « TEST », 3 par jour et par contact | R-ALR-05, R-CON-06 |
| 2.9 | ➕ Position ponctuelle pour le SOS | Dernière position connue + position fraîche (10 s) ; « position non partagée » si refusée | R-LE-01 |
| 2.10 | ➕ Widget, tuile et action SOS dans la notification | SOS en 1 geste partout | R-HOME-01 |
| 2.11 | ➕ Bouton « Appeler les secours » | Composeur pré-rempli avec le 114 | R-HOME-07, R-ESC-02 |
| — | Appel automatisé | **V2** (ADR-0010) | — |
| — | Notifications push, lien de suivi | ↪ Sprint 3 (ils ont besoin du serveur) | — |

**Tests** : scénario JVM « SOS hors ligne → SMS » ; service écran éteint 30 min ; test manuel en **mode avion**.
**Démo** : mode avion → SOS depuis le widget → le proche reçoit le SMS en < 15 s.
**Skills** : `/incremental-implementation`, `/test-driven-development`, `/frontend-ui-engineering`.

---

## Sprint 3 — Backend et tableau de bord des proches (2 semaines) ↪ (était le Sprint 6)

**Objectif** : l'alerte part aussi par le serveur ; le proche consent, reçoit une notification push et suit l'alerte sur une page web.

| # | Étape | Détail | Réf. |
|---|---|---|---|
| 3.1 | Socle serveur | `server/` (Go ou Node selon ADR-0011) ; PostgreSQL, Redis ; migrations ; secrets ; CI ; environnement de staging | R-BE-04 |
| 3.2 | Authentification | Identité de l'appareil (clé du Keystore, requêtes signées) ; pas de mot de passe ; ❓ récupération par numéro de téléphone | R-SY-02 |
| 3.3 | Profils | Prénom affiché, langue, tranche d'âge (rien de plus) | R-DOM |
| 3.4 | Contacts chiffrés | Numéros chiffrés au niveau applicatif ; ➕ invitation et **consentement du contact** ; STOP | R-CON-03/04 |
| 3.5 | Alertes | API idempotente, déduplication avec les SMS directs | R-BE-01, R-NET-02/03 |
| 3.6 | Notifications | ↪ FCM (réveil seulement) + SMS serveur + e-mail ; ➕ SMS de secours si pas d'accusé de réception en 20 s | R-SY-03/05 |
| 3.7 | Liens de suivi expirants | Jeton ≥ 128 bits, 2 à 6 h, révocable | R-DASH-02 |
| 3.8 | WebSocket | Positions et actions en temps réel ; repli en polling | R-DASH-05 |
| 3.9 | ➕ **Tableau de bord minimal des proches** (MVP n° 10) | Page web < 200 Ko : statut, position, chronologie, [Appeler] [J'ai pris contact] [Alerte fausse] [Secours] ; CSP, `noindex` | module 10 |
| 3.10 | Journalisation minimale | Sans contenu personnel ; identifiants pseudonymisés ; 14 j | R-BE-06 |
| 3.11 | Chiffrement au repos | Disque + colonnes sensibles ; sauvegardes chiffrées | R-BE |
| 3.12 | Suppression de compte | Depuis l'app ; effective ≤ 30 j, sauvegardes comprises | vie-privee |
| 3.13 | ➕ Purges de rétention | Tâches planifiées + tests d'intégration | R-BE-03 |
| 3.14 | ➕ Client mobile | HTTPS avec épinglage ; basculement entre serveurs | R-NET-07 |

**Tests** : contrat OpenAPI ; intégration avec une vraie base (conteneur) ; client contre MockWebServer (coupures, doublons).
**Démo** : SOS avec données → push + SMS + lien → le proche voit la position et clique « J'ai pris contact ».
**Jalon** : bêta interne (10 personnes).
**Skills** : `/api-and-interface-design`, `/security-and-hardening`, `/ci-cd-and-automation`, `/observability-and-instrumentation`.

---

## Sprint 4 — Localisation et trajet (2 semaines) ↪ (était le Sprint 3)

**Objectif** : trajet déclaré, détection simple de déviation, vérification puis alerte avec carte.

| # | Étape | Détail | Réf. |
|---|---|---|---|
| 4.1 | Permission de localisation | Précise ou approximative ; mode dégradé ; **sans** `ACCESS_BACKGROUND_LOCATION` | ADR-0006, R-LE-02 |
| 4.2 | Suivi pendant un trajet déclaré | Service de premier plan `location` ; 10 s en trajet, 5 s en urgence ; repli `LocationManager` | R-TRIP-01/03 |
| 4.3 | Calcul d'itinéraire | MVP : **corridor local** (ligne droite élargie selon le transport), sans fournisseur de cartes ; itinéraire routier en V2 (Q14) | module 4 |
| 4.4 | Détection de déviation | Écart > Y m, direction opposée, retour au départ ; filtre de précision | module 4 |
| 4.5 | Arrêt prolongé | Seuils par mode (marche, voiture, bus, taxi, moto) ; « Je fais un arrêt » | module 4 |
| 4.6 | Perte de signal | ➕ Battement toutes les 2 min (opt-in) → alerte « perte de contact » envoyée **par le serveur** ; dernier message à l'extinction | R-TRIP-05, R-BE-08 |
| 4.7 | Minuteur d'arrivée | Heure prévue + 15 min sans « Je suis arrivé » (PIN) → vérification | module 4 |
| 4.8 | Carte de suivi | Dans la page des proches (tuiles légères, ❓ OSM ou MapLibre) | R-DASH-06 |
| 4.9 | Partage temporaire de position | Seulement pendant un trajet suivi ou une alerte ; suppression des points à la fin | R-TRIP-06, R-DASH-01 |
| 4.10 | ➕ Vérification avant toute alerte de trajet | Un événement de trajet = niveau 1 au maximum | R-TRIP-04 |
| 4.11 | ➕ Rejeu GPX | Trajets enregistrés au S0 + scénarios (embouteillage, détour, demi-tour, GPS imprécis) | tests |

**Démo** : trajet réel → détour de 500 m → vérification → pas de réponse → les proches voient la carte ; téléphone éteint → « perte de contact » 10 min après.
**Skills** : `/test-driven-development`, `/performance-optimization`.

---

## Sprint 5 — Écoute IA locale (2 semaines) ↪ (était le Sprint 2)

**Objectif** : des mots-clés de détresse (FR, HT) et la phrase secrète sont détectés **sur le téléphone** ; le score déclenche une vérification.

| # | Étape | Détail | Réf. |
|---|---|---|---|
| 5.1 | Service de premier plan microphone | Type `microphone` ajouté au `ProtectionService` ; démarrage uniquement sur action visible | C1, R-AUD-05 |
| 5.2 | Capture `AudioRecord` | `VOICE_RECOGNITION`, blocs de 20 ms ; tampon circulaire de 10 s en mémoire ; arrêt < 500 ms | R-AE-01, R-AUD-06 |
| 5.3 | Pipeline audio 16 kHz | Mono, PCM 16 bits → log-Mel 64 bandes | ia/ |
| 5.4 | VAD | WebRTC VAD ou Silero (selon le prototype 0.17) | modèle 1 |
| 5.5 | Classification audio TFLite | ⚠ Ta définition du MVP met l'« IA audio avancée » en V2 → le pipeline YAMNet est intégré **derrière un flag désactivé par défaut**, pour mesurer sans risquer de faux positifs | ADR-0010 |
| 5.6 | Détection de mots-clés | KWS FR + HT (liste de l'atelier 0.14), entraîné dans `ml/`, exporté en LiteRT int8 | modèle 3 |
| 5.7 | ➕ Phrase secrète | Enrôlement en 3 à 5 répétitions → empreinte chiffrée → alerte silencieuse | R-AUD-09 |
| 5.8 | Score de risque | `Risk = w₁A + … + w₅E` ; poids par profil ; garde-fous R-RISK-01..03 ; deux signaux indépendants ; hystérésis ; période réfractaire | ADR-0008 |
| 5.9 | Interrupteur utilisateur | Écran, tuile, notification, widget ; sensibilité ; contextes à ignorer | module 3 |
| 5.10 | Indicateur visuel de micro actif | « Écoute IA active » + notification persistante (+ l'indicateur système) | R-AUD-05 |
| 5.11 | Tests : bruit, musique, conversation, cri simulé | ➕ Banc `tools/audio-eval/` avec des clips étiquetés, dont des ambiances haïtiennes (tap-tap, marché, rara, pluie sur tôle) ; métriques : rappel, faux positifs par heure | ia/ |
| 5.12 | ➕ Test « aucun audio ne sort » | Trafic réseau et fichiers pendant 10 min d'écoute → 0 octet audio | R-AUD-01, P2 |
| 5.13 | ➕ Calibration terrain | 20 h d'écoute ambiante en Haïti ; seuils ajustés | ia/ |

**Démo** : « Anmwe ! » → vérification ; phrase secrète → alerte silencieuse ; concert ou musique forte → rien.
**Jalon** : ≤ 1 vérification injustifiée par 8 h, sinon l'écoute sort « bêta », désactivée par défaut.
**Skills** : `/performance-optimization`, `/test-driven-development`, `/doubt-driven-development`, `/source-driven-development`.

---

## Sprint 6 — Preuves (2 semaines)

**Objectif** : après une alerte, l'audio est enregistré chiffré, envoyé dès que possible et supprimé à l'échéance.

| # | Étape | Détail | Réf. |
|---|---|---|---|
| 6.1 | Enregistrement audio | **Seulement** à partir du niveau 2 ou du SOS ; Opus 24 kbit/s ; segments de 30 s ; pré-tampon de 5 s | R-AUD-02, R-EVD-07 |
| 6.2 | Enregistrement vidéo optionnel | **V2** (ADR-0010) | — |
| 6.3 | Chiffrement AES-256-GCM | Tink `StreamingAead` (AES-256-GCM-HKDF), une clé par alerte, jamais de fichier en clair | R-EVD-01, ADR-0007 |
| 6.4 | ➕ Chaîne d'intégrité | SHA-256 chaîné, racine signée et envoyée au serveur | R-EE-03 |
| 6.5 | Coffre-fort local | Biométrie ou PIN ; liste, lecture, conserver, supprimer ; `FLAG_SECURE` | module 6 |
| 6.6 | Rétention configurable | 24 h, 7 j, 30 j, ou à la clôture ; purge toutes les heures + au démarrage ; crypto-shredding ; même règle côté serveur | R-EVD-03, R-EE-02 |
| 6.7 | File d'attente hors ligne | Extension de la file du Sprint 2 aux morceaux audio (priorité : position > métadonnées > audio) | R-EVD-04 |
| 6.8 | Reprise d'envoi | Morceaux reprenables (tus ou URL pré-signées) | R-SY-04 |
| 6.9 | Export sécurisé | Archive chiffrée + code à usage unique transmis séparément | module 6 |
| 6.10 | ➕ Écoute par le proche | Déchiffrement dans le navigateur avec la clé du fragment du lien (option B, Q12) | crypto |

**Tests** : aucun fichier en clair pendant l'enregistrement ; altération détectée ; purge à l'heure exacte (horloge virtuelle) ; reprise après coupure.
**Démo** : SOS → enregistrement → le proche écoute → 24 h plus tard (horloge accélérée), plus rien nulle part.
**Skills** : `/security-and-hardening`, `/test-driven-development`.

---

## Sprint 7 — Sécurité (2 semaines)

**Objectif** : un agresseur ne peut pas arrêter l'alerte en silence ; l'application passe un audit.

| # | Étape | Détail | Réf. |
|---|---|---|---|
| 7.1 | PIN de désactivation | Base faite au S1 → ➕ **PIN de contrainte** (semble désarmer, alerte en silence) ; 5 échecs → message aux proches | R-SEC-02, Q5 |
| 7.2 | Biométrie | `BiometricPrompt` classe Strong ; désactivable pour annuler une alerte (risque de contrainte physique) | module 9 |
| 7.3 | Détection de fermeture forcée | ⚠ Impossible à intercepter localement → détection **par le serveur** (absence de battement) + message à l'extinction + « Protection interrompue » au redémarrage | R-SM-08, C-T2 |
| 7.4 | Mode leurre | Écran neutre ; ❓ alias d'icône (Q8) ; écran de verrouillage neutre | R-SEC-05/06 |
| 7.5 | Journal d'événements | Base faite au S1 → vérification de la chaîne, export, envoi de la racine | R-PE-03 |
| 7.6 | Chiffrement de bout en bout | Preuves : option B (le serveur ne voit jamais le contenu) ; messages : ❓ Signal Protocol en V2 (seulement si les proches ont l'app) | crypto |
| 7.7 | ➕ Effacement d'urgence | Geste + PIN → < 2 s | R-EVD-06 |
| 7.8 | ➕ Guides système | Urgence SOS d'Android ; liste blanche batterie par fabricant (Tecno, Infinix, Itel, Samsung) | C7, C8 |
| 7.9 | ➕ Console admin minimale | Santé des envois, abus (spam SMS), support par code ; SSO + FIDO2 ; aucun accès au contenu | R-ADM-* |
| 7.10 | Tests de sécurité | Revue du modèle de menace T1 à T14 ; OWASP MASVS L2 ; test d'intrusion de l'API et de la page de suivi ; analyse de l'APK | securite/ |
| 7.11 | Audit de permissions | Manifeste final contre `android-permissions-play.md` ; aucune permission inutile ; SDK compatibles « Familles » | R-PLAY-01 |

**Jalon** : 0 vulnérabilité haute ou critique.
**Skills** : `/security-and-hardening`, agents `security-auditor` et `code-reviewer`.

---

## Sprint 8 — Tests et lancement (3 semaines)

| # | Étape | Détail |
|---|---|---|
| 8.1 | Tests unitaires | Déjà écrits à chaque sprint → ici : trous de couverture (cible ≥ 90 % pour la logique) |
| 8.2 | Tests d'intégration | Scénarios de bout en bout app + serveur (SOS, trajet, IA, preuves) |
| 8.3 | Tests sur 20 à 50 téléphones Android | Firebase Test Lab pour l'étendue + **20 téléphones physiques réels à Haïti** (Tecno, Infinix, Itel, Samsung A, Android 10 à 15) ; grille par appareil : service tenu, batterie, SMS, GPS |
| 8.4 | Tests réseau faible | 2G et EDGE simulés, pertes de paquets, Digicel et Natcom réels |
| 8.5 | Tests en mode avion | SOS, vérification, file d'attente, envoi au retour du réseau sans doublon |
| 8.6 | Tests batterie | Par mode (armé, trajet, écoute) ; cibles ≤ 4 %/h (écoute) et ≤ 3 %/h (trajet) |
| 8.7 | Tests de faux positifs | 40 h d'écoute en conditions réelles ; taux par heure ; ajustement des poids et seuils |
| 8.8 | ➕ Accessibilité et langues | TalkBack, police à 200 %, relecture native du créole |
| 8.9 | Politique Play Store | Sécurité des données ; types de service de premier plan + vidéos ; `SEND_SMS` ; « Familles » ; fiche honnête (« peut ne pas détecter ») |
| 8.10 | ➕ Juridique | Conditions d'utilisation et confidentialité finales ; consentement parental validé |
| 8.11 | Bêta fermée | 50 à 200 personnes (Play, test fermé) ; support en 3 langues |
| 8.12 | Lancement pilote | Déploiement progressif 5 % → 20 % → 50 % → 100 %, avec seuils d'arrêt ; astreinte ; procédure de retour arrière |

**Skills** : `/shipping-and-launch`, agents `test-engineer` et `web-performance-auditor`, `/observability-and-instrumentation`.

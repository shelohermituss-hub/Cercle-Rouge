# Phases 0 et 1 — Cadrage, dérisquage, socle (S0 à S3)

---

## S0 — Cadrage et décisions bloquantes (semaines 1–2)

**Objectif** : toutes les questions 🔴 sont tranchées, les démarches longues sont lancées, l'identité visuelle de base existe.

**Prérequis** : aucun.

| # | Tâche | Résultat | Réf. |
|---|---|---|---|
| S0.1 | Trancher Q1 (formule du score), Q2 (pays, numéros d'urgence), Q3 (âge et profils), Q10 (backend, hébergement), Q11 (SMS Play), Q12 (partage des preuves) | Mise à jour de `questions-ouvertes.md` + un ADR par décision | — |
| S0.2 | Lancer la revue juridique : enregistrement de tiers, âge, données en Haïti et dans l'UE, conditions d'utilisation (Q23 à Q26) | Rendez-vous avec un juriste, liste de questions envoyée | conformité |
| S0.3 | Créer le compte Play Console ; réserver le nom ; préparer la demande d'exception `SEND_SMS` (catégorie la plus proche, vidéo de maquette) | Compte prêt, demande rédigée | ADR-0005 |
| S0.4 | Choisir et ouvrir les comptes des fournisseurs : SMS (couverture Digicel et Natcom), Firebase, hébergement (2 régions) | Comptes de test, coûts estimés par alerte | Q13 |
| S0.5 | Protocole de collecte audio consentie (FR, HT, EN, bruits d'Haïti) : formulaire de consentement, matériel, partenaire, budget | Document `docs/ia/collecte-donnees.md` | Q19, Q20 |
| S0.6 | Atelier des mots-clés de détresse en créole avec 5 à 10 locuteurs | Liste validée dans `docs/ia/` | Q20 |
| S0.7 | Identité visuelle : nom définitif ?, palette accessible (contraste AA), typographie, icône, ton des messages (`/brand`, `/design`, `/ui-ux-pro-max`) | Mini design system documenté (`docs/design/`) | R-UI-01, R-UI-04 |
| S0.8 | Maquettes basse fidélité : onboarding, écran principal (7 états × normal, discret, leurre), vérification, SOS, trajet, coffre-fort, page proche | Maquettes revues avec 3 à 5 utilisateurs cibles | modules produit 1, 2, 10 |
| S0.9 | Acheter ou réunir les 4 téléphones de test | Matrice d'appareils dans `docs/plan/appareils.md` | R-PLAY-03 |

**Démo** : présentation des décisions et des maquettes cliquables.
**Risques** : décisions qui traînent → limiter le sprint dans le temps (timebox) ; une décision par défaut documentée vaut mieux qu'un blocage.
**Skills** : `/interview-me`, `/idea-refine`, `/brand`, `/design`, `/ui-ux-pro-max`, `/documentation-and-adrs`.

---

## S1 — Prototypes de dérisquage (semaines 3–4)

**Objectif** : prouver sur de vrais téléphones que les 4 points techniques les plus risqués fonctionnent. **Code jetable**, dans `spikes/`, jamais fusionné dans `app/`.

**Prérequis** : S0.9 (appareils).

| # | Prototype | Question à laquelle répondre | Mesure de succès |
|---|---|---|---|
| S1.1 | Service de premier plan `microphone` + `AudioRecord` 16 kHz + YAMNet LiteRT, porte VAD | L'écoute tient-elle 2 h écran éteint sur chaque appareil ? Combien de batterie ? Que font les fabricants (Tecno, Infinix, Samsung) ? | ≥ 2 h sans arrêt sur 3 des 4 appareils ; ≤ 4 %/h ; latence d'inférence < 30 ms sur l'entrée de gamme |
| S1.2 | Enregistrement Opus chiffré en flux (`MediaCodec` + Ogg → Tink `StreamingAead`) | Peut-on enregistrer sans jamais écrire en clair sur le disque ? | Fichier relisible ; aucun fichier en clair (inspection du répertoire) ; CPU < 10 % |
| S1.3 | `SmsManager` multipart + rapports d'envoi et de remise ; `ACTION_CALL` vers un proche ; `ACTION_DIAL` vers le 114 | Comportement réel chez Digicel et Natcom, avec des accents (UCS-2) | SMS reçu < 10 s ; rapports reçus |
| S1.4 | Service de premier plan `location` 10 s pendant 1 h de trajet réel (tap-tap, moto) | Précision, trous, batterie, repli sans Google Play Services | ≤ 3 %/h ; précision médiane < 30 m |

**Livrables** : `docs/plan/rapport-S1.md` avec les mesures par appareil et une recommandation go/no-go par point ; ADR modifiés si besoin.
**Démo** : vidéo des 4 prototypes sur l'entrée de gamme.
**Risques** : un fabricant tue le service → documenter le guide de liste blanche batterie, et décider du message à l'onboarding.
**Skills** : `/source-driven-development` (vérifier la doc Android **actuelle**), `/performance-optimization`, `/doubt-driven-development`.

---

## S2 — Socle du projet et cœur du domaine (semaines 5–6)

**Objectif** : un projet Android multi-modules avec la CI verte, et la **machine à états** complète, testée en JVM.

**Prérequis** : S1 go ; ADR-0001 à 0004.

| # | Tâche | Détail | Réf. |
|---|---|---|---|
| S2.1 | Squelette Gradle | Gradle Kotlin DSL, catalogue de versions (`libs.versions.toml`), plugins de convention (`build-logic/`) ; modules `:app`, `:core-domain`, `:alert-engine:logic`, `:alert-engine:android`, `:privacy-engine`, `:audio-engine`, `:sensor-engine`, `:location-engine`, `:evidence-engine`, `:contacts-engine`, `:sync-engine` (vides sauf `core-domain` et `alert-engine:logic`) ; `applicationId com.cerclerouge.app` ; minSdk 29 | modules/README |
| S2.2 | Qualité | ktlint, detekt (règles strictes, `!!` interdit), Android Lint `warningsAsErrors`, vérification des dépendances entre modules (R-ARCH-01 : `core-domain` sans Android) | R-ARCH-01/06 |
| S2.3 | CI GitHub Actions | `./gradlew check` + build de l'AAB de release non signé + vérification des clés i18n (script) + scan des secrets (gitleaks) + audit des dépendances ; cache Gradle ; temps < 10 min | Définition de terminé |
| S2.4 | Build release | R8, `debuggable=false`, `allowBackup=false`, `dataExtractionRules`, `network_security_config` (pas de trafic en clair), suppression des `Log.*` en release | R-PE-04/05, R-NET-07 |
| S2.5 | `core-domain` | Entités, objets-valeurs, événements scellés versionnés, ports (`Clock`, `EventLog`, `StateStore`…), fabriques validantes (`Result`) | R-DOM-01..04 |
| S2.6 | `alert-engine:logic` — machine à états | `reduce(state, event, policy, now) → (state, effects)` pour les 7 états et toutes les transitions de `01-etats-et-escalade.md` ; minuteries représentées par des effets ; R-SM-07 (idempotence) | R-SM-01..08, R-ESC-01..07, R-AL-01 |
| S2.7 | Tests de la machine à états | Table état × événement (100 % des transitions) ; tests de propriétés (jamais de niveau 4 sans consentement, jamais de position en `OFF`, SOS toujours possible) ; scénarios avec une horloge virtuelle | R-AL-01 |
| S2.8 | Application coquille Compose | Activité unique, navigation, thème clair et sombre depuis le design system S0.7, ressources `values`/`values-ht`/`values-en`, Hilt | R-UI-03/04 |

**Démo** : la CI verte ; le rapport de tests de la machine à états ; l'app qui s'ouvre dans les 3 langues.
**Risques** : sur-architecture → `/ponytail-review` sur S2.1 ; garder des modules vides tant qu'ils ne servent pas.
**Skills** : `/spec-driven-development`, `/test-driven-development`, `/ci-cd-and-automation`, `/api-and-interface-design`, `/ponytail`.

---

## S3 — Confidentialité, stockage chiffré et onboarding (semaines 7–8)

**Objectif** : toutes les données locales sont chiffrées ; l'utilisateur passe l'onboarding complet, avec des consentements séparés et un PIN.

**Prérequis** : S2 ; Q3, Q4, Q5 tranchées.

| # | Tâche | Détail | Réf. |
|---|---|---|---|
| S3.1 | Clés | Keystore : KEK_db, KEK_evid, KEK_prefs (AES-256-GCM, StrongBox si disponible), K_device (ECDSA P-256) ; gestion de l'absence de StrongBox ; tests instrumentés | ADR-0007, crypto |
| S3.2 | Base chiffrée | Room + SQLCipher, phrase de passe aléatoire enveloppée par KEK_db ; migrations testées | R-SY-01 |
| S3.3 | Réglages chiffrés | DataStore + Tink `Aead` (pas `security-crypto`) | crypto |
| S3.4 | Journal chaîné | Implémentation de `EventLog` : append-only, SHA-256 chaîné, détection de rupture ; écran « Journal » | R-PE-03, P7 |
| S3.5 | PIN | Argon2id (ou PBKDF2 ≥ 310 000) + sel ; PIN de contrainte (`DURESS`) ; temporisation progressive ; vérification locale uniquement | R-PE-02, R-SEC-01/02 |
| S3.6 | Biométrie | `BiometricPrompt` (classe Strong ou identifiants de l'appareil), clé liée à l'authentification | crypto |
| S3.7 | Consentements | `ConsentRepository` historisé (portée, version du texte, langue, date), écran Réglages > Confidentialité avec révocation | R-ONB-03/04 |
| S3.8 | Permissions | `PermissionSnapshot` relu à chaque `onResume` ; écrans explicatifs avant chaque demande ; rétrogradation propre en cas de retrait | R-PE-01, R-ONB-02, R-PLAY-02 |
| S3.9 | Onboarding | Les 9 écrans du module produit 1 ; reprise possible ; le SOS est visible dès le 1er écran (désactivé tant qu'il n'y a pas de contact, ou bien vers le composeur) ; attestation d'âge et de détenteur | R-ONB-01..08 |
| S3.10 | Politique de confidentialité | Version lisible FR, HT et EN (brouillon validé par le juriste) intégrée dans l'app | conformité |

**Tests** : chiffrement aller-retour ; base illisible sans clé ; PIN et contrainte ; parcours d'onboarding avec **toutes les permissions refusées** (R-ONB-02).
**Démo** : onboarding complet sur l'entrée de gamme, en créole ; tout refuser → l'app reste utilisable.
**Skills** : `/security-and-hardening`, `/frontend-ui-engineering`, `/ui-ux-pro-max`, `/test-driven-development`.

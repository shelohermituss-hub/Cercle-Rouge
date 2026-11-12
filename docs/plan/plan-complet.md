# Cercle Rouge — plan complet d’implémentation (MVP, IA avancée, écosystème)

## Contexte

Le porteur du projet a fourni la **documentation complète de l'application** (PDF, 23 sections : SOS, modes, écoute IA, déviation, alertes graduées, preuves, tableau de bord des proches, hors-ligne, sécurité, backend, conformité, interface, feuille de route en 3 phases). Il demande un plan **hyperdétaillé, étape par étape**, pour tout implémenter, en plus des écarts déjà relevés entre la spécification `docs/` et le code (`docs/plan/ecarts-spec.md`).

Ce plan croise trois sources : le PDF, la spécification du dépôt (`docs/`, 171 exigences `R-XXX-nn`) et l'état réel du code (vérifié le 2026-11-12). Il couvre le **MVP complet (phase 1)**, l'**IA avancée (phase 2)** et l'**écosystème (phase 3)**.

### Décisions du porteur (2026-11-12)
| Sujet | Décision | Conséquence dans le plan |
|---|---|---|
| Firebase | **Jamais** | Pas de FCM, pas de Firebase App Distribution, pas d'environnements Firebase. Notifications aux proches : SMS (Vonage), e-mail, WhatsApp, appel ; proches qui ont l'app : interrogation périodique + SMS de réveil. APK de test : GitHub Releases. Environnements : deux projets Supabase. Nouvel ADR « Pas de Firebase ». |
| Rétention | **Ajouter « Manuel »** (jusqu'à suppression par l'utilisateur) | Remplace la limite de 30 jours (N6, P5) par un choix explicite avec avertissement ; ADR qui amende ADR-0021 ; politique de confidentialité mise à jour ; côté serveur, purge à la suppression ou à la fermeture du compte. |
| Permissions | **Toutes celles du document** | `ACCESS_BACKGROUND_LOCATION`, `CALL_PHONE`, `READ_CONTACTS`, `CAMERA`, `ACTIVITY_RECOGNITION` ajoutées, chacune avec sa fonction, sa demande en contexte et son dossier Play. ADR qui remplace ADR-0006 (pas de localisation en arrière-plan). |
| Phase 3 | **Détaillée** comme le reste | Montre, bouton physique, mode entreprise, international, autorités : étapes complètes. |

### Ce qui est déjà fait (ne pas refaire)
Socle Android multi-modules, machine à états `AlertEngine` (escalade, minuteries, refractaire), PIN Argon2/biométrie, journal chaîné, contacts (5 max, ordre, rôles, langue, accord par SMS + page STOP), SOS (appui long, widget, tuile, notification), SMS direct + relais serveur `alert-sms`, page des proches (`web/track`), trajet (destination, route tracée, corridor, arrêt, retard, surveillance serveur `trip-watchdog`), phrase secrète (DTW), visualiseurs, preuves audio chiffrées (Tink, AAC, chaîne SHA-256, coffre, purge, export), parcours parent 12–15 ans, réglages, effacement d'urgence, mode dégradé.

### Écarts relevés dans le code (vérifiés)
- `RiskFusion` (`android/alert-engine/.../RiskFusion.kt`) **n'est branché nulle part** ; `Input.Detection` n'est jamais émis.
- Pas de module capteurs ; pas de YAMNet ; pas de mots-clés ; pas de réglages d'écoute.
- SMS d'alerte sans batterie, réseau ni confiance ; `custom` toujours `null` (`AlertDispatcher.kt:170`) ; texte de test différent du PDF.
- **Bogue** : désarmer ou finir un trajet ne prévient pas les proches (`AlertDispatcher.onDispatch` sort si `alert == null`, `AlertDispatcher.kt:62-72`).
- Accueil sans état réseau, batterie, GPS, dernier trajet, dernière alerte, liste des contacts.
- Pas d'historique des alertes ni des trajets ; pas de mode discret ; tuile/widget = SOS seulement ; « Désactiver » de la notification ouvre juste l'app.
- File `outbox` non relancée au retour du réseau (pas de WorkManager ni de rappel `ConnectivityManager`).
- Pas de stockage serveur des preuves ; pas d'e-mail, WhatsApp, appel ; contacts sans e-mail ni relation ; modes BUS/TRAIN absents, seuils non réglables ; 114 codé en dur ; aucun avertissement « ne remplace pas les secours ».

---

## Règles communes à chaque étape (définition de « fait »)

1. **Avant** : relire la section du PDF et de `docs/` concernée ; vérifier `docs/questions-ouvertes.md` ; poser les questions 🔴 au porteur.
2. **Code** : logique pure dans les modules JVM (`core-domain`, `alert-engine`, `location-engine`, `audio-engine`, `contacts-engine`, `evidence-engine`, nouveau `sensor-engine`) avec tests d'abord ; Android dans `:app`. Exigences citées `R-XXX-nn` dans le code et les tests.
3. **Serveur** : migration dans `supabase/migrations/` (RLS, RPC `SECURITY DEFINER` + `SET search_path = public`, `REVOKE` à `PUBLIC`/`anon`, `(select auth.uid())`, `FOR UPDATE`, idempotence, CHECK) ; tests dans `supabase/tests/security.sql` ; `supabase/scripts/test-local.sh` vert ; application distante (`apply_migration`, sans `drop`/`delete`) ; `security.sql` distant ; Security Advisor sans nouvelle alerte non acceptée.
4. **Textes** FR / HT / EN dans `res/values*/strings.xml` (aucun texte en dur).
5. **Contrôle** : `./gradlew ktlintFormat` puis `./gradlew check assembleDebug --continue` vert ; captures `ScreenshotTest` relues pour toute UI.
6. **Docs** : ADR si décision, suivi du sprint, `docs/conformite/android-permissions-play.md` pour toute permission, politique `web/track/confidentialite.html` pour toute donnée.
7. **Commit + push** sur `ccr-38a56721-9a8jjx` ; APK de test publié (GitHub Releases à partir de l'étape 0.5) ; points 📱 listés pour le porteur.

Légende : 🤖 développement · 👤 action du porteur · 📱 test sur téléphone · ⏱ estimation de développement (indicative).

---

# PHASE 0 — Fondations transversales (≈ 1,5 semaine)

### 0.1 File hors-ligne fiable (PDF 5.8, R-NET-01/06, R-SY-01)
- 🤖 `WorkManager` (dépendance AndroidX) : `OutboxWorker` contraint par `NetworkType.CONNECTED`, relancé au démarrage de l'app et au boot.
- 🤖 `ConnectivityManager.registerDefaultNetworkCallback` dans `ProtectionService` : au retour du réseau, `AlertDispatcher.drain()` immédiat.
- 🤖 Priorités de la file : alerte > position > métadonnées > audio > vidéo (champ `priority` de `outbox`, migration Room v5).
- ✅ Test : JVM sur l'ordonnancement ; Robolectric sur la relance ; 📱 mode avion → SOS → réseau → SMS envoyé une seule fois.
- ⏱ 2 j.

### 0.2 Accusé serveur et déduplication (R-SY-05, R-NET-02/03)
- 🤖 Table `deliveries` (alert_id, contact_hash, channel, status, provider_id, at) + RPC `ack_delivery`.
- 🤖 App : si le serveur n'a pas accusé l'envoi en 20 s → SMS direct ; jamais deux SMS au même proche pour le même `alertId` (clé d'idempotence `alertId+type+seq`).
- ✅ Tests SQL d'idempotence ; JVM sur la règle des 20 s (horloge injectée).
- ⏱ 2 j.

### 0.3 État de l'appareil (`DeviceStatus`) partagé
- 🤖 Nouveau module JVM `:sensor-engine` (squelette) + adaptateur Android : batterie (%, charge), réseau (aucun / cellulaire / Wi-Fi / validé), GPS activé, mode avion, économie d'énergie. Réutiliser le récepteur batterie de `audio/DeviceHealth.kt` (le déplacer).
- 🤖 `StateFlow<DeviceStatus>` injecté partout (accueil, alertes, preuves, battement).
- ⏱ 1 j.

### 0.4 Sécurité du transport et qualité
- 🤖 Épinglage des clés publiques (R-NET-07, ADR-0014) dans `res/xml/network_security_config.xml` : broches de la chaîne de `*.supabase.co`, de `api.nexmo.com` n'est pas appelé par l'app ; broches de secours ; expiration 6 mois.
- 🤖 detekt (`!!` interdit) dans `build.gradle.kts` + CI.
- 🤖 Vérification i18n en CI (clés FR/HT/EN identiques).
- ⏱ 1,5 j.

### 0.5 Environnements et distribution (sans Firebase)
- 👤 Créer un 2ᵉ projet Supabase **staging** ; passer la production en plan Pro (PITR) (R-BE-10).
- 🤖 Variantes Gradle `staging` / `prod` (URL et clé publique par variante, `local.properties`).
- 🤖 Workflow GitHub Actions : APK signé de test publié en **GitHub Release** à chaque tag (remplace Firebase App Distribution) ; clé de signature dans les secrets GitHub 👤.
- 🤖 ADR « Pas de Firebase » : canaux de remplacement, conséquences (pas de push, réveil par SMS).
- ⏱ 1,5 j.

---

# PHASE 1 — Compléter le MVP (≈ 9 à 11 semaines)

## Bloc A — Le cerveau et la détection

### 1.1 Décideur de risque (PDF 8.2, 8.5, 8.6 ; S5.08 ; R-AL-06, R-RISK-01..03)
- 🤖 `android/alert-engine/.../RiskDecider.kt` (pur) :
  - `Observation(component A|B|C|D, value 0..1, reason, origin, at, audioClass?, keywordHighConfidence)`.
  - Fenêtre glissante 10 s ; A, B, C = max ; D = somme des origines distinctes plafonnée à 1 (0,5 immobilité + 0,5 réseau = 1).
  - E (contexte) calculé par l'app : nuit 22 h–5 h +0,3, profil 12–14 ou isolé +0,3, zone non sûre +0,4, plafond 1.
  - Signaux indépendants = origines distinctes avec valeur ≥ 0,5 (une classe audio = une origine).
  - Score et recommandation : réutiliser `RiskFusion.score` / `RiskFusion.recommend` et `RiskWeights.forProfile` (`RiskFusion.kt`).
  - Hystérésis entrée 0,6 / sortie 0,4 (+ décalage de sensibilité) ; émission sur front montant seulement ; réfractaire déjà géré par `AlertEngine` (`refractoryUntil`).
  - `DecisionConfig(version = "risk-1", …)` versionnée ; `Decision.journal()` → `risk= A= B= C= D= E= sig= act= cfg= seuil=`.
- 🤖 `android/app/.../protection/RiskBrain.kt` (Singleton démarré par `ProtectionService`) : collecte les observations, appelle le décideur, envoie `Input.Detection` via `ProtectionController.send`, journalise chaque décision ≥ LOG (`EventLog`), profil via `AgeBand.profile()` (`core-domain/AgeBand.kt`).
- 🤖 Le visualiseur « Réflexion IA » (`ui/home/ListenViz.kt`) affiche le vrai score, les composantes et la décision.
- ✅ Tests JVM (≥ 12 scénarios : un signal seul → rien ; deux origines → VERIFY ; même origine ×2 → 1 ; fenêtre expirée ; hystérésis ; contexte seul → NONE ; mot-clé sûr → VERIFY ; ALERT_CONTACTS seulement avec urgence automatique ; D cumulatif ; journal complet).
- ⏱ 4 j.

### 1.2 Capteurs (PDF 5.3 bruits de lutte, 9.2 perte de signal ; `docs/modules/sensor-engine.md` ; R-SE-01..04)
- 🤖 `:sensor-engine` pur : détecteurs par règles sur traces (50 Hz max) — chute (chute libre < 0,3 g > 100 ms → impact > 2,5 g → immobilité > 2 s), mouvement violent / **arrachage** (variance ‖a‖ sur 2 s + gyroscope > 5 rad/s, puis déplacement rapide), arrêt brusque, téléphone lancé, immobilité N min.
- 🤖 Adaptateur Android : `SensorManager` (`SENSOR_DELAY_GAME`), mode LOW avec `TYPE_SIGNIFICANT_MOTION` / `TYPE_STEP_DETECTOR`, NORMAL/HIGH selon l'état ; capteur absent → détection désactivée.
- 🤖 **`ACTIVITY_RECOGNITION`** (décision porteur) : Activity Recognition Transition API (à pied, en véhicule, immobile) pour D et pour proposer le mode de transport.
- 🤖 Observations : chute/violence → B (+0,2 plafonné), immobilité/réseau → D ; jamais d'alerte directe (vérification au mieux).
- 🤖 Résumé ±30 s autour d'une détection joint aux preuves (R-SE-03), jamais de flux brut persisté.
- ✅ Banc de rejeu JVM (marche, course, bus, moto, chute sur matelas, téléphone arraché, lancé) dans `sensor-engine/src/test/resources`.
- 📱 Calibration sur 3 téléphones.
- ⏱ 6 j.

### 1.3 Réglages d'écoute (PDF 8.3, 8.6 ; S5.09)
- 🤖 Écran « Écoute IA » (étendre `ui/listen/Listen.kt`) : sensibilité faible/normale/élevée ; cases par type de son (cris, chocs, alarmes, pleurs, mots-clés) ; **contextes à ignorer** sport, concert, conduite, **sommeil** (PDF), personnalisé, avec durée (1 h, 3 h, jusqu'à demain) ; durée de confirmation 10/20/30 s (`Policy.verifyDelay`) ; mémoire 30 à 120 s du tampon.
- 🤖 Effet : classes concernées +0,2 au seuil ; jamais sur phrase secrète, mot-clé sûr ni SOS ; expiration automatique.
- 🤖 Raccourci « contexte » dans la notification persistante.
- ⏱ 3 j.

### 1.4 Mots-clés locaux (PDF 19 point 9, MVP)
- 🤖 **Étape MVP sans données** : mots-clés **enrôlés par l'utilisateur** (« au secours », « anmwe », « help »…) avec le moteur DTW existant (`audio-engine/SecretPhrase.kt`, `PhraseTemplate`) : jusqu'à 5 mots-clés, 3 répétitions chacun, empreinte chiffrée ; détection → observation A (confiance) + `keywordHighConfidence` au-dessus d'une marge.
- 🤖 Liste par défaut suggérée (`DistressKeywords` HT/FR) à enregistrer.
- 🤖 « Silence brutal après activité vocale » (PDF 5.3) : règle VAD (voix forte puis silence > 20 s) → D.
- ✅ Banc JVM sur sons synthétiques + 📱 enregistrements réels.
- ⏱ 4 j.

## Bloc B — Alertes et proches

### 1.5 Contenu des alertes (PDF 5.1, 10.3, 12.3)
- 🤖 `contacts-engine/.../Messages.kt` : ajouter niveau de batterie, état du réseau, niveau de confiance, raison, heure, lien, **bouton d'appel** (`tel:` de l'utilisateur), secours ; garder GSM-7 et 2 SMS max.
- 🤖 Réglage « Message personnalisé » (Réglages) → `custom` réellement passé (`AlertDispatcher.kt:170`).
- 🤖 Test d'alerte : texte exact du PDF « Ceci est un test de Cercle Rouge. Aucune urgence réelle. » (FR/HT/EN), sans corps d'alerte.
- ✅ Tests de gabarits (longueur, langue, accents).
- ⏱ 1,5 j.

### 1.6 Prévenir les proches au désarmement et en fin de trajet (bogue R-SEC-03)
- 🤖 `AlertDispatcher.onDispatch` : traiter `PROTECTION_DISABLED` et `TRIP_ENDED` sans alerte ouverte (destinataires = proches du trajet / principaux), message neutre « X a arrêté la protection à HH:MM » ; anti-spam existant.
- ✅ Test de table sur `AlertEngine` + test du dispatcher.
- ⏱ 1 j.

### 1.7 Contacts complets (PDF 12)
- 🤖 `TrustedContact` + `ContactEntity` (Room v5) : **e-mail**, **relation** (texte court), rôles principal, **secondaire**, médical, **professionnel**, **voisinage** (`ContactRole`), canaux préférés SMS / e-mail / WhatsApp / appel.
- 🤖 **`READ_CONTACTS`** (décision porteur) : recherche dans le répertoire, en plus du sélecteur système ; jamais d'envoi du répertoire au serveur (R-CE-02).
- 🤖 Écran Contacts : édition des nouveaux champs, statut de consentement visible.
- ⏱ 2,5 j.

### 1.8 Envoi serveur automatique multicanal (PDF 5.5, 14.1 ; S3.04 sans FCM)
- 🤖 Migration : `alert_recipients` (numéro chiffré pgcrypto + hachage, e-mail chiffré, canaux, langue, ordre) envoyés par l'app à l'ouverture de l'alerte ; trigger → `pg_net` → Edge Function **`notify-alert`**.
- 🤖 `notify-alert` : SMS Vonage + **e-mail** (fournisseur à choisir 👤 : clé dans les secrets) + lien de suivi ; `deliveries` ; réessais ; respect STOP ; quotas existants (`server_sms_allowed`).
- 🤖 L'app reste premier canal (SMS direct) ; déduplication 0.2.
- ✅ `security.sql` (aucun numéro en clair, propriétaire seul, quotas) ; p95 < 5 s mesuré.
- ⏱ 4 j.

### 1.9 Tableau de bord des proches complet (PDF 5.7 ; S3.08–S3.10 ; S4.10 ; R-DASH-01..06)
- 🤖 `private.track_read` (migration) : statut, dernière position, chronologie, **batterie**, **réseau**, preuves partagées (après 1.15), bouton **appeler l'utilisateur** (numéro vérifié, consentement à l'activation), secours.
- 🤖 **Lien de suivi de trajet** : `tracking_links.trip_id` (alert_id rendu nullable + CHECK un seul des deux), RPC `create_trip_link`, `track_read` branche « trajet » (position du battement, arrivée prévue, « plus de nouvelles depuis »), envoi du lien aux proches du trajet au départ (SMS).
- 🤖 `web/track/track.js` : vue trajet, batterie/réseau, appel, actions des autres proches visibles (R-DASH-05, interrogation 5 s), < 200 Ko, CSP.
- 🤖 « J'ai pris contact » arrête l'escalade ; « Alerte fausse » informe sans clôturer (test de bout en bout, S3.10).
- 🤖 **Vue « proche » dans l'app** (S3.09) : écran listant les personnes qui m'ont choisi et leurs alertes/trajets en cours (RPC `my_watched`), rafraîchi toutes les 15 s quand l'app est ouverte ; réveil par SMS (pas de push).
- ⏱ 6 j.

### 1.10 Accueil conforme (PDF 16.2) et historique (PDF 16.1)
- 🤖 Accueil : bandeau d'état **GPS, réseau, batterie** (depuis `DeviceStatus`), **contacts d'urgence** (prénoms + statut), **dernier trajet**, **dernière alerte** ; garder SOS, ON/OFF, statut de l'écoute.
- 🤖 Room : tables `alert_history` et `trip_history` (résumés sans coordonnées : heure, durée, raison, niveau, issue) ; écran **Historique des alertes** (+ trajets) ; effacement possible.
- ⏱ 3 j.

### 1.11 Modes de protection (PDF 5.2)
- 🤖 Sélecteur de mode : Désactivé, **Écoute IA uniquement** (`ARMED_AUDIO_ONLY`), Trajet protégé, **Mode discret**, Urgence.
- 🤖 **Mode discret** (R-HOME-03, R-AL-05) : notification persistante neutre (titre générique, icône neutre), aucun mot « alerte » à l'écran, vibration codée seulement ; l'indicateur micro Android reste (obligatoire, N2).
- 🤖 Activation depuis **tuile, widget et notification** (PDF 8.1) : armer en 1 geste ; désarmer → écran PIN/biométrie (P8). Widget migré vers Glance avec état.
- ⏱ 3 j.

### 1.12 Trajet complet (PDF 5.4, 9.2, 9.3)
- 🤖 Modes **BUS** et **TRAIN** ajoutés à `TransportMode` (`location-engine/.../Trip.kt`) avec seuils du PDF (marche 100–200 m / 10–15 min ; voiture 300–800 m / 15–30 min ; bus 500–1 000 m / 20–40 min ; taxi 300–800 m / 10–20 min ; train 1–3 km / 30–60 min) ; **seuils réglables** par mode dans Réglages.
- 🤖 **Contacts à alerter** choisis par trajet (écran Trajet).
- 🤖 **Corridor sur la route** : `TripMonitor` utilise les points de `RouteService` (`trip/Routes.kt`) au lieu de la ligne droite quand la route existe.
- 🤖 Événements manquants : **trajet anormalement long** (durée > estimée × 1,5), **retour vers le départ**, **perte de signal** (D), **entrée en zone non sûre** (zones existantes `UnsafeZone`) ; vérifier chaque cas dans le banc GPX.
- 🤖 **`ACCESS_BACKGROUND_LOCATION`** (décision porteur) : demandée après explication, utilisée pour reprendre le suivi si le service est relancé par le système ; ADR qui remplace ADR-0006 ; dossier Play (vidéo).
- ⏱ 4 j.

## Bloc C — Preuves

### 1.13 Rétention « Manuel » (décision porteur)
- 🤖 `Retention.MANUAL` (`core-domain/Model.kt`, `evidence-engine/Retention.kt`) ; avertissement clair à l'onboarding et aux réglages ; purge seulement à la suppression ; ADR qui amende ADR-0021 et N6.
- ⏱ 1 j.

### 1.14 Métadonnées de preuve (PDF 5.6, 11.1)
- 🤖 Chaque alerte enregistre dans son coffre : positions, horodatage, **batterie**, **réseau**, **événements détectés** (décisions 1.1), extrait du journal ; tout chiffré par la clé de l'alerte.
- ⏱ 2 j.

### 1.15 Envoi des preuves au serveur (S6.03–S6.05 ; R-EVD-04/05 ; R-SY-04)
- 🤖 Bucket privé `evidence` (Supabase Storage), RLS sur `storage.objects` (propriétaire ; proches si partagé), objets déjà chiffrés côté client, envoi **reprenable** (TUS), priorités 0.1, Wi-Fi seulement hors urgence (R-NET-04).
- 🤖 Racine de chaîne signée par `K_device` → RPC `register_chain_root`.
- 🤖 Purge serveur alignée sur la rétention choisie (dont « Manuel »).
- ✅ 📱 coupure réseau en plein envoi → reprise.
- ⏱ 5 j.

### 1.16 Partage de bout en bout (S6.06, S1.13b, ADR-0016)
- 🤖 Paire de clés Tink hybride par appareil, publication signée (`publish_public_key`) ; clé d'alerte enveloppée pour chaque proche qui a l'app (`evidence_key_wraps`) ; lecture dans la vue « proche » ; page web : « Installe Cercle Rouge pour écouter ».
- ⏱ 5 j.

## Bloc D — Sécurité, avertissements, comptes

### 1.17 Détection de fermeture forcée et dernière activité (PDF 13.1 ; S7.03 ; S4.09)
- 🤖 Battement de **protection** (pas seulement de trajet) toutes les 5 min quand l'utilisateur l'active ; perte → SMS « dernière activité » aux proches (réutiliser `trip-watchdog` et `trip_alerts_due`, généralisés).
- 🤖 Au redémarrage : « Protection interrompue à HH:MM » (notification + journal).
- 🤖 `ACTION_SHUTDOWN` → dernier SMS (opt-in) avec position.
- 🤖 Racine du journal anti-effacement envoyée au serveur pendant une alerte ou un trajet.
- ⏱ 3 j.

### 1.18 Avertissements et numéros d'urgence (PDF 20, 22, 23)
- 🤖 Numéros d'urgence **par pays** (114/115/116 Haïti, 17/15/18/112 France, 112 UE, 911 US/CA), détectés par le pays de la SIM, modifiables ; remplace `tel:114` codé en dur (`Home.kt:185`).
- 🤖 Avertissement « Cercle Rouge peut aider à alerter rapidement, mais il ne remplace pas les secours… » à l'onboarding, dans Réglages › À propos et sur l'écran d'urgence ; message de sécurité (section 23) ; slogan sur l'écran d'accueil de l'onboarding.
- ⏱ 1,5 j.

### 1.19 Inscription et connexion (PDF 14.1, 19 point 1)
- 🤖 **Connexion sur un nouveau téléphone** par code SMS (Supabase Auth, Vonage déjà configuré) : retrouver le compte serveur ; contacts restaurés depuis le serveur (après 1.8) ; preuves locales non transférées (vie privée).
- 🤖 Déconnexion ; changement de numéro.
- ⏱ 3 j.

### 1.20 Onboarding final et accessibilité (PDF 15.3, 16.3 ; R-ONB-07, R-UI-01..07)
- 🤖 Écran résumé « actif / inactif » ; guide « Urgence SOS d'Android » (5 pressions) ; explication de chaque permission au moment de la demande ; utilisation partielle sans permission vérifiée.
- 🤖 **Mode clair** et **contraste élevé** ; TalkBack sur tous les écrans ; police 200 % sans troncature ; cibles ≥ 48 dp ; actions critiques en bas.
- ⏱ 4 j.

### 1.21 Console admin et audit (S7.07–S7.09 ; R-ADM-01..05)
- 🤖 `web/admin` (accès conditionnel, MFA) : santé des envois (`deliveries`), abus (quotas), support par code ; aucun contenu, aucune position (R-ADM-03) ; journal append-only des actions admin.
- 🤖 Revue du modèle de menace T1–T14, OWASP MASVS L2, tests d'intrusion automatisés de la RLS et des Edge Functions (scripts dans `supabase/tests/`), audit des dépendances.
- ⏱ 6 j.

### 1.22 Qualité et lancement du MVP (S8)
- 🤖 Jacoco : couverture ≥ 90 % de la logique ; tests de bout en bout sur staging.
- 🤖 Test « aucun audio ne sort » (S5.11) ; banc `tools/audio-eval/` (S5.10).
- 👤📱 Campagne téléphones (Tecno, Infinix, Itel, Samsung A ; Android 10–15), réseau faible, Digicel/Natcom, batterie (≤ 4 %/h écoute, ≤ 3 %/h trajet), 40 h d'écoute réelle.
- 👤 Dossier Play (sécurité des données, types de service, `SEND_SMS`, `CALL_PHONE`, localisation en arrière-plan, `READ_CONTACTS`, vidéos), juriste (conditions, confidentialité FR/HT/EN, consentement parental), bêta fermée, lancement progressif.
- ⏱ 2 semaines de dev + délais externes.

---

# PHASE 2 — IA avancée et canaux (≈ 12 à 16 semaines)

### 2.1 Classification audio YAMNet (LiteRT) (PDF 5.3, 8.4 ; S5.07 ; R-AE-02)
- 🤖 Dépendance LiteRT ; modèle `yamnet.tflite` (≈ 4 Mo) embarqué dans `audio-engine/src/main/assets` avec **hachage vérifié au chargement** et carte du modèle ; 👤/🤖 source du fichier (Kaggle Models / TF Hub) à autoriser dans le réseau ou fourni par le porteur.
- 🤖 Fenêtres 0,975 s, pas 0,5 s, porte d'énergie + VAD avant inférence ; correspondance vers nos classes `scream`, `crying`, `glass_break`, `impact`, `alarm`, `horn_brake`, `gunshot` et classes d'inhibition `music_crowd` (`docs/ia/modele-ia-locale.md`).
- 🤖 Sorties → observations A (cri, pleurs) et B (violence) du décideur ; interrupteur éteint par défaut, activé par paliers.
- ✅ Latence < 30 ms, batterie ≤ 4 %/h, banc audio.
- ⏱ 8 j.

### 2.2 Programme de données (Q19, N7)
- 🤖 Bouton opt-in « Envoyer ce faux positif pour améliorer » (extrait choisi, consentement séparé, révocable), bucket séparé, purge.
- 👤 Partenaire de collecte, protocole et consentements écrits (créole, bruits d'Haïti).
- ⏱ 3 j.

### 2.3 Mots-clés entraînés FR / HT / EN (S5.04, S5.05)
- 🤖 `ml/` : notebooks reproductibles DS-CNN par langue → LiteRT int8 + carte du modèle ; intégration avec hachage ; remplace ou complète les mots-clés enrôlés (1.4).
- ✅ Rappel à faux positifs fixé ; équité par langue/genre/âge (écart > 10 points = bloqué).
- ⏱ 10 j (après les données).

### 2.4 Détresse vocale (PDF 5.3 « voix tremblante », 8.4)
- 🤖 Classifieur sur empreintes de parole, **jamais déclencheur seul** (plafond de contribution), interrupteur expérimental, évaluation par langue, genre et âge ; revue juridique (Q22).
- ⏱ 10 j.

### 2.5 Analyse de contexte et apprentissage local (PDF 8.6, 21 phase 2)
- 🤖 Apprentissage local borné (R-AUD-10) : +0,05 par fausse alerte répétée au même lieu/heure, plafond +0,15, remise à zéro ; contextes récurrents proposés (« Tu es souvent au marché le samedi : ignorer ? »).
- 🤖 Modèles personnalisés : mots-clés et phrase de l'utilisateur ; aucune donnée ne quitte le téléphone.
- 🤖 Fusion multimodale apprise (régression logistique entraînée hors ligne sur les données du pilote), versionnée, comparée aux règles avant bascule.
- ⏱ 8 j.

### 2.6 Vidéo de preuve (PDF 5.6, 11.1 ; `CAMERA`)
- 🤖 Service de premier plan type `camera`, clips de 15 s H.264 480p chiffrés (Tink), Wi-Fi par défaut, purge et envoi comme l'audio ; option désactivée par défaut.
- ⏱ 6 j.

### 2.7 Appel automatisé (PDF 5.5 ; `CALL_PHONE`)
- 🤖 Niveau 3 : appel du contact principal depuis le téléphone (`CALL_PHONE`) **jamais** vers un numéro d'urgence (composeur seulement, N4).
- 🤖 Appel serveur avec message vocal FR/HT/EN (Vonage Voice API, TTS) depuis `notify-alert` ; quota par alerte ; arrêt dès « Je vais bien ».
- ⏱ 5 j.

### 2.8 WhatsApp (PDF 5.5)
- 👤 WhatsApp Business Platform (Meta), modèles approuvés FR/HT/EN.
- 🤖 Canal WhatsApp dans `notify-alert` ; préférence par contact (1.7).
- ⏱ 4 j.

### 2.9 Mode leurre et PIN de contrainte (PDF 13.1 ; ADR-0015, ADR-0017)
- 🤖 PIN de contrainte : semble désarmer, garde l'alerte et prévient en silence.
- 🤖 Mode leurre : écran neutre interne (calculatrice) + alias d'icône déclaré à Play ; notification de protection toujours présente (N2).
- ⏱ 6 j.

### 2.10 Chiffrement de bout en bout des messages (PDF 11.2)
- 🤖 Messages proche ↔ utilisateur chiffrés de bout en bout (protocole Signal ou Tink hybride + double ratchet allégé) dans la vue « proche ».
- ⏱ 8 j.

---

# PHASE 3 — Écosystème (≈ 14 à 20 semaines)

### 3.1 Tableau de bord familial (PDF 21 phase 3)
- 🤖 Espace « famille » : plusieurs personnes protégées par un même parent (tables `families`, `family_members`, RLS), vue d'ensemble web et app, historique partagé consenti, sans suivi permanent (N9 : la personne protégée garde le contrôle).
- ⏱ 8 j.

### 3.2 Montre connectée
- 🤖 Module `:wear` (Wear OS, Compose for Wear) : tuile et complication SOS, état de la protection, vibration de vérification ; Data Layer API vers le téléphone ; SOS depuis la montre même téléphone dans la poche.
- 📱 Test sur 1 montre Wear OS.
- ⏱ 10 j.

### 3.3 Bouton physique (PDF 13.2)
- 🤖 Bouton Bluetooth de panique (BLE, type iTag) : association par Companion Device Manager, appui long → SOS.
- 🤖 **Appui long sur le volume** : `AccessibilityService` dédié, désactivé par défaut, déclaration Play (risque de refus documenté).
- 🤖 **Triple pression d'alimentation** : impossible pour une app tierce → guide vers « Urgence SOS » d'Android (1.20).
- ⏱ 6 j.

### 3.4 Intégration aux autorités (PDF 10.2 niveau 4)
- 👤 Interlocuteur officiel et base légale (Q27).
- 🤖 Niveau 4 : transmission structurée (position, heure, niveau, preuves partagées) à un point d'entrée partenaire ; consentement explicite préalable, désactivé par défaut ; sans partenaire : composeur + SMS au numéro configuré.
- ⏱ 6 j (après accord).

### 3.5 Mode entreprise
- 🤖 Organisations (`organizations`, `org_members`, rôles), invitation des employés, tableau de bord des alertes des membres **consentants seulement**, aucune position hors alerte, journal d'audit, facturation (👤 choix du prestataire).
- ⏱ 12 j.

### 3.6 Support international
- 🤖 Numéros d'urgence par pays (1.18 étendu), indicatifs, fuseaux, langues supplémentaires (espagnol), couverture SMS par pays (Vonage), cartes mondiales, conformité RGPD (hébergement UE possible : projet Supabase UE).
- ⏱ 8 j.

### 3.7 iOS (optionnel)
- 🤖 App iOS (Swift/SwiftUI ; logique partagée via Kotlin Multiplatform pour `core-domain`) ; limites iOS documentées (pas d'écoute continue en arrière-plan comme Android).
- ⏱ 12 semaines.

---

## Ordre d'exécution et jalons
1. **Phase 0** (fondations) → APK de test.
2. **Bloc A** (1.1 → 1.4) : le cerveau et la détection → APK + 📱 calibration capteurs.
3. **Bloc B** (1.5 → 1.12) : alertes, proches, accueil, modes, trajet → APK + 📱 SMS réels, page des proches.
4. **Bloc C** (1.13 → 1.16) : preuves serveur et partage.
5. **Bloc D** (1.17 → 1.22) : sécurité, avertissements, comptes, accessibilité, audit, campagne → **MVP 1.0**.
6. **Phase 2** dans l'ordre 2.1 → 2.10 (2.3 et 2.4 attendent les données).
7. **Phase 3** selon les partenariats (3.4) et les priorités du porteur.

## Actions du porteur (👤) à lancer tôt
Projet Supabase staging + plan Pro · clé de signature Android · fournisseur d'e-mail · WhatsApp Business · source du modèle YAMNet · partenaire de collecte audio · juriste · dossier Play (permissions sensibles) · interlocuteur autorités · téléphones et montre de test.

## Vérification globale
- À chaque étape : `./gradlew ktlintFormat && ./gradlew check assembleDebug --continue` ; `supabase/scripts/test-local.sh` ; `security.sql` distant (marqueur `ALL_PASS`) ; Security Advisor ; captures `ScreenshotTest` relues.
- Fin de bloc : APK GitHub Release + liste 📱 dans `docs/demarches/tests-telephone.md`.
- Fin de phase 1 : scénarios de bout en bout du PDF (SOS hors ligne, trajet dévié, cri + chute → vérification → proches, preuves envoyées au retour du réseau, page des proches) sur staging puis téléphones réels.

## Fichiers clés (réutilisés ou modifiés)
- Cerveau : `android/alert-engine/.../RiskFusion.kt`, `AlertEngine.kt` (`Input.Detection`, `refractoryUntil`), `android/app/.../protection/ProtectionController.kt`, `ProtectionService.kt`.
- Écoute : `android/audio-engine/.../SecretPhrase.kt` (`Listener`, `PhraseTemplate`), `app/.../audio/AudioListener.kt` (`activity`), `ui/listen/Listen.kt`, `ui/home/ListenViz.kt`.
- Alertes : `contacts-engine/.../Messages.kt`, `Recipients.kt` (`channelsFor`), `app/.../alert/AlertDispatcher.kt`, `server/ServerSync.kt`.
- Trajet : `location-engine/.../Trip.kt`, `TripMonitor`, `app/.../trip/TripController.kt`, `trip/Routes.kt`, `server/TripWatch.kt`.
- Preuves : `evidence-engine/.../Retention.kt`, `app/.../evidence/EvidenceStore.kt`, `EvidenceRecorder.kt`.
- Serveur : `supabase/migrations/*`, `supabase/functions/{track,alert-sms,trip-watchdog}`, `supabase/tests/security.sql`, `web/track/track.js`.

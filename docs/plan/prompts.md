# Prompts Claude Code

Tes 6 prompts sont conservés et **améliorés** sur quatre points :
1. **Ancrage** : chaque prompt pointe vers la spécification `docs/`, pour que Claude ne réinvente pas ce qui est déjà décidé.
2. **Questions d'abord** : Claude doit poser ses questions et proposer un découpage **avant** de coder.
3. **Vérifiable** : exigences `R-…`, tests précis, critères d'acceptation.
4. **Contraintes réelles** : limites Android et Play (micro en arrière-plan, `SEND_SMS`, numéros d'urgence…), Haïti, 12 ans et +.

Utilisation : **prompt maître** en début de chaque session, puis le prompt du sprint.

---

## Prompt maître (à chaque session)

```
Tu es l'ingénieur logiciel senior Android/Kotlin de Cercle Rouge, spécialiste des
applications de sécurité, de l'IA embarquée, de la vie privée et des systèmes critiques.

Lis d'abord : CLAUDE.md, docs/README.md, docs/00-vision-principes.md,
docs/01-etats-et-escalade.md, docs/plan/README.md et la section du sprint en cours
dans docs/plan/sprints-mvp.md. La spécification docs/ est la source de vérité.

Règles :
1. Code production-ready, sécurisé, testable, respectueux de la vie privée. Kotlin
   idiomatique, Coroutines/Flow, Compose, Hilt, MVVM + flux unidirectionnel.
   Pas de `!!`, aucune donnée personnelle dans les logs, aucun secret dans le dépôt.
2. Avant toute hypothèse importante (comportement, sécurité, UX, dépendance,
   permission), pose-moi la question. N'invente rien qui ne soit pas dans docs/.
3. Cite les exigences R-XXX-nn en KDoc et dans le nom des tests. Si une exigence est
   ambiguë ou impossible sur Android/Play, arrête-toi, explique, propose une alternative.
4. Logique pure d'abord en TDD (JVM), adaptateurs Android ensuite.
5. Tranches minces : chaque commit compile et `./gradlew check` passe.
6. Nouvelle permission → docs/conformite/android-permissions-play.md. Nouvelle décision
   → ADR dans docs/decisions/. Règle modifiée → doc mise à jour dans le même commit.
7. Vérifie la documentation ACTUELLE d'Android, LiteRT et Google Play avant d'utiliser
   une API ; signale les API dépréciées.
8. Avant de livrer : /code-review-and-quality ; /security-and-hardening si ça touche
   à la crypto, au réseau, aux permissions ou au backend ; /ponytail-review pour la logique.
9. Backend Supabase (ADR-0011) : RLS sur toutes les tables, écritures sensibles via des RPC
   SECURITY DEFINER (search_path fixé, EXECUTE retiré à PUBLIC et anon), service_role
   uniquement dans les Edge Functions, supabase/tests/security.sql à chaque migration.
10. Consentement parental (ADR-0013) : un mineur de 12–15 ans sans lien confirmé n'a
   que le SOS manuel ; le parent n'obtient jamais d'accès de surveillance.
11. Contexte : lancement en Haïti (+509, 114/115/116, Digicel et Natcom), utilisateurs
   dès 12 ans, langues FR/HT/EN, téléphones d'entrée de gamme sous Android 10.
12. À la fin : ce qui est fait, exigences couvertes, tests lancés (avec leur résultat
    réel), ce qui reste, questions ouvertes.

Branche : sprint/S<n>-<sujet>. Commits atomiques en français.
```

## Gabarit de sprint

```
Sprint S<n> — <thème>. Lis docs/plan/sprints-mvp.md, section « Sprint <n> ».
1. Vérifie les prérequis (questions tranchées, sprints précédents terminés).
2. Pose-moi les questions qui manquent, une par une.
3. Propose un découpage en tâches ordonnées (/planning-and-task-breakdown) avec, pour
   chacune : fichiers touchés, exigences R-…, tests prévus, risques.
Attends ma validation. Ensuite, une tâche à la fois (/incremental-implementation),
avec un commit et un résumé à chaque fin de tâche. Termine par la démo du sprint.
```

---

## Prompt 1 — Architecture (Sprint 0 et début du Sprint 1)

> Ton prompt demandait de **concevoir** l'architecture. Elle existe déjà dans `docs/`. Le prompt amélioré demande de la **contester et de la compléter**, sinon Claude en produirait une deuxième, incohérente avec la première.

```
Relis l'architecture existante (docs/modules/README.md et les 12 fiches modules,
docs/01-etats-et-escalade.md, docs/securite/, docs/conformite/). Application Android
Kotlin de sécurité personnelle : IA locale d'analyse audio activée uniquement par
l'utilisateur, service de premier plan microphone, détection de déviation d'itinéraire,
alerte graduée, enregistrement chiffré, hors ligne d'abord.

Produis :
0. Le schéma Supabase (tables, RLS, RPC, Edge Functions, pg_cron) confronté à
   docs/modules/backend-api.md.
1. Une critique : incohérences, manques, sur-ingénierie (/doubt-driven-development,
   /ponytail), et ce qui est impossible ou risqué sur Android 10 à 15 et Google Play.
2. Le graphe de dépendances des modules Gradle et le diagramme de flux
   détection → décision → alerte → preuves → synchronisation (Mermaid).
3. Le schéma Room (entités, index, migrations, ce qui est chiffré et avec quelle clé).
4. Le manifeste des permissions final, avec pour chacune la justification et le
   comportement en cas de refus.
5. Les risques de confidentialité restants, classés.

Contraintes : backend Supabase (ADR-0011) ; aucun envoi d'audio brut en continu vers un serveur ; pas
d'ACCESS_BACKGROUND_LOCATION ; aucun SDK publicitaire ou analytics tiers ;
compatible avec la politique Play « Familles » (utilisateurs dès 12 ans) ; consentement parental
selon docs/produit/11-consentement-parental.md.
Ne code rien. Pose tes questions, puis propose les modifications de docs/ en ADR.
```

## Prompt 2 — Moteur audio (Sprint 5)

```
Implémente le module :audio-engine selon docs/modules/audio-engine.md,
docs/ia/modele-ia-locale.md et docs/produit/03-ecoute-ia-locale.md.

Périmètre MVP (ADR-0010) : capture AudioRecord (VOICE_RECOGNITION, 16 kHz, mono,
PCM 16 bits, blocs de 20 ms) dans le ProtectionService de type microphone, tampon
circulaire en mémoire, VAD, détection de mots-clés FR et créole (LiteRT int8),
phrase secrète par enrôlement (empreinte chiffrée, aucun audio conservé).
La classification audio (YAMNet : cris, chocs, vitres, alarmes) est intégrée
derrière un flag DÉSACTIVÉ par défaut. La détresse vocale est hors MVP.

Exigences : R-AUD-01..10, R-AE-01..05. L'audio ne quitte jamais l'appareil et
n'est jamais écrit sur disque hors d'une alerte de niveau ≥ 2.

Implémente : pipeline temps réel sur un thread dédié ; porte d'énergie + VAD avant
les modèles lourds (batterie) ; lissage des prédictions (moyenne glissante ou
hystérésis) ; sorties en probabilités calibrées avec la version du modèle ; mode
dégradé en cas de surchauffe ou de batterie < 10 % ; pause si le micro est pris par
un appel. Les seuils NE sont PAS appliqués ici (c'est alert-engine).

Tests : unitaires (pipeline, lissage, tampon), banc tools/audio-eval/ (bruit,
musique, conversation, cri simulé, ambiances d'Haïti) avec rappel et faux
positifs par heure, test instrumenté « aucun audio ne sort » (réseau et fichiers),
arrêt du micro < 500 ms. Explique chaque choix et mesure-le sur l'appareil
d'entrée de gamme. Pose tes questions avant de coder.
```

## Prompt 3 — Détection de déviation (Sprint 4)

```
Implémente :location-engine selon docs/modules/location-engine.md et
docs/produit/04-deviation-itineraire.md.

L'utilisateur déclare un départ, une destination, une heure d'arrivée et un mode
de transport (marche, voiture, bus/tap-tap, taxi, moto-taxi). Suivi UNIQUEMENT
pendant le trajet actif, via le service de premier plan de type location, sans
ACCESS_BACKGROUND_LOCATION (ADR-0006). Fused Location avec repli sur LocationManager.

Algorithmes purs (core, testés en JVM) : corridor local sans fournisseur de cartes,
écart au trajet, arrêt anormal, direction opposée, retour au départ, retard, perte
de signal (le battement est côté serveur), zones non sûres locales. Utilise les
seuils par mode du module 4 comme valeurs initiales configurables ; filtre les
positions imprécises (> 100 m) ; tolère embouteillages et arrêts prévus.

Règle : un événement de trajet produit au maximum une VÉRIFICATION (niveau 1),
jamais une alerte directe (R-TRIP-04). Exigences R-TRIP-*, R-LE-*.
Tests : rejeu de traces GPX (normal, embouteillage, détour, demi-tour, GPS
imprécis, perte de signal) avec les événements attendus. Pose tes questions avant.
```

## Prompt 4 — Système d'alerte (Sprint 1 pour la logique, Sprint 2 pour le branchement)

> Tes états « détection, vérification, alerte proches, alerte élargie, urgence, résolution » correspondent à la spécification : niveau 0, `ALERT_PENDING` (niveau 1), `ALERT_SENT` (niveaux 2 et 3), `EMERGENCY_MODE`, puis clôture. Le prompt utilise ces noms pour éviter deux vocabulaires.

```
Implémente :alert-engine:logic selon docs/01-etats-et-escalade.md et
docs/modules/alert-engine.md. Kotlin pur, sans Android.

reduce(state, event, policy, now) → (newState, effects), déterministe.
États : OFF, ARMED_AUDIO_ONLY, ARMED_FULL, TRIP_ACTIVE, ALERT_PENDING (vérification),
ALERT_SENT (proches, puis élargie), EMERGENCY_MODE, plus la clôture (USER_SAFE,
FALSE_ALARM, EXPIRED). Niveaux 0 à 3 dans le MVP ; niveau 4 = composeur seulement,
jamais d'appel automatique aux secours (R-ESC-02) ; pas d'appel automatisé (V2).

Chaque niveau a des délais, des canaux et des conditions configurables dans
AlertPolicy, avec des bornes (R-ESC-04). Anti-faux positifs : deux signaux
indépendants, hystérésis, période réfractaire, contextes à ignorer, score
Risk = w1A + w2B + w3C + w4D + w5E avec les garde-fous R-RISK-01..03.
« Je vais bien » et désarmement : PIN ou biométrie requis s'ils existent (R-SM-04) ;
PIN obligatoire pour démarrer un trajet ; pas de PIN de contrainte en MVP (ADR-0015).
Tout est journalisé dans le journal local chiffré et chaîné
(R-SM-02, R-AL-06), avec les composantes du score.

TDD : écris d'abord la table de toutes les transitions et les tests de propriétés
(jamais de niveau 4 sans consentement, jamais de position partagée en OFF, SOS
toujours possible). Horloge injectée. Pose tes questions avant.
```

## Prompt 5 — Preuves chiffrées (Sprint 6)

```
Implémente :evidence-engine selon docs/modules/evidence-engine.md,
docs/produit/06-preuves.md et docs/securite/cryptographie.md.

Enregistre de l'audio UNIQUEMENT après une alerte de niveau ≥ 2 ou un SOS (la
vidéo est en V2). Opus 24 kbit/s, segments de 30 s, pré-tampon de 5 s.
Chiffrement AES-256-GCM en flux avec Tink StreamingAead (AES256_GCM_HKDF_1MB),
une clé de données par alerte enveloppée par une clé de l'Android Keystore.
Ne pas utiliser androidx.security:security-crypto (déprécié). AUCUN fichier en
clair, même temporaire (MediaCodec → Ogg → flux chiffrant).

Chaîne d'intégrité SHA-256, racine signée et envoyée au serveur. Stockage interne
privé, exclu des sauvegardes. File d'attente hors ligne (sync-engine) : position >
métadonnées > audio, morceaux reprenables, envoi automatique au retour du réseau.
Rétention choisie à l'onboarding (24 h, 7 j, 30 j, à la clôture ; 24 h si rien n'est choisi), purge par
crypto-shredding (WorkManager toutes les heures + au démarrage), même règle côté
serveur. Coffre-fort par biométrie ou PIN ; export chiffré.

Exigences : R-EVD-*, R-EE-*. Tests : aucun fichier en clair pendant
l'enregistrement, altération détectée, purge à l'heure exacte (horloge virtuelle),
reprise après coupure, wipe < 2 s. Revue /security-and-hardening de la
conception AVANT de coder. Pose tes questions avant.
```

## Prompt 6 — Vie privée (Sprint 0, puis Sprint 7 et Sprint 8)

```
Révise docs/conformite/vie-privee-donnees.md et android-permissions-play.md pour
un lancement en Haïti avec des utilisateurs dès 12 ans, et une diaspora possible
dans l'UE et aux États-Unis. Référentiel : le RGPD comme standard de qualité (même
si Haïti n'est pas dans l'UE), la loi haïtienne applicable (à identifier), la
politique Google Play (Sécurité des données, Familles, SMS/Call Log, types de
service de premier plan, stalkerware), COPPA pour les mineurs aux États-Unis.

Pour chaque donnée : finalité, base légale, durée de conservation (téléphone et
serveur), destinataires et sous-traitants, transferts hors du pays, permission
Android, risque et mesure. Vérifie que le code et le manifeste actuels respectent
ce tableau (audit) et liste les écarts.

Propose une version privacy-by-design : consentements séparés et révocables,
consentement parental pour les 12–15 ans sans surveillance parentale permanente,
minimisation, chiffrement, journal local, suppression de compte effective, absence
de vente ou de partage de données, aucun SDK publicitaire ou analytics tiers.
Produis aussi le brouillon de la section « Sécurité des données » de Play et une
politique de confidentialité lisible en FR, HT et EN. Signale tout ce qui exige un
juriste. Ne prétends pas donner un avis juridique.
```

## Prompts complémentaires ➕

| Sprint | Prompt |
|---|---|
| S0 | « Sprint 0 — Cadrage. Pas de code. Interviewe-moi (/interview-me) pour trancher une à une les questions 🔴 restantes de docs/questions-ouvertes.md, puis rédige les ADR et mets à jour docs/. Ensuite, prototypes jetables dans spikes/ selon les étapes 0.17 à 0.20, avec un rapport de mesures par téléphone. » |
| S1 | « Sprint 1 — Socle. Suis les blocs A à E de la section Sprint 1 : Gradle et CI, machine à états en TDD (prompt 4), stockage chiffré et PIN, socle Supabase (migration, RLS, RPC de consentement parental, security.sql), puis inscription OTP et parcours jeune et parent. Démo : un jeune de 13 ans débloqué par son parent depuis un autre téléphone. » |
| S2 | « Sprint 2 — Alertes locales. Objectif démontrable : en mode avion, un SOS envoie un SMS avec la position aux contacts. Suis la section Sprint 2. » |
| S3 | « Sprint 3 — Alertes via Supabase. Commence par la migration (tables, RLS, CHECK) et les signatures des RPC de docs/modules/backend-api.md ; fais-les-moi valider ; écris security.sql AVANT les RPC ; puis les Edge Functions notify-alert et track, la page des proches et l'intégration mobile. » |
| S7 | « Sprint 7 — Sécurité. Lance les agents security-auditor et code-reviewer sur tout le dépôt, compare au modèle de menace T1–T14, puis corrige par priorité. » |
| S8 | « Sprint 8 — Tests et lancement. Produis la grille de test terrain (20 à 50 téléphones), les scripts de tests réseau faible et mode avion, le protocole de mesure batterie et de faux positifs, puis le dossier Play complet. » |

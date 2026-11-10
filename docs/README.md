# Cercle Rouge — Documentation de conception

> Nom provisoire. Statut : **spécification, aucun code écrit**. Dernière révision : 2026-11-10.

Cercle Rouge protège une personne pendant un trajet, une sortie, une situation à risque ou une urgence. L'application combine une détection audio **locale**, les capteurs du téléphone, une géolocalisation **temporaire**, l'alerte des proches et la conservation de preuves **chiffrées**.

Cercle Rouge **ne prédit pas** un enlèvement ou une agression. Elle détecte des *signaux de danger*, demande une *confirmation rapide* puis alerte selon les *règles choisies par l'utilisateur*.

## Comment lire cette documentation

| Ordre | Document | Contenu |
|---|---|---|
| 1 | [00-vision-principes.md](00-vision-principes.md) | Mission, principes non négociables, ce que l'app ne fait jamais |
| 2 | [01-etats-et-escalade.md](01-etats-et-escalade.md) | Machine à états globale et niveaux d'alerte 0 à 4 (cœur du produit) |
| 3 | [produit/](produit/) | Spécification fonctionnelle des 10 modules produit |
| 4 | [modules/](modules/) | Spécification technique des 12 modules logiciels |
| 5 | [ia/modele-ia-locale.md](ia/modele-ia-locale.md) | Pipeline audio, modèles, score de risque, anti-faux positifs |
| 6 | [securite/](securite/) | Modèle de menace, cryptographie, anti-manipulation |
| 7 | [conformite/](conformite/) | Permissions Android, règles Google Play, vie privée et données |
| 8 | [decisions/](decisions/) | Décisions d'architecture (ADR) |
| 9 | [questions-ouvertes.md](questions-ouvertes.md) | Points à trancher avant d'implémenter |
| 10 | [glossaire.md](glossaire.md) | Vocabulaire commun |
| 11 | [plan/](plan/README.md) | Plan de développement sprint par sprint et prompts Claude Code |

### Modules produit (`produit/`)

1. [Onboarding et consentement](produit/01-onboarding-consentement.md)
2. [Écran principal : mode protection](produit/02-ecran-principal.md)
3. [Moteur d'écoute IA locale](produit/03-ecoute-ia-locale.md)
4. [Détection de déviation d'itinéraire](produit/04-deviation-itineraire.md)
5. [Système d'alerte graduée](produit/05-alerte-graduee.md)
6. [Enregistrement de preuves](produit/06-preuves.md)
7. [Contacts de confiance](produit/07-contacts-confiance.md)
8. [Réseau, hors-ligne et résilience](produit/08-reseau-hors-ligne.md)
9. [Sécurité et anti-manipulation](produit/09-anti-manipulation.md)
10. [Tableau de bord des proches](produit/10-tableau-de-bord-proches.md)
11. [Consentement parental (12–15 ans)](produit/11-consentement-parental.md)

### Modules logiciels (`modules/`)

| Module | Responsabilité |
|---|---|
| [core-domain](modules/core-domain.md) | Entités : utilisateur, contact, trajet, alerte, preuve, événement |
| [audio-engine](modules/audio-engine.md) | Capture, VAD, classification, mots-clés, scores audio |
| [sensor-engine](modules/sensor-engine.md) | Accéléromètre, gyroscope, batterie, connectivité, mouvement |
| [location-engine](modules/location-engine.md) | Position, trajet, géofencing, déviation, dernière position connue |
| [alert-engine](modules/alert-engine.md) | Machine à états, fusion du risque, escalade, canaux, anti-faux positifs |
| [evidence-engine](modules/evidence-engine.md) | Enregistrement, chiffrement, rétention, envoi différé |
| [contacts-engine](modules/contacts-engine.md) | Contacts, groupes, consentement, ordre d'alerte |
| [sync-engine](modules/sync-engine.md) | Synchronisation sécurisée, file d'attente, reprise après panne |
| [privacy-engine](modules/privacy-engine.md) | Permissions, consentements, journalisation, suppression des données |
| [ui-mobile](modules/ui-mobile.md) | Écrans, widgets, notifications, mode urgence |
| [backend-api](modules/backend-api.md) | Supabase : comptes, alertes, liens de suivi, canaux de notification |
| [admin-console](modules/admin-console.md) | Modération, incidents, statistiques anonymisées, support |

## Conventions de ce dossier

- **Identifiants d'exigence** : `R-<MODULE>-<n°>` (ex. `R-AUD-04`). Chaque exigence doit être vérifiable par un test, une revue ou une mesure. Le code et les tests y feront référence.
- **Niveaux** : **DOIT** = obligatoire ; **DEVRAIT** = attendu sauf raison documentée ; **PEUT** = optionnel.
- **⚠ Contrainte plateforme** : une limite d'Android ou de Google Play qui change la spécification initiale. Ces points sont détaillés dans [conformite/android-permissions-play.md](conformite/android-permissions-play.md).
- **❓ Question ouverte** : un point à trancher, renvoyé vers [questions-ouvertes.md](questions-ouvertes.md).

## Décisions déjà prises

| Sujet | Décision | ADR |
|---|---|---|
| Plateforme | Android natif d'abord, iOS en phase 2 | [ADR-0001](decisions/ADR-0001-android-natif-kotlin-compose.md) |
| UI | Kotlin + Jetpack Compose | ADR-0001 |
| Version minimale | minSdk 29 (Android 10) | [ADR-0002](decisions/ADR-0002-minsdk-29.md) |
| Identifiant | `com.cerclerouge.app` | ADR-0001 |
| IA embarquée | LiteRT (TensorFlow Lite) | [ADR-0003](decisions/ADR-0003-ia-locale-litert.md) |
| Langues | Français (par défaut), créole haïtien, anglais | [ADR-0004](decisions/ADR-0004-langues-fr-ht-en.md) |
| Canaux SOS | SMS automatique, appel, WhatsApp (+ notification, e-mail) | [ADR-0005](decisions/ADR-0005-canaux-alerte.md) |
| Localisation en arrière-plan | Service de premier plan « location » pendant un trajet, sans `ACCESS_BACKGROUND_LOCATION` | [ADR-0006](decisions/ADR-0006-localisation-sans-background-location.md) |
| Chiffrement local | Android Keystore + Tink (AES-256-GCM, streaming) | [ADR-0007](decisions/ADR-0007-chiffrement-local.md) |
| Score de risque | `Risk = w₁A + w₂B + w₃C + w₄D + w₅E` | [ADR-0008](decisions/ADR-0008-formule-score-risque.md) |
| Lancement et âge | Haïti ; 12 ans minimum | [ADR-0009](decisions/ADR-0009-haiti-age-minimum-12.md) |
| Backend | Supabase | [ADR-0011](decisions/ADR-0011-backend-supabase.md) |
| Architecture UI | MVVM + flux unidirectionnel | [ADR-0012](decisions/ADR-0012-mvvm-udf.md) |
| Consentement parental | Confirmation dans l'app d'un adulte référent inscrit (parent ou adulte de confiance), ajouté par défaut comme contact | [ADR-0013](decisions/ADR-0013-consentement-parental.md) |
| SMS, région, TLS | Vonage ; `us-east-1` ; épinglage prudent | [ADR-0014](decisions/ADR-0014-epinglage-tls.md) |
| Défauts et PIN | Désactivé + SOS ; SOS manuel vers les contacts non consentis ; PIN obligatoire en trajet ; biométrie | [ADR-0015](decisions/ADR-0015-defauts-et-pin.md) |
| Preuves partagées | Chiffrement de bout en bout, app obligatoire pour écouter | [ADR-0016](decisions/ADR-0016-preuves-chiffrement-bout-en-bout.md) |
| Carte d'accueil | MapLibre + OpenFreeMap ; point de position seulement protection active | [ADR-0018](decisions/ADR-0018-carte-accueil-maplibre-openfreemap.md) |
| Nom, leurre, contacts, conservation | « Cercle Rouge » fixe ; leurre en V2 ; 5 contacts dont 3 alertés ; conservation choisie à l'onboarding | [ADR-0017](decisions/ADR-0017-nom-leurre-contacts-retention.md) |
| Périmètre MVP | SOS, contacts, push et SMS, trajet, audio chiffré après alerte, mots-clés, vérification, hors ligne, page des proches | [ADR-0010](decisions/ADR-0010-perimetre-mvp.md) |

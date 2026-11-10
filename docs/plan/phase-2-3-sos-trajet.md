# Phases 2 et 3 — MVP SOS et trajet protégé (S4 à S7)

---

## S4 — SOS local : contacts, service de protection, SMS et appel (semaines 9–10)

**Objectif** : **sans serveur et sans Internet**, un appui sur SOS envoie un SMS avec la position aux contacts et appelle le contact n° 1.

**Prérequis** : S3 ; S1.3 validé.

| # | Tâche | Détail | Réf. |
|---|---|---|---|
| S4.1 | `contacts-engine` | CRUD chiffré ; sélecteur système `ACTION_PICK` ; normalisation E.164 (libphonenumber, préfixe SIM +509) ; groupes, rôles (1 PRIMARY), rang, canaux, langue ; écran Contacts (glisser-déposer) | R-CON-02/07, R-CE-03 |
| S4.2 | Résolution des destinataires | `resolveRecipients` pur + tests (niveaux 2 et 3, sans données → GSM d'abord, 2 canaux indépendants) | R-CE-01, R-ALR-01 |
| S4.3 | `ProtectionService` | Service de premier plan unique ; types ajustés selon l'état ; notification persistante avec [SOS] [Désactiver] ; persistance de l'état avant les effets ; restauration après la mort du processus ; notification « Réarmer » après un redémarrage | R-ARCH-04, R-SM-01/08 |
| S4.4 | Exécution des effets | Adaptateur `alert-engine:android` : vibrations codées, notifications (canaux armé, vérification, alerte), minuteries (coroutine + `setExactAndAllowWhileIdle`) | R-AL-02/03 |
| S4.5 | `sync-engine` v1 (local) | File persistante Room ; jobs idempotents ; canal SMS direct (multipart, rapports) ; canal appel (`ACTION_CALL` proche) ; composeur pour les secours ; SMS pré-rempli si `SEND_SMS` est refusée | R-NET-01/02, R-SY-01, R-ALR-06 |
| S4.6 | Gabarits de messages | FR, HT, EN ; version SMS courte avec coordonnées brutes ; marqueur « TEST » | R-ALR-05/07, module 5 |
| S4.7 | Surfaces SOS | Bouton SOS (appui long 1,5 s ou glissement, compte à rebours d'annulation de 5 s), widget Glance, tuile de réglages rapides, action dans la notification | R-HOME-01, R-SM-05 |
| S4.8 | Position ponctuelle | Dernière position connue + une position fraîche (timeout 10 s) pour le message SOS ; « position non partagée » si refusée | R-LE-01 |
| S4.9 | Test d'alerte | Envoi « TEST » par le même chemin ; limite de 3 par jour et par contact | R-CON-06 |
| S4.10 | Écran principal v1 | États, SOS, contacts joignables, appel secours par pays | R-HOME-02/05/07 |

**Tests** : scénarios JVM (SOS hors ligne → SMS + appel) ; test instrumenté du service (survie écran éteint 30 min) ; test manuel en **mode avion avec SMS seul**.
**Démo** : téléphone sans données mobiles → SOS depuis le widget → le proche reçoit le SMS avec la position en < 15 s et l'appel sonne.
**Skills** : `/incremental-implementation`, `/frontend-ui-engineering`, `/security-and-hardening`.

---

## S5 — Backend v0, consentement des proches et lien de suivi (semaines 11–12)

**Objectif** : l'alerte part aussi par le serveur (push, SMS serveur), le proche **consent** et suit l'alerte sur une page web.

**Prérequis** : Q10, Q12, Q13 tranchées ; comptes S0.4.

| # | Tâche | Détail | Réf. |
|---|---|---|---|
| S5.1 | Socle serveur | Dépôt `server/` ; Go (ou Node) ; PostgreSQL, Redis ; migrations ; configuration par environnement ; gestionnaire de secrets ; CI (tests, lint, scan) ; déploiement de staging | R-BE-04 |
| S5.2 | Identité de l'appareil | `/devices` : enregistrement par clé publique K_device ; requêtes signées (horodatage + nonce) ; rotation et révocation ; ❓ Play Integrity | R-SY-02 |
| S5.3 | Contacts et consentement | `/contacts` (numéros chiffrés au niveau applicatif) ; invitation SMS ; page d'acceptation (langue, canaux) ; STOP et retrait en < 1 min | R-CON-03/04, module 7 |
| S5.4 | Alertes | `/alerts` idempotent (`Idempotency-Key`) ; création, mise à jour, clôture ; déduplication avec les SMS directs | R-BE-01, R-NET-02/03 |
| S5.5 | Canaux serveur | FCM (réveil seul, sans données), passerelle SMS, e-mail ; limites de débit ; métriques de livraison | R-SY-03, R-BE-05/07 |
| S5.6 | Lien de suivi v0 | `/track/{token}` : jeton ≥ 128 bits, TTL 2 à 6 h, révocation ; page légère (< 200 Ko) : statut, dernière position, heure, [Appeler] [Secours] ; CSP, `noindex`, `no-referrer` | R-DASH-01/02/04/06 |
| S5.7 | `sync-engine` v2 | Client HTTPS + épinglage ; basculement primaire → secondaire ; SMS de secours si pas d'accusé de réception en 20 s | R-NET-07, R-SY-05 |
| S5.8 | Purges serveur | Tâches de rétention + tests d'intégration | R-BE-03 |

**Tests** : contrats d'API (OpenAPI) ; tests d'intégration avec une base réelle (conteneur) ; client mobile contre MockWebServer (coupures, 500, doublons).
**Démo** : SOS avec données → le proche reçoit le SMS du serveur et la notification, ouvre le lien et voit la position ; SOS sans données → SMS direct seulement, sans doublon quand le réseau revient.
**Jalon** : **bêta interne** possible (5 à 10 personnes de confiance).
**Skills** : `/api-and-interface-design`, `/security-and-hardening`, `/ci-cd-and-automation`, `/observability-and-instrumentation`.

---

## S6 — Trajet protégé et détection de déviation (semaines 13–14)

**Objectif** : l'utilisateur déclare un trajet ; l'application détecte arrêt, détour, demi-tour et retard, et demande une vérification.

**Prérequis** : S4 ; S1.4.

| # | Tâche | Détail | Réf. |
|---|---|---|---|
| S6.1 | `location-engine` | Profils EMERGENCY, TRIP, ARMED ; Fused Location + repli `LocationManager` ; filtrage de précision ; position approximative en mode dégradé | R-LE-01/02, R-TRIP-03 |
| S6.2 | Algorithmes de trajet (purs) | Corridor ; distance point-segment ; arrêt prolongé ; direction opposée ; retour au départ ; retard ; zones non sûres (cercle, polygone) ; seuils par mode de transport | module 4, R-TRIP-07 |
| S6.3 | Rejeu GPX | Banc de tests avec des trajets enregistrés en S1.4 (normal, embouteillage, détour, demi-tour, perte de GPS) | tests location |
| S6.4 | Écran Trajet | Modèles (« Je rentre », « Rendez-vous », « Transport ») ; destination (saisie, favoris locaux) ; heure prévue ; mode ; arrêts prévus ; contacts qui suivent ; « Je fais un arrêt » ; « Je suis arrivé » (PIN) | R-HOME, module 4 |
| S6.5 | Vérification (niveau 1) | Notification « Es-tu en sécurité ? » + vibration ; délai 10, 20 ou 30 s ; [Je vais bien] (PIN), [J'ai besoin d'aide] | R-ESC-04, module 5 |
| S6.6 | Suppression des traces | Fin de trajet : suppression des points ; résumé conservé | R-TRIP-06, R-LE-04 |

**Démo** : trajet réel → détour volontaire de 500 m à pied → vérification → pas de réponse → alerte niveau 2 aux proches avec la position.
**Skills** : `/test-driven-development`, `/performance-optimization`.

---

## S7 — Escalade complète, perte de contact et tableau de bord des proches (semaines 15–16)

**Objectif** : escalade 1 → 2 → 3 complète ; le serveur détecte la perte de contact ; les proches agissent en temps réel.

**Prérequis** : S5, S6.

| # | Tâche | Détail | Réf. |
|---|---|---|---|
| S7.1 | Escalade 2 → 3 | Minuterie (5 min par défaut) ; appel du rang 1 puis du rang 2 après 30 s ; SMS à tous ; notifications répétées ; « J'ai pris contact » arrête l'escalade | R-ESC-03/04, R-CE résolution |
| S7.2 | Niveau 4 | Consentement préalable séparé ; écran « Appeler les secours » avec un résumé à lire ; composeur | R-ESC-02, R-AL-04 |
| S7.3 | Battement et perte de contact | Battement toutes les 2 min (opt-in) ; minuterie Redis ; alerte niveau 2 envoyée par le serveur ; annulation au retour ; dernier message à l'extinction (`ACTION_SHUTDOWN`) | R-TRIP-05, R-NET-05, R-BE-08 |
| S7.4 | Tableau de bord des proches | WebSocket (repli en polling) ; carte légère (tuiles ❓ OSM ou MapLibre) ; chronologie ; actions « J'ai pris contact », « Alerte fausse » (sans clôture), « Transmettre aux secours » ; journal des consultations visible par l'utilisateur ; ❓ code SMS séparé | R-DASH-03/05, module 10 |
| S7.5 | Clôture | « Je vais bien », fausse alerte, expiration de 24 h → message de clôture à tous les proches alertés | R-ALR-03, R-ESC-06 |
| S7.6 | Notification de désactivation | Désarmer pendant un trajet ou une alerte → message aux proches | R-SM-06, R-SEC-03 |
| S7.7 | Historique | Écran des alertes et trajets (dans la limite de la rétention) | R-HOME |

**Démo** : trajet → téléphone éteint de force → 10 min après, les proches reçoivent « perte de contact » avec la dernière position ; un proche clique « J'ai pris contact » et les autres le voient.
**Jalon** : **bêta fermée « SOS + trajet »** (20 à 50 personnes), utile même sans IA.
**Skills** : `/api-and-interface-design`, `/frontend-ui-engineering` (page web), `/web-performance-auditor` (agent), `/observability-and-instrumentation`.

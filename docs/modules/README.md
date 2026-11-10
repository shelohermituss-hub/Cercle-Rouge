# Architecture et modules logiciels

## Vue d'ensemble

| Couche | Technologie | Remarque |
|---|---|---|
| Android | Kotlin, Jetpack Compose, Coroutines/Flow, Hilt, Room + SQLCipher, WorkManager, DataStore | minSdk 29, targetSdk = dernière API exigée par Play |
| IA embarquée | LiteRT (TFLite) ; ❓ ONNX Runtime pour un VAD Silero si besoin | ADR-0003 |
| Crypto locale | Android Keystore (StrongBox si disponible) + Google Tink | ADR-0007 |
| iOS | Swift + SwiftUI | Phase 2 ; `core-domain` reste portable (❓ Kotlin Multiplatform) |
| Backend | ❓ Node.js (TypeScript) ou Go ; PostgreSQL, Redis, stockage objet chiffré | Voir [backend-api](backend-api.md) |
| Temps réel | WebSocket (suivi d'urgence) | Repli en polling |
| Notifications | Firebase Cloud Messaging + SMS de secours | FCM ne transporte **aucune** donnée sensible : seulement un « réveil » |
| Cartes | ❓ Mapbox ou Google Maps SDK ; V1 sans fournisseur (corridor) | [Module 4](../produit/04-deviation-itineraire.md) |

## Modules Gradle (Android)

| Module | Type | Dépend de |
|---|---|---|
| `:core-domain` | Kotlin pur (JVM, sans Android) | — |
| `:audio-engine` | Android library | core-domain |
| `:sensor-engine` | Android library | core-domain |
| `:location-engine` | Android library | core-domain |
| `:alert-engine` | Kotlin pur (logique) + adaptateur Android séparé | core-domain ; **interfaces** des moteurs |
| `:evidence-engine` | Android library | core-domain, privacy-engine (clés) |
| `:contacts-engine` | Android library | core-domain |
| `:sync-engine` | Android library | core-domain |
| `:privacy-engine` | Android library | core-domain |
| `:ui-mobile` (`:app`) | Application | tous |

Les modules serveur (`backend-api`, `admin-console`) vivent dans `server/` et `admin/`, dans le même dépôt (❓ ou des dépôts séparés).

### Règles de dépendance

| ID | Règle |
|---|---|
| R-ARCH-01 | **DOIT** : `core-domain` n'importe rien d'Android ni de réseau. Toute la logique de décision (machine à états, fusion du risque, escalade, règles de trajet) y est, ou dans la partie pure d'`alert-engine`, testable sur JVM. |
| R-ARCH-02 | **DOIT** : les moteurs ne se connaissent pas entre eux. Ils publient des **événements de domaine** (`Flow<DomainEvent>`) consommés par `alert-engine`. |
| R-ARCH-03 | **DOIT** : seul `ui-mobile` connaît Compose. Les moteurs exposent des `StateFlow` et des commandes. |
| R-ARCH-04 | **DOIT** : un seul **service de premier plan** (`ProtectionService`) héberge les moteurs actifs. Ses types (`microphone`, `location`, `camera`) sont ajustés selon l'état. Pas de multiplication de services. |
| R-ARCH-05 | **DOIT** : les horloges (UTC, monotone) sont injectées (`Clock`), pour des tests déterministes. |
| R-ARCH-06 | **DOIT** : aucune dépendance tierce n'est ajoutée sans revue (licence, maintenance, permissions, traceurs). Aucun SDK publicitaire ni analytics tiers dans l'application. |

## Flux principal : détection → alerte

```
audio-engine ──AudioSignal──┐
sensor-engine ─SensorSignal─┼──► alert-engine ──► RiskScore ──► StateMachine ──► Effets
location-engine TripEvent ──┘          ▲                                    │
                                       │                                    ├─► ui-mobile (vibration, notif.)
                          privacy-engine (politiques,                       ├─► evidence-engine (enregistrer)
                          consentements, contexte)                          ├─► contacts-engine (destinataires)
                                                                            └─► sync-engine (file → canaux)
```

## Fiches modules

Chaque fiche suit le même plan : Responsabilité · Hors périmètre · Interface publique · Données · Cycle de vie · Règles · Tests.

- [core-domain](core-domain.md)
- [audio-engine](audio-engine.md)
- [sensor-engine](sensor-engine.md)
- [location-engine](location-engine.md)
- [alert-engine](alert-engine.md)
- [evidence-engine](evidence-engine.md)
- [contacts-engine](contacts-engine.md)
- [sync-engine](sync-engine.md)
- [privacy-engine](privacy-engine.md)
- [ui-mobile](ui-mobile.md)
- [backend-api](backend-api.md)
- [admin-console](admin-console.md)

## Qualité transversale

| Sujet | Exigence |
|---|---|
| Tests | Unitaires JVM pour toute la logique (couverture ≥ 90 % de `core-domain` et de la partie pure d'`alert-engine`) ; tests instrumentés pour les services, permissions et Room ; tests de bout en bout des scénarios d'alerte ; tests de non-régression des modèles (jeu audio de référence) |
| Analyse statique | ktlint ou detekt, Android Lint en erreur, pas de `!!` dans le code de production |
| CI | Build, tests, lint, vérification des clés i18n, scan des dépendances (OWASP Dependency-Check ou équivalent), vérification qu'aucun secret n'est commité |
| Build | R8 activé, `debuggable=false`, signature Play App Signing, aucun log sensible en release |
| Observabilité | Journal **local** pour l'utilisateur ; côté serveur : métriques techniques sans contenu (taux de livraison, latence). ❓ Crash reporting sans données personnelles (ex. ACRA auto-hébergé) |

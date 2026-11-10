# ADR-0001 — Android natif, Kotlin et Jetpack Compose

- Statut : **Acceptée** · Date : 2026-11-10

## Contexte
L'application dépend fortement des services de premier plan, de l'audio bas niveau (`AudioRecord`), de LiteRT, du Keystore et des capteurs. iOS est prévu en phase 2.

## Décision
- Android natif en Kotlin, UI en Jetpack Compose, une activité unique.
- Identifiant d'application et package racine : `com.cerclerouge.app`.
- DI Hilt ; Coroutines/Flow ; Room + SQLCipher ; WorkManager.
- Modules Gradle selon [modules/README.md](../modules/README.md) ; `core-domain` en Kotlin pur pour un futur partage avec iOS (❓ Kotlin Multiplatform, à décider en phase 2).

## Conséquences
+ Accès direct et fiable aux API système critiques ; performances sur l'entrée de gamme.
− Deux bases de code UI à terme (Compose, SwiftUI).

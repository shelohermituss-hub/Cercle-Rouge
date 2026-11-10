# ui-mobile

## Responsabilité

Écrans Compose, widget, tuile de réglages rapides, notifications, mode urgence, mode discret, mode leurre, onboarding, vue « proche ». Il **affiche** l'état et **émet des intentions** ; il ne décide de rien.

## Surfaces

| Surface | Contenu | Remarques Android |
|---|---|---|
| Application | Onboarding, écran principal, trajet, réglages, coffre-fort, historique, contacts, vue proche | Navigation Compose ; une activité unique |
| Notification persistante | État + actions [SOS] [Désactiver] [Je vais bien] | Obligatoire pour le service de premier plan ; canal dédié, importance basse en armé, haute en alerte |
| Notification de vérification | « Es-tu en sécurité ? » [Je vais bien] [J'ai besoin d'aide] | Canal « vérification » avec vibration personnalisée ; ⚠ pas de `USE_FULL_SCREEN_INTENT` (réservé aux appels et alarmes depuis Android 14) |
| Widget | Bouton SOS + état + armer/désarmer | Glance ; ⚠ armer l'écoute depuis le widget est une action utilisateur visible, donc autorisée à démarrer le service micro |
| Tuile de réglages rapides | Activer/désactiver la protection ; appui long → app | `TileService` |
| Mode leurre | Écran neutre plein écran | `FLAG_SECURE` |

## Exigences UX et accessibilité

| ID | Règle |
|---|---|
| R-UI-01 | **DOIT** : WCAG 2.2 AA ; TalkBack ; tailles de police jusqu'à 200 % sans troncature des actions critiques. |
| R-UI-02 | **DOIT** : cibles tactiles ≥ 48 dp (≥ 72 dp pour le SOS et l'activation). |
| R-UI-03 | **DOIT** : textes en FR, HT et EN via les ressources Android (`values`, `values-ht`, `values-en`) ; aucun texte en dur ; une vérification CI des clés manquantes. |
| R-UI-04 | **DOIT** : thème clair et sombre ; l'état n'est jamais transmis par la couleur seule. |
| R-UI-05 | **DOIT** : les écrans critiques (SOS, vérification) fonctionnent hors ligne et sans animation bloquante ; les animations respectent le réglage « réduire les animations ». |
| R-UI-06 | **DOIT** : aucune donnée sensible dans les notifications de l'écran de verrouillage (R-SEC-06). |
| R-UI-07 | **DEVRAIT** : interface utilisable d'une main, actions critiques dans la moitié basse de l'écran. |

## Architecture

MVVM / UDF : `ViewModel` expose `StateFlow<UiState>` dérivé d'`alert-engine` et des moteurs ; les événements utilisateur deviennent des `UserIntent`. Aucune logique métier dans les composables.

## Tests

Tests Compose (sémantique, accessibilité) ; captures de référence (screenshot tests) par état × variante (normale, discrète, leurre) × langue × thème.

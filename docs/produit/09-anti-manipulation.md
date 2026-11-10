# Module produit 9 — Sécurité et anti-manipulation

**Objectif** : empêcher qu'un agresseur désactive facilement la protection, sans piéger l'utilisateur lui-même.

Modules logiciels : `privacy-engine`, `alert-engine`, `ui-mobile`, `backend-api`. Menaces détaillées : [securite/modele-de-menace.md](../securite/modele-de-menace.md).

## Fonctionnalités et faisabilité Android

| Fonction | Faisabilité | Spécification |
|---|---|---|
| **PIN de désactivation** | ✅ | 4 à 8 chiffres ; requis pour désarmer, terminer un trajet ou annuler une alerte. Stocké comme hachage dérivé (Argon2id ou PBKDF2 ≥ 310 000 itérations, avec sel) ; temporisation progressive après des échecs |
| **PIN de contrainte** | ✅ | **Non retenu pour le MVP** (ADR-0015) ; option V2 |
| **Biométrie** | ✅ | **Autorisée** (ADR-0015). `BiometricPrompt` (classe Strong), comme alternative au PIN. ⚠ Elle peut être forcée physiquement (doigt de la victime) : désactivable pour annuler une alerte |
| **Phrase secrète de détresse** | ✅ (si l'écoute est active) | → `EMERGENCY_MODE` silencieux (module 3) |
| **Bouton volume : appui long → SOS** | ⚠ Limité | Une application ne reçoit pas les touches de volume en arrière-plan. Seul un `AccessibilityService` le permet, et la politique Play réserve l'API d'accessibilité aux usages d'accessibilité → **risque de rejet**. Alternative V1 : la tuile de réglages rapides, le widget et l'action SOS sur la notification persistante. ❓ |
| **Triple pression sur l'alimentation → SOS** | ❌ pour une app tierce | Non accessible aux applications. En revanche, **Urgence SOS d'Android** (Android 12+ selon le fabricant : 5 pressions) existe : l'onboarding guide l'utilisateur pour l'activer et y mettre ses contacts |
| **Détection de l'arrêt de l'application** | ⚠ Partielle | Un arrêt forcé ou une désinstallation ne peuvent pas être interceptés. Mesures : extinction détectée via `ACTION_SHUTDOWN` → dernier message aux proches ; perte du battement côté serveur → « perte de contact » (R-TRIP-05) |
| **Notification de dernière activité** | ✅ (serveur) | Pendant un trajet ou une alerte, le serveur informe les proches si le battement s'arrête, avec la dernière position connue |
| **Mode leurre** | ✅ | Écran neutre (ex. calculatrice ou météo) au lieu de l'interface ; l'alerte continue. ⚠ L'icône et le nom de l'application ne peuvent pas être masqués de manière fiable sans enfreindre les règles Play (pas de nom ni d'icône trompeurs) → ❓ seulement un mode leurre **dans** l'application, et un alias d'icône neutre déclaré à Play |
| **Effacement d'urgence** | ✅ | Geste dédié + PIN → suppression des clés (preuves, journal, contacts locaux) en < 2 s. Les preuves déjà envoyées au serveur restent accessibles aux proches désignés (c'est voulu) |
| **Journal anti-effacement** | ✅ | Chaque armement, désarmement, changement de réglage sensible ou échec de PIN est horodaté dans une chaîne de hachages ; la racine est envoyée au serveur si une alerte ou un trajet est actif |

## Règles

| ID | Règle |
|---|---|
| R-SEC-01 | **DOIT** : sans PIN configuré, désarmer reste possible en 1 geste **hors trajet**. Démarrer un trajet protégé exige un PIN (ADR-0015). Biométrie autorisée en alternative. |
| R-SEC-02 | **DOIT** : après 5 PIN erronés pendant une alerte, l'alerte est maintenue et les proches reçoivent « Tentatives de désactivation ». |
| R-SEC-03 | **DOIT** : désarmer pendant un trajet ou une alerte notifie les proches suivants (R-SM-06). |
| R-SEC-04 | **DOIT** : l'application ne demande pas les droits d'administrateur de l'appareil et ne bloque pas sa propre désinstallation (politique Play + N9). |
| R-SEC-05 | **DOIT** : l'interface n'apparaît pas dans les captures d'écran ni dans l'aperçu des applications récentes en mode leurre et dans le coffre (`FLAG_SECURE`). |
| R-SEC-06 | **DOIT** : les notifications sur l'écran de verrouillage n'affichent aucun contenu sensible (visibilité `PRIVATE` ou `SECRET`, avec une version publique neutre). |

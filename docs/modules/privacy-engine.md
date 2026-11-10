# privacy-engine

## Responsabilité

Permissions, consentements, PIN et biométrie, clés maîtresses, journal local anti-effacement, politiques de rétention, suppression et export des données (droits de l'utilisateur).

Références : [conformite/vie-privee-donnees.md](../conformite/vie-privee-donnees.md), [securite/cryptographie.md](../securite/cryptographie.md).

## Interface publique

| Élément | Description |
|---|---|
| `permissions: StateFlow<PermissionSnapshot>` | État réel de chaque permission, relu à chaque `onResume` (l'utilisateur peut les retirer dans les réglages système) |
| `consents` | Enregistrement et révocation historisés (`Consent` de core-domain) |
| `pin` | `setPin`, `verify(pin) → OK, DURESS ou FAIL(retryAfter)`, temporisation progressive |
| `authenticate(reason)` | `BiometricPrompt` ou PIN |
| `keys` | Accès aux clés maîtresses (Keystore) pour les autres modules ; jamais exportées |
| `eventLog` | Journal chaîné append-only (implémentation du port `EventLog`) |
| `retentionPolicy` | Durées par type de donnée (preuves, trajets, journal) |
| `exportMyData()` | Archive lisible (JSON + médias) chiffrée par un mot de passe choisi |
| `deleteMyAccount()` | Suppression locale + demande de suppression côté serveur (confirmation) |
| `emergencyWipe()` | Effacement d'urgence (module 9) |

## Durées de rétention par défaut

| Donnée | Téléphone | Serveur |
|---|---|---|
| Audio d'analyse | 0 (mémoire seulement) | 0 |
| Preuves | 24 h après clôture (7 j ou 30 j en option) | Identique |
| Positions d'un trajet sans alerte | Points supprimés à la fin ; résumé conservé 30 j | Supprimées ≤ 1 h après la fin |
| Journal local des détections | 30 j | — |
| Racines de chaîne et événements d'alerte (sans contenu) | 30 j | 90 j (❓ obligation légale ou défense en justice) |
| Contacts de confiance | Jusqu'à leur suppression | Jusqu'à leur suppression ou leur retrait |
| Journaux techniques serveur | — | 14 j, sans contenu et avec IP tronquée |

## Règles

| ID | Règle |
|---|---|
| R-PE-01 | **DOIT** : un retrait de permission dans les réglages système est détecté et l'état de la protection est rétrogradé proprement (ex. micro retiré → `ARMED_FULL` sans audio, avec une notification). |
| R-PE-02 | **DOIT** : le PIN n'est jamais stocké en clair ni envoyé au serveur ; il n'est vérifié que localement. |
| R-PE-03 | **DOIT** : le journal est chaîné (chaque entrée contient le hachage de la précédente) ; une rupture est détectée et signalée. |
| R-PE-04 | **DOIT** : aucune donnée personnelle dans les logs Android (`Log.*` supprimés en release par R8, ou wrapper sans contenu). |
| R-PE-05 | **DOIT** : `allowBackup=false` et `dataExtractionRules` excluent tout (les clés du Keystore ne sont de toute façon pas sauvegardables). |

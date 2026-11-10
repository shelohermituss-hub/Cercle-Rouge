# Module produit 10 — Tableau de bord des proches

**Objectif** : permettre à un proche de réagir vite, sans envahir la vie privée de l'utilisateur.

Modules logiciels : `backend-api`, `ui-mobile` (vue « proche » dans l'application), page web de suivi (servie par `backend-api`).

## Accès

| Mode | Pour qui | Accès |
|---|---|---|
| **Lien de suivi web** | Tout proche alerté, même sans l'application | URL avec un jeton aléatoire, valable 2 à 6 h, révocable, et ❓ un code à 4 chiffres envoyé par SMS séparé, pour éviter qu'un lien transféré suffise |
| **Application (vue proche)** | Proche qui a installé Cercle Rouge | Notification push, historique limité |

## Fonctionnalités

| Fonction | Détail |
|---|---|
| Carte | Dernière position connue + trace de l'alerte en cours seulement ; précision et âge affichés (« il y a 2 min ») |
| Statut | En sécurité · Alerte potentielle · Alerte active · Hors ligne |
| Chronologie | Événements de l'alerte : détection, vérification sans réponse, alerte envoyée, positions, preuves disponibles |
| Preuves partagées | Seulement celles que l'utilisateur a choisi de partager (extrait audio, enregistrement), lisibles en streaming, sans téléchargement par défaut |
| Appel direct | `tel:` vers l'utilisateur |
| « J'ai pris contact » | Arrête l'escalade vers le niveau 3 et informe les autres proches |
| « Alerte fausse » | Signale aux autres proches ; **ne clôt pas** l'alerte côté utilisateur, qui doit confirmer avec son PIN (sinon un agresseur proche pourrait l'annuler) |
| « Transmettre aux secours » | Ouvre le composeur avec le numéro local et affiche un résumé à lire (nom, position, raison, heure) ; partage d'un lien « secours » si une intégration officielle existe |
| Historique limité | Alertes des 7 derniers jours au maximum, dans la limite de la rétention de l'utilisateur ; rien en mode « contact anonyme » |

## Règles

| ID | Règle |
|---|---|
| R-DASH-01 | **DOIT** : aucune position n'est visible en dehors d'une alerte active ou d'un trajet partagé. |
| R-DASH-02 | **DOIT** : le lien expire automatiquement ; après la clôture, il affiche seulement « Alerte terminée à HH:MM ». |
| R-DASH-03 | **DOIT** : chaque consultation est journalisée (contact, heure). L'utilisateur peut voir qui a consulté son alerte. |
| R-DASH-04 | **DOIT** : la page web n'utilise aucun traceur ni analytics tiers, envoie l'en-tête `Referrer-Policy: no-referrer`, est exclue de l'indexation (`noindex`) et applique une CSP stricte. |
| R-DASH-05 | **DOIT** : les actions des proches sont visibles par l'utilisateur et par les autres proches, en temps réel (WebSocket, avec repli en polling). |
| R-DASH-06 | **DOIT** : la page fonctionne sur un téléphone d'entrée de gamme en 3G (poids initial < 200 Ko hors carte, carte en tuiles légères). |

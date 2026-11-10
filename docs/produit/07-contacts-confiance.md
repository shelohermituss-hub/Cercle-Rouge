# Module produit 7 — Contacts de confiance

**Objectif** : définir qui est alerté, dans quel ordre et avec quelles informations.

Modules logiciels : `contacts-engine`, `backend-api`, `ui-mobile`.

## Fonctionnalités

| Fonction | Détail |
|---|---|
| Ajout | Sélecteur de contact système (`ACTION_PICK`, aucune permission) ou saisie manuelle. `READ_CONTACTS` n'est pas nécessaire en V1 |
| Groupes | Famille, amis, voisinage, professionnel, personnalisé |
| Ordre d'appel | Liste ordonnée 1, 2, 3… (glisser-déposer) ; c'est l'ordre d'appel au niveau 3 |
| Rôles | Répondant principal (au plus 1), backup, contact médical (reçoit les infos médicales si renseignées) |
| Canaux préférés | Par contact : SMS, appel, WhatsApp, notification, e-mail (multi-sélection, ordonnée) |
| Langue | FR, HT ou EN, pour les messages reçus |
| Message personnalisé | Texte ajouté à l'alerte (ex. « Mon code de portail est… » : ⚠ avertir que ce texte part en SMS) |
| Test d'alerte | Envoi marqué « TEST » via le même chemin que l'alerte réelle ; le proche confirme la réception |
| Mode « contact anonyme » | Le proche ne voit que l'alerte active : ni historique, ni trajets passés |
| Plans par contexte | ❓ (V2) Contacts différents selon le contexte (trajet de nuit, rendez-vous…) |

## Consentement du contact

Un proche **ne reçoit pas d'alertes réelles** avant d'avoir accepté.

1. L'utilisateur ajoute le contact.
2. Le contact reçoit un SMS : « Prénom souhaite t'ajouter comme contact de confiance sur Cercle Rouge. Accepter : [lien] » (ou une notification s'il a l'application).
3. La page d'acceptation explique ce qu'il recevra, quand, et comment se retirer. Il choisit sa langue et ses canaux.
4. Statut côté utilisateur : *En attente* · *Accepté* · *Refusé* · *Retiré*.

❓ **Exception à arbitrer** : un contact non consenti peut-il recevoir un SOS **manuel** en cas d'urgence réelle ? Recommandation : oui, uniquement pour le SOS manuel, avec la mention « Tu n'es pas inscrit·e ; message envoyé par Prénom » et un lien pour bloquer.

## Règles

| ID | Règle |
|---|---|
| R-CON-01 | **DOIT** : au moins 1 contact accepté pour armer les niveaux ≥ 2 automatiques. Sinon, un avertissement s'affiche : la protection peut quand même être armée, avec un journal local et le SOS vers le composeur. |
| R-CON-02 | **DOIT** : jusqu'à 10 contacts ; jusqu'à 5 alertés simultanément au niveau 2 (❓ limite à confirmer). |
| R-CON-03 | **DOIT** : un contact peut se retirer à tout moment via un lien ou en répondant STOP par SMS. Le retrait est effectif en < 1 min côté serveur. |
| R-CON-04 | **DOIT** : le serveur ne stocke que ce qui est nécessaire pour joindre le contact (numéro E.164, langue, canaux, statut). Le numéro est chiffré au repos. Pas de copie du répertoire de l'utilisateur. |
| R-CON-05 | **DOIT** : « Contacts joignables » sur l'écran principal = contacts acceptés ayant ≥ 1 canal utilisable dans les conditions actuelles (ex. SMS sans données). |
| R-CON-06 | **DOIT** : le test d'alerte est limité à 3 par jour et par contact. |
| R-CON-07 | **DOIT** : les numéros sont normalisés en E.164 (libphonenumber) ; le préfixe par défaut est celui de la carte SIM (Haïti +509). |

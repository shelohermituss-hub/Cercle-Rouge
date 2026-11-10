# admin-console

## Responsabilité

Outil interne : modération des abus, gestion des incidents techniques, statistiques **anonymisées**, support utilisateur.

## Principe directeur

**L'équipe ne voit pas le contenu des alertes.** Pas d'accès aux positions, preuves ni messages. Les preuves sont chiffrées côté client ; la console travaille sur des métadonnées.

## Fonctionnalités

| Fonction | Données visibles | Garde-fous |
|---|---|---|
| Santé du service | Taux de livraison par canal et par opérateur, latences, files, erreurs | Agrégats seulement |
| Incidents | Alertes non livrées (identifiant pseudonymisé, canal, code d'erreur) | Pas de contenu |
| Abus | Signalements de contacts (« je ne connais pas cette personne », STOP), volumes anormaux de SMS | Actions : limiter ou bloquer un appareil ; motif obligatoire |
| Support | Recherche par identifiant de support fourni **par l'utilisateur** (code affiché dans l'app) | Accès limité dans le temps, journalisé, avec motif |
| Statistiques | Nombre d'alertes par niveau et par jour, part des fausses alertes, temps de réponse des proches | k-anonymat (pas de cellule < 10) ; pas de géographie plus fine que la ville |

## Règles

| ID | Règle |
|---|---|
| R-ADM-01 | **DOIT** : authentification forte (SSO + clé matérielle FIDO2), rôles (lecture, support, admin), principe du moindre privilège. |
| R-ADM-02 | **DOIT** : chaque action est journalisée de façon append-only et revue mensuellement. |
| R-ADM-03 | **DOIT** : aucune fonction de « suivi en direct » d'un utilisateur, même pour le support. |
| R-ADM-04 | **DOIT** : la console n'est pas exposée publiquement (VPN ou proxy à accès conditionnel). |
| R-ADM-05 | **DOIT** : les réquisitions des autorités suivent une procédure écrite (❓ juridique) ; on ne peut livrer que ce qui existe encore (rétention courte) et ce qui est déchiffrable par le serveur (rien pour les preuves chiffrées de bout en bout, si cette option est retenue). |

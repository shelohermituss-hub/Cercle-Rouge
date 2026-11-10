# ADR-0015 — Réglages par défaut, SOS non consenti et PIN

- Statut : **Acceptée** · Date : 2026-11-10

## Décisions
| Sujet | Décision |
|---|---|
| Niveau par défaut (S0.02) | **Désactivé + SOS** : aucune écoute ni position après l'installation ; le SOS est toujours visible ; l'app propose « Trajet protégé » au premier trajet |
| SOS vers un contact non consenti (Q6) | **Oui, uniquement pour le SOS manuel**. Message : « Tu n'es pas inscrit·e ; message envoyé par Prénom » + lien STOP ou blocage. Les alertes automatiques (IA, trajet, perte de contact) restent réservées aux contacts qui ont accepté |
| PIN en trajet (Q4) | **PIN obligatoire pour démarrer un trajet protégé** (sinon un agresseur peut appuyer sur « Je suis arrivé ») |
| PIN ailleurs | Facultatif mais fortement recommandé ; s'il existe, il est exigé pour désarmer et annuler une alerte (R-SM-04) |
| Biométrie | **Autorisée** en alternative au PIN (classe Strong). L'utilisateur peut l'interdire pour l'annulation d'une alerte |
| PIN de contrainte (Q5) | **Non retenu** pour le MVP |

## Conséquences
- Sans PIN de contrainte, un agresseur qui **force** la victime à donner son PIN peut désarmer. Mesures conservées : notification « Protection désactivée à HH:MM » aux proches pendant un trajet ou une alerte (R-SM-06), journal horodaté, perte de contact côté serveur. Le PIN de contrainte reste une option documentée pour la V2.
- R-SEC-01 est précisé : sans PIN, désarmer reste possible en 1 geste **hors trajet** ; un trajet ne peut pas exister sans PIN.

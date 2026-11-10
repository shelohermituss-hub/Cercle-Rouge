# Vision et principes non négociables

## Mission

Réduire le temps entre le moment où une personne est en danger et celui où quelqu'un de confiance le sait et peut agir, **sans transformer le téléphone en outil de surveillance**.

## Utilisateurs

| Profil | Besoin principal | Points d'attention |
|---|---|---|
| Adulte en trajet (nuit, taxi, moto-taxi, transport en commun) | Que quelqu'un sache s'il n'arrive pas | Faux positifs dans le bruit urbain |
| Personne en rendez-vous ou en sortie | Alerte discrète, sans montrer l'application | Mode discret (mode leurre en V2) |
| Personne isolée ou vulnérable | Alerte même sans pouvoir toucher le téléphone | Phrase secrète, détection audio |
| Jeune et adolescent·e (12–17 ans) | Rassurer les parents sans être surveillé·e en permanence | Âge minimum 12 ans ; consentement d'un parent ou tuteur pour les 12–15 ans (ADR-0009) ; autonomie |
| Contact de confiance (proche) | Comprendre vite et agir | Message clair, pas de panique inutile |

## Ce que Cercle Rouge fait

- Détecter des **signaux** de danger : sons (cri, choc, vitre brisée, alarme), mots de détresse, phrase secrète, déviation de trajet, arrêt anormal, perte de contact.
- **Vérifier** auprès de l'utilisateur avant d'alerter (vibration, notification, « Es-tu en sécurité ? »).
- **Alerter** les contacts choisis, dans l'ordre et par les canaux choisis.
- **Conserver** des preuves chiffrées, pour une durée limitée.
- **Fonctionner hors ligne** et envoyer dès que le réseau revient.

## Ce que Cercle Rouge ne fait jamais

| # | Interdit | Pourquoi |
|---|---|---|
| N1 | Prétendre prédire ou garantir la détection d'un enlèvement ou d'une agression | Honnêteté, responsabilité ; l'IA produit des probabilités, pas des certitudes |
| N2 | Écouter sans que l'utilisateur l'ait activé, ou sans indicateur visible | Consentement ; obligation Android (service de premier plan + indicateur micro) |
| N3 | Envoyer de l'audio brut au cloud en fonctionnement normal | IA locale d'abord |
| N4 | Appeler la police ou les secours automatiquement sans consentement explicite préalable | L'IA ne décide pas seule ; Android l'interdit de toute façon pour les numéros d'urgence |
| N5 | Partager la position, l'audio ou la vidéo hors d'un trajet protégé ou d'une urgence déclarée | Données minimales |
| N6 | Conserver des preuves sans limite de durée par défaut | Rétention limitée |
| N7 | Entraîner un modèle sur les données personnelles sans consentement explicite et séparé | Vie privée |
| N8 | Bloquer une fonctionnalité essentielle parce qu'une permission non indispensable est refusée | Dégradation gracieuse |
| N9 | Servir à surveiller quelqu'un à son insu (usage « stalkerware ») | L'utilisateur protégé est toujours le détenteur du téléphone et le seul à armer la protection |

## Principes non négociables

| ID | Principe | Traduction vérifiable |
|---|---|---|
| P1 | **Écoute opt-in** | Le micro n'est ouvert que dans les états `ARMED_*`, `TRIP_ACTIVE` (si l'audio est activé), `ALERT_*` et `EMERGENCY_MODE`. Une notification persistante et l'indicateur système du micro sont visibles. Désactivation possible en 1 geste depuis l'écran principal, la tuile de réglages rapides, la notification et le widget, sous réserve du PIN anti-contrainte (voir P8). |
| P2 | **IA locale d'abord** | Aucun octet audio ne quitte le téléphone hors des niveaux d'alerte ≥ 2 **et** si l'utilisateur a activé le partage d'extraits audio. Vérifiable par un test réseau. |
| P3 | **Escalade progressive** | Détection → vibration/notification → vérification → proches → autorités (si configuré). Aucun saut de niveau, sauf le SOS manuel, la phrase secrète et l'« urgence automatique » configurée explicitement. |
| P4 | **Données minimales** | La position est collectée seulement dans `ARMED_FULL`, `TRIP_ACTIVE`, `ALERT_*` et `EMERGENCY_MODE`. Elle est partagée seulement à partir du niveau 2. Les liens de suivi expirent (2 à 6 h). |
| P5 | **Preuves chiffrées** | Chiffrées avant écriture sur disque. Rétention **choisie à l'onboarding** (24 h, 7 j, 30 j ou à la clôture ; 24 h tant que rien n'est choisi), et au maximum 30 jours. Suppression automatique vérifiée par une tâche planifiée. |
| P6 | **Dégradation gracieuse** | Sans réseau : file d'attente persistante, SMS en secours, envoi à la reconnexion. Sans GPS : alertes audio et SOS toujours disponibles. |
| P7 | **Transparence** | Chaque détection, même non confirmée, est inscrite dans un journal **local** lisible par l'utilisateur (« Détection à 20 h 12, non confirmée, aucune donnée envoyée »). |
| P8 | **Résistance à la contrainte** | Désarmer pendant une alerte exige le PIN ou la biométrie s'ils existent ; démarrer un trajet exige un PIN (ADR-0015). Toute désactivation pendant un trajet ou une alerte est notifiée aux proches et horodatée. (PIN de contrainte : V2.) |

## Ordre de priorité en cas de conflit

1. **Sécurité physique de l'utilisateur** : une alerte légitime ne doit pas être bloquée.
2. **Vie privée et consentement** (P1, P2, P4, N2, N9).
3. **Fiabilité** : l'alerte part, même en mode dégradé.
4. **Réduction des faux positifs.**
5. **Autonomie batterie.**
6. **Confort et esthétique.**

> Exemple : si la batterie est à 5 % pendant `EMERGENCY_MODE`, on coupe la vidéo et on garde la position et le SMS (3 > 5). Mais on n'active jamais le micro en cachette pour « économiser » une confirmation (2 > 4).

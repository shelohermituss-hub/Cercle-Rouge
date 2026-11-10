# ADR-0010 — Périmètre du MVP

- Statut : **Acceptée** · Date : 2026-11-10

## Décision : dans le MVP (1.0)
1. Bouton SOS (application, widget, tuile, notification).
2. Contacts de confiance (consentement, ordre, rôles).
3. Alerte par notification push et SMS (direct + serveur en secours).
4. Partage temporaire de la position pendant un trajet ou une alerte.
5. Enregistrement audio chiffré **après une alerte ou un SOS** (pas avant).
6. Détection locale de **mots-clés** de détresse + phrase secrète (FR, puis HT).
7. Détection simple de déviation d'itinéraire (corridor, arrêt, retard, perte de signal).
8. Vérification avant alerte (« Es-tu en sécurité ? »).
9. Hors ligne avec envoi différé.
10. Tableau de bord minimal des proches (lien web temporaire).
11. **Ajouté au MVP** : PIN de désactivation (obligatoire en trajet, ADR-0015) et journal local, car sans eux le bouton « Je vais bien » permet à un agresseur d'annuler l'alerte.

## Hors MVP (V2)
Classification audio avancée (cris, vitres, chocs, alarmes : YAMNet), vidéo, analyse émotionnelle, **appels automatisés** (y compris l'appel automatique du contact n° 1 au niveau 3), WhatsApp automatique, intégration aux autorités, iOS.

L'appel **manuel** (bouton « Appeler les secours » → composeur) reste dans le MVP : ce n'est pas une intégration.

## Conséquences
- Niveau 3 du MVP = SMS à tous les contacts + notifications répétées (sans appel automatique).
- La composante A du score repose sur le mot-clé ; B est à 0 sauf chute (capteurs).
- Le MVP est atteignable en ~9 sprints au lieu de 16.

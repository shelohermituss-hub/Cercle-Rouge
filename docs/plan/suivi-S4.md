# Suivi du Sprint 4 — Trajet protégé (partie locale)

Mis à jour le 2026-11-11. Légende : ✅ fait et vérifié · 🟡 fait, non vérifié sur téléphone · ⏳ à faire · ⏸ mis de côté · 👤 dépend d'une action humaine

Le Sprint 3 (IA audio) est mis de côté pour l'instant, à la demande du porteur du projet. Le Sprint 4 ne fait que sa partie locale : le serveur (Supabase) viendra plus tard.

## Décisions du sprint (ADR-0019)

- **Destination** : toucher la carte, favoris locaux chiffrés, et recherche d'adresse **opt-in** (avis à la première utilisation ; seul le texte tapé part, jamais la position).
- **Position** : Google Fused Location, repli `LocationManager` sans Play Services.

## Étapes

| Étape | Statut | Note |
|---|---|---|
| S4.01 Profils de suivi, Fused + repli, précise/approximative | ✅ (logique) / 🟡 (capteur) | `Tracking.intervalFor` testé (ARMED 30 s, TRIP 10 s, EMERGENCY 5 s, ralenti sous 15 % de batterie) ; `LocationTracker` non essayé sur téléphone, ni sans Play Services |
| S4.02 Service de premier plan type `location` | 🟡 | `specialUse\|location`, `location` seulement si accordée (API 34+). **Suivi écran éteint 1 h à faire** |
| S4.03 Algorithmes purs (corridor, écart, direction, retour, arrêt, retard, zones) | ✅ | Module `:location-engine`, seuils par mode (à calibrer sur le terrain) |
| S4.04 Banc de rejeu GPX | ✅ | Scénarios synthétiques ; **traces réelles de S0.21 à ajouter** (👤) |
| S4.05 Écran Trajet | 🟡 | Destination (carte, favoris, recherche), mode (marche, voiture, tap-tap, taxi, moto), heure prévue (−5/+5), démarrage avec PIN, « Je fais un arrêt » (10 à 60 min), « Je suis arrivé·e » (PIN), arrivée détectée proposée. « Contacts qui suivent » : avec le serveur (S4.07) |
| S4.06 Événement de trajet → vérification (niveau 1 au plus) | ✅ (moteur) / 🟡 (branchement) | `TripIssue` → vérification ; sans réponse, escalade normale |
| S4.07 `trips` + RPC + battement | ⏸ | Serveur |
| S4.08 Chien de garde `pg_cron` | ⏸ | Serveur |
| S4.09 Dernier message à l'extinction | ⏸ | Opt-in à définir + test sur appareil |
| S4.10 Carte dans la page des proches | ⏸ | Serveur et page web |
| S4.11 Fin de trajet : suppression des points | ✅ (local) | Aucun point gardé ; 3 dernières positions en mémoire, effacées à la fin ; résumé sans coordonnées au journal. Partie serveur avec S4.07 |

## À vérifier sur un vrai téléphone (👤)

1. Trajet à pied de 15 min, écran éteint : la notification reste, aucun faux incident.
2. Arrêt de 12 min sans le déclarer : vérification « Es-tu en sécurité ? ».
3. Recherche d'adresse : avis affiché une fois ; résultats en Haïti.
4. Téléphone sans Play Services (si disponible) : position par repli.

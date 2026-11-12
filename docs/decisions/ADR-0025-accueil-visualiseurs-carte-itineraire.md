# ADR-0025 — Accueil : visualiseurs d'écoute, carte réduite, itinéraire tracé

- Statut : **Acceptée** · Date : 2026-11-12 · Lié à ADR-0018 (carte), ADR-0019 (trajet), ADR-0020 (phrase secrète)

## Décisions
| Sujet | Décision |
|---|---|
| Carte de l'accueil | Limitée à la zone au-dessus des panneaux ; le disque central est retiré. Le **bouton ON/OFF** prend la place du bouton micro, en haut à droite (rempli lime quand la protection est active) |
| Zoom | La carte s'ouvre toujours sur une vue de ville (zoom 13 sur Port-au-Prince, ou 15 sur la dernière position connue). Avant, elle restait sur le monde entier tant que le GPS n'avait pas rendu de point. Zoom minimum 6 : plus de vue « monde » |
| Visualiseur « Écoute en direct » | 28 barres, une par 100 ms : niveau **réel** du micro (dBFS, −70 à −10). Aucun son n'est gardé ni envoyé : seulement des nombres, perdus à l'arrêt du micro |
| Visualiseur « Réflexion IA » | Orbe qui respire avec la voix, étape courante (écoute, voix détectée, résultat) et les 3 dernières lignes de l'analyse (voix détectée, écart et seuil DTW, phrase reconnue ou non). **Ce sont les étapes réelles** de la comparaison à la phrase secrète (ADR-0020) : il n'y a pas de réseau de neurones à montrer. Toucher un panneau ouvre les réglages de l'écoute |
| Boutons de droite | Une couleur par fonction (cyan : position, violet : proches, ambre : trajet, blanc : réglages), fond opaque : ils ne se confondent plus avec le lime de la carte. Coupée : tout en gris |
| SOS | Rouge du logo (`#ED1B24`), texte blanc |
| Route du trajet | Tracée en **ambre** (liseré sombre), avec le repère d'arrivée ambre ; la carte cadre tout le trajet. Ligne droite en pointillés tant qu'aucune route n'est calculée ; trait plein pour une route par les rues |
| Route par les rues | **Opt-in** (accord demandé une fois, avec le texte exact de ce qui part). Départ et arrivée (2 points à 5 décimales, sans nom ni identifiant) vont au routage OpenStreetMap (`routing.openstreetmap.de`, profils piéton et voiture ; moto, taxi et tap-tap prennent le profil voiture) ; le service voit l'adresse IP. Sans accord, ou service injoignable : ligne droite locale. Le tracé reste en mémoire, jamais sur le disque |

## Réserves
- Le service OSM Allemagne est communautaire (usage raisonnable, sans garantie). Avant le lancement : héberger un routeur (OSRM ou Valhalla) ou prendre un fournisseur sous contrat, puis changer l'URL dans `RouteService`.
- La route n'alimente pas encore les alertes de trajet (corridor, écart) : elles restent calculées en ligne droite (ADR-0019).
- Les seuils de la phrase secrète n'ont pas été calibrés sur de vraies voix (S5.12) : l'écart affiché est indicatif.

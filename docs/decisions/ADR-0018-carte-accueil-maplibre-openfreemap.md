# ADR-0018 — Carte réelle sur l'accueil : MapLibre + OpenFreeMap

- Statut : **Acceptée** · Date : 2026-11-10 · Résout **Q14** (fournisseur de cartes)

## Contexte
Le porteur du projet veut une **vraie carte en direct** en arrière-plan de l'accueil (design v3 « Pixel Lime »), noir et blanc en veille, en couleurs quand la protection est active. Contraintes : P4 (données minimales, rien en `OFF`), téléphones d'entrée de gamme, données mobiles chères en Haïti.

## Décisions
| Sujet | Décision |
|---|---|
| Moteur | **MapLibre Native Android** (libre, sans compte, sans Google Play Services) |
| Tuiles | **OpenFreeMap** (`tiles.openfreemap.org`, données OpenStreetMap) : sans clé, sans compte, sans pistage publicitaire |
| Style | Style maison (`MapStyle.kt`) : palette grise en veille, routes lime quand la protection est active |
| Position | Point en direct **seulement si la protection est active** et la localisation accordée, application au premier plan. En `OFF`, le GPS n'est jamais sollicité : carte centrée sur Port-au-Prince, sans point |
| Autorisations | Micro et localisation demandés **ensemble, au moment d'activer** la protection (R-PLAY-02). Pas de localisation en arrière-plan (ADR-0006) |
| Attribution | « © OpenMapTiles © OpenStreetMap » affichée en texte sur l'accueil |

## Conséquences
- **Vie privée** : la position **ne quitte jamais le téléphone**. Le serveur de tuiles voit l'adresse IP et la zone affichée (comme tout fournisseur de cartes). Il est ajouté à la liste des destinataires (`conformite/vie-privee-donnees.md`).
- **Données mobiles** : tuiles vectorielles légères, mises en cache par MapLibre ; la carte fonctionne en lecture du cache hors ligne.
- **Taille** : MapLibre embarque du code natif (~10 Mo par architecture). Distribution en AAB : chaque téléphone ne télécharge que la sienne.
- **Disponibilité** : OpenFreeMap n'a pas de SLA. Si besoin : auto-hébergement des tuiles d'Haïti (PMTiles) ou MapTiler, sans changer le code (le style ne change que d'URL).
- **TLS** : pas d'épinglage pour les tuiles (ADR-0014 vise le backend) ; rien de sensible n'y transite.
- **Tests** : le style est validé sur la JVM (`MapStyleTest`) ; le rendu réel est vérifié avec maplibre-gl dans un navigateur (même spécification de style).

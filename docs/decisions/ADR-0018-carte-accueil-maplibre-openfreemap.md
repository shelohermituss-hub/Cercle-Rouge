# ADR-0018 — Carte réelle sur l'accueil : MapLibre + OpenFreeMap

- Statut : **Acceptée** · Date : 2026-11-10 · Résout **Q14** (fournisseur de cartes)

## Contexte
Le porteur du projet veut une **vraie carte en direct** en arrière-plan de l'accueil (design v3 « Pixel Lime »), noir et blanc en veille, en couleurs quand la protection est active. Contraintes : P4 (données minimales, rien en `OFF`), téléphones d'entrée de gamme, données mobiles chères en Haïti.

## Décisions
| Sujet | Décision |
|---|---|
| Moteur | **MapLibre Native Android** (libre, sans compte, sans Google Play Services) |
| Tuiles | **OpenFreeMap** (`tiles.openfreemap.org`, données OpenStreetMap) : sans clé, sans compte, sans pistage publicitaire |
| Style | **Satellite hybride** (2026-11-11) : imagerie **Sentinel-2 cloudless 2016 d'EOX** (`tiles.maps.eox.at`, CC BY 4.0, sans clé), routes principales et noms de lieux OpenFreeMap par-dessus. Veille : image en noir et blanc ; protection active : couleurs |
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

## Révision du 2026-11-11 : satellite
- Le porteur du projet veut une carte satellite. Choix : **Sentinel-2 EOX, couche 2016**, la seule sous **CC BY 4.0** (les couches 2017-2024 sont CC BY-NC-SA, interdites pour une app publiée). Gratuit, sans clé ni compte.
- Limite : **10 m par pixel**. On voit les quartiers et la côte, pas les rues ; le suivi de position est plafonné au zoom 13,5. Si une imagerie plus fine devient nécessaire : MapTiler Satellite (clé API), sans changer le code hors du style.
- Nouveau destinataire technique : EOX (voit l'IP et la zone affichée, jamais la position). Attribution affichée sur la carte.

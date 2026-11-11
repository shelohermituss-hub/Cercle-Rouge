# ADR-0019 — Trajet protégé : fournisseur de position, destination, recherche d'adresse

- Statut : **Acceptée** · Date : 2026-11-11 · Sprint 4 (S4.01, S4.05)

## Contexte
Le Sprint 4 ajoute le trajet protégé. Il faut choisir comment lire la position, comment l'utilisateur choisit sa destination, et comment rester fidèle à P4 (données minimales) et R-LE-03 (la position ne part pas sans alerte). Le Sprint 3 (IA audio) est mis de côté pour l'instant à la demande du porteur du projet.

## Décisions
| Sujet | Décision |
|---|---|
| Position | **Google Fused Location** (`play-services-location`) quand Google Play Services est présent ; sinon **repli `LocationManager`** (GPS + réseau). Profils : ARMED 30 s, TRIP 10 s, EMERGENCY 5 s, ralentis sous 15 % de batterie (`Tracking.intervalFor`) |
| Service | `ProtectionService` déclare `specialUse\|location` ; le type `location` n'est demandé que si la localisation est accordée (API 34+). **Pas d'`ACCESS_BACKGROUND_LOCATION`** (ADR-0006) |
| Destination | 1) **toucher la carte** ; 2) **favoris locaux** (10 au plus, base chiffrée SQLCipher) ; 3) **recherche d'adresse opt-in** |
| Recherche | Géocodeur du système Android (`android.location.Geocoder`, service Google), **bornée au rectangle d'Haïti**. Seul le texte tapé part ; **la position de l'utilisateur n'est jamais jointe**. Avis et accord à la première utilisation (réglage `trip.search.consent`). Sans accord, la carte et les favoris marchent hors ligne |
| Durée prévue | Estimée en ligne droite à la vitesse moyenne du mode (arrondie aux 5 min), toujours modifiable (−5 / +5) |
| Démarrage | PIN obligatoire (ADR-0015) ; le moteur refuse sans PIN ou sans localisation |
| Fin | « Je suis arrivé·e » avec PIN ; l'arrivée détectée est seulement **proposée** |
| Incidents | Écart, direction opposée, retour au départ, arrêt prolongé, retard, zone non sûre → `TripIssue` : **vérification (niveau 1) au plus**, jamais une alerte directe |
| Points | Aucun point conservé : les 3 dernières positions en mémoire, effacées à la fin ; au journal, un résumé sans coordonnées (S4.11 local) |

## Alternatives écartées
- **Photon / Nominatim (OpenStreetMap)** pour la recherche : sans Google, mais Nominatim interdit l'autocomplétion et impose un débit d'une requête par seconde ; Photon public n'a pas de garantie. Un nouvel hôte réseau serait à déclarer. Le géocodeur du système ne demande ni dépendance ni clé. À revoir si un serveur Photon auto-hébergé devient disponible.
- **Saisie d'adresse seule** : les adresses en Haïti sont souvent informelles ; la carte et les favoris sont plus fiables.

## Conséquences
- **Vie privée** : nouveau destinataire, Google (géocodeur), **seulement si l'utilisateur accepte** la recherche, et seulement pour le texte tapé. Ajouté à `conformite/vie-privee-donnees.md`.
- **Appareils sans Play Services** : position par `LocationManager` (plus lente au premier point) ; recherche indisponible (l'écran le dit et propose la carte).
- **Play** : type de service `location` à déclarer dans la Play Console (`conformite/android-permissions-play.md`).
- **Reste serveur** (S4.07, S4.08, S4.10) : battement, chien de garde, carte des proches, à faire avec Supabase.

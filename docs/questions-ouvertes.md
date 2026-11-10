# Questions ouvertes

À trancher **avant** l'implémentation des modules concernés. Les questions bloquantes sont marquées 🔴.

## Produit

| # | Question | Recommandation | Bloque |
|---|---|---|---|
| Q1 🔴 | **Formule du score de risque** : la spécification indique « Où : A… E » mais la formule n'a pas été transmise | Proposition dans [ia/modele-ia-locale.md](ia/modele-ia-locale.md#score-de-risque) | alert-engine |
| Q2 🔴 | Pays de lancement ? Numéros d'urgence par défaut ? (la spécification cite 17 et 112, qui sont français et européens ; Haïti : police 114, pompiers 115, ambulance 116, à vérifier) | Haïti d'abord, numéros configurables par pays | ui-mobile, juridique |
| Q3 🔴 | Âge minimum et profils enfant et ado : qui arme l'app d'un enfant ? Consentement parental ? | Adolescents ≥ 13 ans avec consentement ; enfants en V2 après revue juridique | onboarding, conformité Play |
| Q4 | PIN obligatoire en mode trajet ? | Fortement recommandé, pas obligatoire (R-SEC-01) | module 9 |
| Q5 | PIN de contrainte (désarmement simulé + alerte silencieuse) | Oui | module 9 |
| Q6 | Un contact non consenti peut-il recevoir un SOS manuel ? | Oui, SOS manuel uniquement, avec lien de blocage | contacts-engine |
| Q7 | Nombre maximal de contacts et de destinataires simultanés | 10 et 5 | contacts-engine |
| Q8 | Mode leurre : interne à l'app seulement, ou aussi un alias d'icône neutre ? | Interne + alias neutre honnête (déclaré) | module 9 |
| Q9 | Vidéo de preuve en V1 ? | V1.1 (complexité, batterie, données) | evidence-engine |

## Technique

| # | Question | Recommandation | Bloque |
|---|---|---|---|
| Q10 🔴 | Backend : Go ou Node.js ? Hébergement et localisation des données ? | Go si l'équipe est à l'aise ; deux régions | backend-api |
| Q11 🔴 | Exception Play `SEND_SMS` : soumettre ? Variante APK hors Play ? | Soumettre tôt ; plan B serveur | ADR-0005 |
| Q12 | Partage des preuves avec les proches : option A, B ou C | B (clé dans le fragment de l'URL) | crypto, backend |
| Q13 | Fournisseurs SMS et téléphonie couvrant Digicel et Natcom | À évaluer (coût, fiabilité, délai) | backend-api |
| Q14 | Fournisseur de cartes (V1.1) : Mapbox, Google, OSM auto-hébergé ? | OSM ou MapLibre pour la vie privée, à évaluer | location-engine |
| Q15 | Appareils sans Google Play Services (FCM, Fused Location) : à supporter ? | Repli `LocationManager` + SMS ; pas de push | sync, location |
| Q16 | `IGNORE_BATTERY_OPTIMIZATIONS` et `USE_EXACT_ALARM` : acceptables pour Play ? | Demander avec justification « safety » | alert-engine |
| Q17 | Kotlin Multiplatform pour `core-domain` (iOS phase 2) ? | Garder du Kotlin pur maintenant, décider en phase 2 | — |
| Q18 | Crash reporting sans données personnelles : outil ? | ACRA auto-hébergé, ou rien en V1 | — |

## IA et données

| # | Question | Recommandation |
|---|---|---|
| Q19 🔴 | Collecte de données audio consentie (créole, bruits d'Haïti) : partenaire, budget, protocole | Indispensable pour le KWS en créole et la calibration |
| Q20 | Liste des mots-clés de détresse en créole | Atelier avec des locuteurs natifs |
| Q21 | Inclure la détection de coups de feu ? | Oui, en signal B |
| Q22 | Modèle 4 (émotions) : date d'activation ? | Pas avant une évaluation par langue et une revue juridique |

## Juridique

| # | Question |
|---|---|
| Q23 🔴 | Légalité de l'enregistrement de tiers et valeur probante, par pays |
| Q24 | Loi haïtienne sur les données personnelles et obligations des opérateurs |
| Q25 | Procédure de réquisition des autorités |
| Q26 | Conditions d'utilisation : limites de responsabilité (non-détection, fausses alertes) |
| Q27 | Intégration officielle avec des centres d'urgence (niveau 4) : existe-t-il un interlocuteur ? |

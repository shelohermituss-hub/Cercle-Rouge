# Questions ouvertes

À trancher **avant** l'implémentation des modules concernés. Les questions bloquantes sont marquées 🔴.

## Produit

| # | Question | Recommandation | Bloque |
|---|---|---|---|
| ~~Q1~~ ✅ | Formule du score de risque | **Décidé** : `Risk = w₁A + w₂B + w₃C + w₄D + w₅E` (ADR-0008) | — |
| ~~Q2~~ ✅ | Pays de lancement | **Décidé : Haïti** (ADR-0009). Reste à confirmer les numéros 114, 115 et 116 auprès des autorités | — |
| ~~Q3~~ ✅ | Âge minimum | **Décidé : 12 ans** (ADR-0009). Reste Q3b ci-dessous | — |
| ~~Q3b~~ ✅ | Consentement parental | **Décidé** : confirmation dans l'app, sur le téléphone du parent inscrit (ADR-0013, produit 11) | — |
| Q3c | Parent proposé automatiquement comme contact de confiance ? | Proposé ; le jeune décide | produit 11 |
| Q3d | Aucun adulte avec smartphone (fréquent en Haïti) ? | MVP : SOS manuel seulement ; V2 : code SMS + appel de vérification | produit 11 |
| Q4 | PIN obligatoire en mode trajet ? | Fortement recommandé, pas obligatoire (R-SEC-01) | module 9 |
| Q5 | PIN de contrainte (désarmement simulé + alerte silencieuse) | Oui | module 9 |
| Q6 | Un contact non consenti peut-il recevoir un SOS manuel ? | Oui, SOS manuel uniquement, avec lien de blocage | contacts-engine |
| Q7 | Nombre maximal de contacts et de destinataires simultanés | 10 et 5 | contacts-engine |
| Q8 | Mode leurre : interne à l'app seulement, ou aussi un alias d'icône neutre ? | Interne + alias neutre honnête (déclaré) | module 9 |
| ~~Q9~~ ✅ | Vidéo de preuve | **V2** (ADR-0010) | — |

## Technique

| # | Question | Recommandation | Bloque |
|---|---|---|---|
| ~~Q10~~ ✅ | Backend | **Supabase** (ADR-0011). Reste Q10b | — |
| Q10b | Région Supabase (`us-east-1` ?) et fournisseur SMS pour l'OTP et les alertes (Twilio ?) avec une bonne remise chez Digicel et Natcom | À tester en S0 | S0 |
| Q11 🔴 | Exception Play `SEND_SMS` : soumettre ? Variante APK hors Play ? | Soumettre tôt ; plan B serveur | ADR-0005 |
| Q12 | Partage des preuves avec les proches : option A, B ou C | B (clé dans le fragment de l'URL) | crypto, backend |
| Q13 | Fournisseurs SMS et téléphonie couvrant Digicel et Natcom | À évaluer (coût, fiabilité, délai) | backend-api |
| Q14 | Fournisseur de cartes (V1.1) : Mapbox, Google, OSM auto-hébergé ? | OSM ou MapLibre pour la vie privée, à évaluer | location-engine |
| Q15 | Appareils sans Google Play Services (FCM, Fused Location) : à supporter ? | Repli `LocationManager` + SMS ; pas de push | sync, location |
| Q16 | `IGNORE_BATTERY_OPTIMIZATIONS` et `USE_EXACT_ALARM` : acceptables pour Play ? | Demander avec justification « safety » | alert-engine |
| Q17 | Kotlin Multiplatform pour `core-domain` (iOS phase 2) ? | Garder du Kotlin pur maintenant, décider en phase 2 | — |
| Q28 | Épinglage TLS avec Supabase : épingler les autorités de certification (avec secours et expiration) ou ne rien épingler ? | Épingler l'ensemble des AC avec une expiration courte, et garder le SMS direct en secours | S0 |
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

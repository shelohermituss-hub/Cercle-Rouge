# Questions ouvertes

À trancher **avant** l'implémentation des modules concernés. Les questions bloquantes sont marquées 🔴.

## Produit

| # | Question | Recommandation | Bloque |
|---|---|---|---|
| ~~Q1~~ ✅ | Formule du score de risque | **Décidé** : `Risk = w₁A + w₂B + w₃C + w₄D + w₅E` (ADR-0008) | — |
| ~~Q2~~ ✅ | Pays de lancement | **Décidé : Haïti** (ADR-0009). Reste à confirmer les numéros 114, 115 et 116 auprès des autorités | — |
| ~~Q3~~ ✅ | Âge minimum | **Décidé : 12 ans** (ADR-0009). Reste Q3b ci-dessous | — |
| ~~Q3b~~ ✅ | Consentement parental | **Décidé** : confirmation dans l'app, sur le téléphone du parent inscrit (ADR-0013, produit 11) | — |
| ~~Q3c~~ ✅ | Adulte référent comme contact | **Décidé** : ajouté par défaut comme contact de confiance, retirable par le jeune sans que l'adulte en soit averti (ADR-0013) | — |
| ~~Q3d~~ ✅ | Pas de parent avec smartphone | **Décidé** : tout adulte inscrit (tuteur, enseignant, voisin…) peut confirmer comme « adulte référent » (ADR-0013) | — |
| ~~Q4~~ ✅ | PIN en trajet | **Obligatoire pour démarrer un trajet** ; biométrie autorisée (ADR-0015) | — |
| ~~Q5~~ ✅ | PIN de contrainte | **Non retenu** pour le MVP (ADR-0015) | — |
| ~~Q6~~ ✅ | SOS vers un contact non consenti | **Oui, SOS manuel uniquement**, avec lien STOP (ADR-0015) | — |
| ~~Q7~~ ✅ | Limites de contacts | **5 au total, 3 alertés au niveau 2** (ADR-0017) | — |
| ~~Q8~~ ✅ | Mode leurre et icône neutre | **V2** (ADR-0017) | — |
| ~~Q9~~ ✅ | Vidéo de preuve | **V2** (ADR-0010) | — |

## Technique

| # | Question | Recommandation | Bloque |
|---|---|---|---|
| ~~Q10~~ ✅ | Backend | **Supabase** (ADR-0011). Reste Q10b | — |
| ~~Q10b~~ ✅ | Région et fournisseur SMS | **Décidé** : `us-east-1` ; **Vonage** (OTP Supabase Auth + SMS d'alerte), remise à mesurer en S0 (ADR-0011) | — |
| Q11 🔴 | Exception Play `SEND_SMS` : soumettre ? Variante APK hors Play ? | Soumettre tôt ; plan B serveur | ADR-0005 |
| ~~Q12~~ ✅ | Partage des preuves | **App obligatoire pour écouter** : chiffrement de bout en bout (ADR-0016) | — |
| Q13 | Fournisseurs SMS et téléphonie couvrant Digicel et Natcom | À évaluer (coût, fiabilité, délai) | backend-api |
| Q14 | ~~Fournisseur de cartes~~ | ✅ **MapLibre + OpenFreeMap** ([ADR-0018](decisions/ADR-0018-carte-accueil-maplibre-openfreemap.md)) | location-engine |
| Q15 | Appareils sans Google Play Services (FCM, Fused Location) : à supporter ? | Repli `LocationManager` + SMS ; pas de push | sync, location |
| Q16 | `IGNORE_BATTERY_OPTIMIZATIONS` et `USE_EXACT_ALARM` : acceptables pour Play ? | Demander avec justification « safety » | alert-engine |
| Q17 | Kotlin Multiplatform pour `core-domain` (iOS phase 2) ? | Garder du Kotlin pur maintenant, décider en phase 2 | — |
| ~~Q28~~ ✅ | Épinglage TLS | **Décidé** : épinglage prudent des AC avec broches de secours et expiration de 6 mois (ADR-0014) | — |
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
| Q23 🔴 | Légalité de l'enregistrement de tiers et valeur probante, par pays — **provisoire (2026-11-11)** : enregistrement selon la spec avec avis à l'onboarding et interrupteur (ADR-0021) ; **avis juridique requis avant le lancement** |
| Q24 | Loi haïtienne sur les données personnelles et obligations des opérateurs |
| Q25 | Procédure de réquisition des autorités |
| Q26 | Conditions d'utilisation : limites de responsabilité (non-détection, fausses alertes) |
| Q27 | Intégration officielle avec des centres d'urgence (niveau 4) : existe-t-il un interlocuteur ? |

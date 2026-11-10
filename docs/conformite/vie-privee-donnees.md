# Vie privée et données

> ⚠ Ce document n'est pas un avis juridique. Une revue par un juriste est nécessaire avant le lancement, pour chaque pays visé (❓ Haïti, France ou UE, autres).

## Inventaire des données

| Donnée | Collectée quand | Où | Partagée avec | Rétention | Base (RGPD si UE) |
|---|---|---|---|---|---|
| Audio d'analyse | État armé | Mémoire du téléphone | Personne | 0 | Consentement |
| Extrait audio et enregistrements | Niveau ≥ 2 ou à la demande | Téléphone (chiffré) ; serveur si partagé | Proches désignés | 24 h par défaut (7 ou 30 j en option) | Consentement ; intérêts vitaux en urgence |
| Vidéo | Sur demande ou niveau 3 si activée | Idem | Idem | Idem | Consentement |
| Position | Armé Full, trajet, alerte | Téléphone ; serveur pendant un trajet suivi ou une alerte | Proches (alerte ou trajet partagé) | Trajet : ≤ 1 h après la fin sur le serveur ; alerte : rétention des preuves | Consentement |
| Contacts de confiance | Configuration | Téléphone + serveur (numéro chiffré) | Passerelles SMS et appel (sous-traitants) | Jusqu'à suppression | Consentement de l'utilisateur **et** du contact |
| Phrase secrète (empreinte) | Enrôlement | Téléphone seulement (chiffré) | Personne | Jusqu'à suppression | Consentement |
| Profil (prénom, langue, pays, profil de vulnérabilité) | Onboarding | Téléphone ; serveur (prénom, langue) | Proches (prénom) | Durée du compte | Contrat ou consentement |
| Identifiant d'appareil (clé publique) | Installation | Serveur | — | Durée du compte | Contrat |
| Jeton FCM | Installation | Serveur, Google | Google (transport) | Durée du compte | Contrat |
| Journal technique serveur | Requêtes | Serveur | Hébergeur | 14 j | Intérêt légitime |

**Données sensibles** : la position en temps réel, l'audio et le profil « vulnérabilité » peuvent révéler des informations très sensibles (santé, violences subies). Il faut les traiter comme des données de catégorie particulière : minimisation maximale, ❓ analyse d'impact (AIPD/DPIA) obligatoire si l'UE est visée.

## Sous-traitants prévus (❓ à choisir)

Supabase (hébergement, base, stockage, région à confirmer) · passerelle SMS (couverture Digicel et Natcom) · téléphonie (appel automatisé) · Firebase Cloud Messaging (Google) · ❓ fournisseur de cartes · ❓ autorité d'horodatage · ❓ WhatsApp Business Platform (Meta).

Chaque sous-traitant doit faire l'objet d'un contrat de traitement des données, et ses transferts hors UE doivent être identifiés.

## Droits de l'utilisateur

| Droit | Mise en œuvre |
|---|---|
| Information | Politique lisible dans l'app, en FR, HT et EN, avec des exemples concrets ; version et date |
| Accès et portabilité | `exportMyData()` (privacy-engine) |
| Effacement | Suppression locale immédiate + serveur ≤ 30 j (sauvegardes comprises) ; effacement d'urgence |
| Retrait du consentement | Par fonction, dans les réglages, effet immédiat |
| Contacts tiers | Le proche peut se retirer à tout moment (STOP, lien) ; il est informé de ce qui le concerne |

## Points juridiques à instruire (❓)

1. **Enregistrement de tiers** : capter les paroles d'autrui sans leur consentement est encadré, voire pénalement sanctionné, dans plusieurs pays (ex. France, art. 226-1 du Code pénal), même si la jurisprudence admet souvent la preuve produite par une victime. À vérifier pour Haïti et chaque marché, et à formuler dans les conditions d'utilisation.
2. **Âge minimum : 12 ans (décidé).** Un public visé de moins de 13 ans fait entrer l'app dans la politique Play « Familles » (déclaration du public cible, SDK certifiés, pas de publicité). À instruire aussi : consentement du parent ou tuteur pour les 12–15 ans, et COPPA (États-Unis) et RGPD art. 8 (UE) pour les utilisateurs de la diaspora.
3. **Responsabilité** en cas de non-détection ou de fausse alerte : avertissement clair (N1), conditions d'utilisation.
4. **Réquisitions** des autorités (procédure, transparence).
5. **Loi applicable en Haïti** en matière de données personnelles et de télécommunications (❓ à instruire).
6. **IA et émotions** : la détection d'émotions (modèle 4) relève d'un régime particulier dans le règlement européen sur l'IA si l'UE est visée → à garder désactivée tant que ce n'est pas instruit.

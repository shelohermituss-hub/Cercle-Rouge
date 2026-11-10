# Suivi du Sprint 0 — Cadrage

Démarré le 2026-11-10. Légende : ✅ fait · 🔄 en cours · ⏳ à faire · 👤 action humaine (comptes, paiements, rendez-vous)

## Décisions

| Étape | Sujet | Statut | Résultat |
|---|---|---|---|
| — | Q3c : adulte référent comme contact | ✅ | Ajouté par défaut, retirable en silence (ADR-0013) |
| — | Q3d : pas de parent avec smartphone | ✅ | Tout adulte inscrit peut confirmer (ADR-0013) — ⚠ validation juridique |
| — | Q10b : fournisseur SMS et région | ✅ | Vonage ; `us-east-1` (ADR-0011) |
| S0.06 | Q28 : épinglage TLS | ✅ | Prudent, autorités de certification + secours, expiration de 6 mois (ADR-0014) |
| S0.01 | Nom | ✅ | « Cercle Rouge », fixe (ADR-0017) ; recherche de marque 👤 |
| S0.02 | Niveau de protection par défaut | ✅ | Désactivé + SOS (ADR-0015) |
| S0.03 | Q6 : SOS vers un contact non consenti | ✅ | Oui, SOS manuel seulement (ADR-0015) |
| S0.03b | Q7 : limites de contacts | ✅ | 5 au total, 3 alertés (ADR-0017) |
| S0.04 | Conservation | ✅ | Choisie à l'onboarding, sans présélection (ADR-0017) |
| S0.05 | Q12 : partage des preuves | ✅ | De bout en bout, app obligatoire pour écouter (ADR-0016) |
| S0.07 | Relecture des ADR 0008 à 0017 | ⏳ 👤 | Dernière étape de décision |
| — | Q4 PIN en trajet ; Q5 PIN de contrainte | ✅ | Obligatoire en trajet + biométrie ; pas de PIN de contrainte (ADR-0015) |
| — | Q8 : mode leurre et icône neutre | ✅ | V2 (ADR-0017) |

## Comptes et démarches (👤)

| Étape | Sujet | Statut |
|---|---|---|
| S0.08 | Supabase : organisation, projets staging et prod en plan Pro, `us-east-1`, PITR, `pg_cron` | 👤 ⏳ |
| S0.09 | Vonage : compte, expéditeur autorisé en Haïti, branché sur l'OTP Supabase ; test de 20 OTP Digicel et Natcom | 👤 ⏳ |
| S0.10 | Firebase : projet, FCM, compte de service | 👤 ⏳ |
| S0.11 | Google Play Console : compte, `SEND_SMS`, « Familles » | 👤 ⏳ |
| S0.12 | Juriste (avec la nouvelle question sur l'adulte référent) | 👤 ⏳ |
| S0.13 | Numéros d'urgence 114, 115 et 116 confirmés | 👤 ⏳ |
| S0.14 | Atelier mots-clés créole + protocole de collecte | 👤 ⏳ |
| S0.15–16 | Maquettes Figma + tests utilisateurs | 👤 ⏳ |
| S0.17 | Appareils de test | 👤 ⏳ |

## Prototypes (Claude peut les écrire ; tests sur vrais téléphones 👤)

| Étape | Prototype | Statut |
|---|---|---|
| S0.18 | Micro en arrière-plan + LiteRT | ⏳ |
| S0.19 | Audio chiffré en flux | ⏳ |
| S0.20 | SMS multipart Digicel et Natcom | ⏳ |
| S0.21 | GPS en trajet réel | ⏳ |
| S0.22 | supabase-kt : OTP + RPC + Realtime | ⏳ (nécessite S0.08 et S0.09) |

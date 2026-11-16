# ADR-0028 — Copie de secours des preuves sur le serveur

- Statut : **Acceptée** · Date : 2026-11-16 · Étape 1.15 (S6.03–S6.05, R-EVD-04/05, R-SY-04, R-NET-04) · Lié à ADR-0016, ADR-0021, ADR-0027

## Contexte
Les preuves n'existaient que sur le téléphone. Un téléphone volé, cassé ou effacé pendant une agression emporte tout. Il faut une copie hors du téléphone, sans que le serveur puisse écouter.

## Décisions
| Sujet | Décision |
|---|---|
| Ce qui part | Le fichier du segment **tel qu'il est sur le disque**, déjà chiffré par la clé de l'alerte (Tink, ADR-0021). Le serveur n'a jamais la clé. Les métadonnées chiffrées (`meta.bin`, 1.14) partent à la clôture |
| Rangement | Bucket Supabase Storage **privé** `evidence`, `<propriétaire>/<alerte>/<n°>.bin`, 2 Mo par objet au plus, `application/octet-stream` |
| Accès | RLS sur `storage.objects` : écrire seulement dans son dossier, pour un envoi ouvert (`begin_evidence_upload`) et pas échu ; lire seulement son dossier ; **aucun remplacement ni suppression** par le client. Partage aux proches : 1.16 (enveloppes de clés, ADR-0016) |
| Reprise | Un objet par segment de 30 s, jamais remplacé : après une coupure, seuls les segments manquants repartent (un doublon compte comme envoyé). Pas de TUS : des objets de ~120 Ko n'en ont pas besoin |
| Réseau (R-NET-04) | Pendant l'alerte et à sa clôture : tout de suite, n'importe quel réseau. Le reste : tâche WorkManager « réseau non facturé » (Wi-Fi) |
| Intégrité | À la clôture, racine de la chaîne signée ECDSA P-256 par la **clé d'appareil** `K_device` (Keystore, non exportable), déposée par `register_chain_root` et **horodatée par le serveur** ; elle ne change plus |
| Durée | La copie serveur suit la rétention choisie, **plafonnée à 30 jours**, y compris en « Manuel » (ADR-0027) : sur le serveur, rien n'est gardé sans limite. Sans clôture (téléphone détruit) : 30 jours |
| Suppression | Suppression locale (manuelle, échéance, effacement d'urgence) → `forget_evidence` ; compte supprimé → orphelins détectés. L'Edge Function `evidence-purge` (pg_cron, toutes les heures, secret `cr_cron_secret`) vide les dossiers par l'API Storage avec la clé `service_role`, puis retire la ligne |

## Conséquences
+ Un téléphone perdu n'efface plus les preuves (30 jours au plus) ; une fuite du serveur n'expose aucun audio.
− Après un effacement d'urgence, la copie serveur reste au plus une heure (illisible : la clé a été détruite sur le téléphone).
− Récupérer une copie serveur sur un nouveau téléphone demande la clé de l'alerte : à traiter avec la connexion sur un nouveau téléphone (1.19) ou le partage aux proches (1.16).
- Vérifié sur le vrai projet (comptes de test jetables, supprimés ensuite) : envoi par le propriétaire, doublon refusé, écriture d'un tiers refusée, lecture d'un tiers refusée, remplacement refusé, écriture refusée après échéance, purge par l'Edge Function (dossier vidé, ligne retirée).

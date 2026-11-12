# ADR-0024 — Surveillance du trajet par le serveur (perte de contact)

- Statut : **Acceptée** · Date : 2026-11-12 · S4.07, S4.08, S4.11 · R-BE-08, R-TRIP-06, R-LE-04 · Lié à ADR-0019, ADR-0022

## Contexte
Le moteur local ne peut rien faire si le téléphone est éteint, cassé, volé ou sans batterie. Pendant un trajet, c'est le moment
où les proches doivent être prévenus. Il faut donc un tiers qui remarque le silence.

## Décisions
| Sujet | Décision |
|---|---|
| Opt-in | Case « Prévenir mes proches si mon téléphone se tait » sur l'écran Trajet, **désactivée par défaut**, gardée pour les trajets suivants |
| Condition | Numéro **vérifié** par code SMS (`auth.users.phone_confirmed_at`), comme frein aux abus (SMS payants envoyés par le serveur). Sans numéro : le texte explique où le vérifier |
| Destinataires | Jusqu'à **3 proches ayant accepté** l'invitation (ADR-0022, V1), dans l'ordre de la liste. Les numéros STOP sont ignorés. Aucun proche accepté : pas de surveillance, l'écran le dit |
| Battement | Toutes les **2 min** : dernière position (si connue), précision, batterie. RPC `trip_heartbeat` (propriétaire seulement, `FOR UPDATE`) |
| Perte de contact | `pg_cron` chaque minute (`private.trip_watchdog_tick`) : **10 min** sans battement → `lost_at`. `pg_net` réveille l'Edge Function `trip-watchdog` (secret `cr_cron_secret` dans Vault, comparé en temps constant ; pas de JWT) qui envoie le SMS (Vonage, GSM-7, FR/HT/EN) : heure du dernier signal (heure d'Haïti), lien de carte vers la dernière position, « Essaie de l'appeler » |
| Retour | Un battement après un SMS de perte → un 2ᵉ SMS « de nouveau joignable ». **Une fois par trajet** pour chaque palier (R-BE-08) ; marqué dès qu'au moins un envoi réussit, sinon nouvel essai à la minute suivante |
| Pas d'alerte | Le serveur ne crée **pas** d'alerte et n'appelle personne : un silence n'est pas un danger (règle 3, règle 7). Le SMS dit seulement « plus de nouvelles depuis » |
| Données | Numéros chiffrés (`pgp_sym_encrypt`, clé `cr_trip_key` dans Vault), table `private.trip_recipients` jamais exposée à l'API. L'app ne lit de `trips` que des colonnes sans position. Fin du trajet (`end_trip`) : position effacée, numéros effacés ; trajet non terminé : arrêt automatique **2 h après l'arrivée prévue** (≤ 12 h), numéros effacés 1 h plus tard |
| Processus tué | Au redémarrage de l'app, un trajet serveur resté ouvert est clos (`end_trip`) pour éviter un faux SMS |
| Quotas | 10 trajets suivis par jour et par compte ; 3 destinataires ; 2 SMS par destinataire et par trajet au plus |
| `pg_net` | Installé dans le schéma `extensions` (Security Advisor) |

## Conséquences
- Un téléphone sans réseau pendant 10 min (zone blanche) déclenche aussi le SMS : c'est voulu, et le texte reste factuel.
- Le serveur voit la dernière position **pendant** le trajet suivi seulement (règle 4).
- Firebase (push) n'est pas utilisé : tout passe par SMS (Vonage).

## Reporté
- Lien de suivi en direct du trajet pour les proches (V4, S4.10).
- Dernier message à l'extinction (S4.09).

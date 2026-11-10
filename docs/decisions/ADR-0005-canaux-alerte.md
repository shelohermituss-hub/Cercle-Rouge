# ADR-0005 — Canaux d'alerte

- Statut : **Acceptée avec risques** · Date : 2026-11-10

## Contexte
Canaux choisis : SMS automatique, appel d'urgence, WhatsApp (+ notification et e-mail selon la spécification). La politique Play restreint `SEND_SMS` ; WhatsApp n'offre pas d'envoi automatique depuis un compte personnel ; Android n'autorise pas l'appel automatique aux numéros d'urgence.

## Décision
| Canal | Mise en œuvre |
|---|---|
| SMS automatique | `SmsManager` depuis le téléphone **si** l'exception Play est obtenue ; sinon SMS envoyé par le serveur (données requises), et hors ligne un SMS pré-rempli (1 tap) |
| Appel | Appel automatique d'un **proche** (`CALL_PHONE`) au niveau 3 ; numéros d'urgence via le composeur seulement |
| WhatsApp | Partage pré-rempli (1 tap) en V1 ; ❓ WhatsApp Business Platform côté serveur en V2 (modèles approuvés, coût) |
| Push | FCM (réveil seulement, contenu en HTTPS) |
| E-mail | Serveur, canal complémentaire |

Règle : au moins deux canaux indépendants par alerte (R-ALR-01).

## Conséquences
− Risque de rejet Play sur `SEND_SMS` → à soumettre tôt (❓ ou distribution d'un APK hors Play avec SMS direct).
− Coûts SMS et téléphonie côté serveur.

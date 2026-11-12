# ADR-0026 — Pas de Firebase

- Statut : **Acceptée** · Date : 2026-11-12 · Décision du porteur du projet · Remplace les mentions de FCM dans ADR-0011 et le plan S3.04/S3.05

## Décision
Cercle Rouge n'utilise **aucun service Firebase** : ni notifications push (FCM), ni Firebase App Distribution, ni environnements Firebase.

## Remplacements
| Besoin | Sans Firebase |
|---|---|
| Prévenir les proches | SMS du téléphone d'abord, puis SMS serveur (Vonage), e-mail, WhatsApp et appel automatisé (phase 2) |
| Proche qui a l'app | L'app interroge le serveur quand elle est ouverte ; un SMS avec le lien la réveille |
| APK de test | GitHub Releases (`.github/workflows/release.yml`, étiquette `v*`) |
| Environnements | Deux projets Supabase : staging et production |

## Conséquences
+ Aucun SDK Google de traçage ou d'analytics (R-ARCH-06) ; moins de données partagées.
+ Rien à configurer côté Google pour les tests.
− Pas de réveil instantané d'une app « proche » : le SMS reste le canal principal (déjà le cas en Haïti, où les données mobiles sont coûteuses).
− Les messages R-SY-03 (contenu des push) deviennent sans objet.

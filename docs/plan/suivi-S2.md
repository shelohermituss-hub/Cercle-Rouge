# Suivi du Sprint 2 — Alertes locales, hors ligne

Mis à jour le 2026-11-11. Légende : ✅ fait et vérifié · 🟡 fait, non vérifié sur téléphone · ⏳ à faire · 👤 dépend d'une action humaine

## Décisions du sprint

- **Q11 (SEND_SMS)** : envoi direct par `SmsManager` + **SMS pré-rempli en repli** si la permission est refusée ou absente (version Play sans l'exception). Le dossier d'exception Play reste à déposer (👤).
- **Ordre** : logique pure d'abord, puis persistance et écran, puis service de premier plan, file d'attente, SMS, widget.

## Étapes

| Étape | Statut | Note |
|---|---|---|
| S2.01 contacts-engine : E.164, groupes, rôles, langue, canaux | ✅ | Module Kotlin pur `:contacts-engine`, 51 tests ; numéro invalide refusé ; +509 par défaut |
| S2.02a SOS manuel vers les contacts non consentis | ✅ | `resolveRecipients` : SMS seul, mention + STOP ; jamais vers refusé/retiré |
| S2.02 Ordre d'alerte, `resolveRecipients` | ✅ | Niveau 2 : principal + backups, 3 max ; niveau 3 : tous ; 2 canaux, SMS d'abord sans données. Écran : boutons haut/bas (accessibles) au lieu du glisser-déposer |
| S2.03 Adulte référent contact par défaut | ✅ / 🟡 | Logique testée ; branché à la confirmation (simulée) de l'onboarding |
| S2.04 Service de premier plan, état persisté | 🟡 | `ProtectionService` (notification [SOS] [Désactiver], s'arrête à OFF) ; état écrit avant chaque effet et restauré (codec testé sur 3 000 événements). **Pas encore éprouvé 30 min écran éteint sur téléphone** |
| S2.05 Exécuteur d'effets, minuteries en Doze | 🟡 | Coroutine (exacte) + alarme `setAndAllowWhileIdle` (filet), identifiant de minuterie déterministe. Pas d'alarme exacte (Q16 ouverte). **Test adb en Doze à faire** |
| S2.06 File d'attente hors ligne persistante | ✅ / 🟡 | Règles testées (priorités, 2→60 s, idempotence) ; table `outbox` (migration 2→3 identique au schéma Room). Mort du processus pendant un envoi : à éprouver sur téléphone |
| S2.07 Canal SMS direct + pré-rempli | 🟡 | `SmsSender` multipart avec rapports d'envoi et de remise ; SMS pré-rempli si permission refusée. **Jamais essayé avec un vrai SMS** |
| S2.08 Gabarits FR/HT/EN | ✅ | GSM-7 vérifié par test CI ; **créole à relire par des locuteurs natifs** |
| S2.09 Position ponctuelle | ✅ (logique) / 🟡 (capture) | `chooseFix` testé ; `LocationProvider` (fraîche 10 s + dernière connue) non éprouvé sur téléphone |
| S2.10 Vérification « Es-tu en sécurité ? » | 🟡 | Écran avec compte à rebours, [Je vais bien] (PIN) et [J'ai besoin d'aide]. Vibration codée une fois (pas répétée toutes les 5 s) |
| S2.11 Anti-spam | ✅ | `SendLedger` : 1 alerte/contact/2 min, 12 SMS/alerte, 3 tests/jour |
| S2.12 Test d'alerte | 🟡 | Bouton « Test » par contact, même chemin, « [TEST] », 3 par jour |
| S2.13 SOS widget, tuile, notification | 🟡 | Tuile, widget (RemoteViews, sans Glance) et action de la notification ouvrent le compte à rebours de 5 s par-dessus l'écran verrouillé. Jamais d'envoi direct |
| S2.14 Appeler les secours (composeur 114) | ✅ | Déjà en place (S1) |
| Écran Contacts + persistance chiffrée | 🟡 | Base v2 (migration identique au schéma Room), sélecteur système sans permission, écran FR/HT/EN. **Pas encore vérifié sur un téléphone ni en capture** |

## Limites

- Sans serveur, un nouveau contact reste « en attente » : il ne reçoit que le SOS manuel. En version de test, un bouton « Simuler accepté » permet d'essayer.
- Rien de tout cela n'a été essayé sur un vrai téléphone (SMS réel, mode avion, Doze, mort du processus) : l'émulateur d'ici est trop lent. Les contrôles automatiques (lint, 130 tests) passent.
- Anti-spam : le plafond de 12 SMS compte 1 SMS par envoi réussi (approximation) ; le plafond exact par segment est testé en logique pure.
- Pas de lien de suivi dans les SMS avant le serveur (Sprint 3) : position en coordonnées brutes.
- Type de service `specialUse` provisoire, à remplacer par `microphone` et `location` aux Sprints 4 et 5.

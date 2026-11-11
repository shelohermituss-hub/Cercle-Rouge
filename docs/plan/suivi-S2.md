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
| S2.04 Service de premier plan, état persisté | ⏳ | Prochaine étape (S2-C) |
| S2.05 Exécuteur d'effets, minuteries en Doze | ⏳ | S2-C |
| S2.06 File d'attente hors ligne persistante | ⏳ | S2-C |
| S2.07 Canal SMS direct + pré-rempli | ⏳ | S2-D |
| S2.08 Gabarits FR/HT/EN | ✅ | GSM-7 vérifié par test CI ; **créole à relire par des locuteurs natifs** |
| S2.09 Position ponctuelle | ✅ (logique) / ⏳ (capture GPS) | `chooseFix` testé ; l'acquisition réelle arrive avec le service |
| S2.10 Vérification « Es-tu en sécurité ? » | ⏳ | S2-D |
| S2.11 Anti-spam | ✅ | `SendLedger` : 1 alerte/contact/2 min, 12 SMS/alerte, 3 tests/jour |
| S2.12 Test d'alerte | ⏳ | S2-D (gabarit « [TEST] » et plafond déjà prêts) |
| S2.13 SOS widget, tuile, notification | ⏳ | S2-D |
| S2.14 Appeler les secours (composeur 114) | ✅ | Déjà en place (S1) |
| Écran Contacts + persistance chiffrée | 🟡 | Base v2 (migration identique au schéma Room), sélecteur système sans permission, écran FR/HT/EN. **Pas encore vérifié sur un téléphone ni en capture** |

## Limites

- Sans serveur, un nouveau contact reste « en attente » : il ne reçoit que le SOS manuel. En version de test, un bouton « Simuler accepté » permet d'essayer.
- Aucun SMS ne part encore : le canal arrive en S2-D.

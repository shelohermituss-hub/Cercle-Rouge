# Suivi du Sprint 7 — Sécurité

Mis à jour le 2026-11-11. Légende : ✅ fait et vérifié · 🟡 fait, non vérifié sur téléphone · ⏳ à faire · ⏸ attend une autre étape

| Étape | Statut | Note |
|---|---|---|
| S7.01 5 PIN faux pendant une alerte → message aux proches | 🟡 | SMS « tentatives de désactivation » (FR/HT/EN, testé GSM-7 et 1 SMS) ; moteur déjà testé. PIN de contrainte : V2 |
| S7.02 Biométrie (classe Strong) | 🟡 | Désactivée par défaut ; « annuler une alerte » séparé, désactivé par défaut (ADR-0023) |
| S7.03 Fermeture forcée détectée par le serveur | ⏸ | Avec le battement et `notify-alert` ; au redémarrage, « Protection interrompue » existe déjà (S2) |
| S7.04 `FLAG_SECURE` sur les écrans sensibles | 🟡 | Urgence, Preuves, Réglages. Mode leurre : V2 |
| S7.05 Effacement d'urgence | 🟡 | Réglages + PIN ; clés des preuves détruites d'abord (testé : `wipeAll`) |
| S7.06 Guides batterie par marque | 🟡 | Consignes + bouton vers les réglages de batterie |
| S7.07 Console admin minimale | ⏳ | |
| S7.08 Modèle de menace, MASVS L2, test d'intrusion | ⏳ | |
| S7.09 Audit des permissions et SDK ; Security Advisor | 🟡 | Advisor revu au Sprint 3 (avertissements acceptés, ADR-0022) |

# Suivi du Sprint 6 — Preuves chiffrées (partie locale)

Mis à jour le 2026-11-11. Légende : ✅ fait et vérifié · 🟡 fait, non vérifié sur téléphone · ⏳ à faire · ⏸ attend le serveur ou une décision · 👤 dépend d'une action humaine

## Décisions du sprint (ADR-0021)

- **Q23** : enregistrement selon la spécification (niveau 2 ou SOS), **avis clair à l'onboarding** et interrupteur ; **avis juridique à obtenir avant le lancement** (👤).
- **Format** : AAC 32 kbit/s au lieu d'Opus.

## Étapes

| Étape | Statut | Note |
|---|---|---|
| S6.01 Enregistrement audio (niveau 2 / SOS, segments de 30 s, pré-tampon 5 s, 60 min) | 🟡 | MediaCodec AAC, même micro que l'écoute. **Jamais essayé sur un téléphone** |
| S6.02 Chiffrement en flux, une clé par alerte | ✅ | Test : aucun octet en clair dans le répertoire, noms hachés |
| S6.03 Chaîne SHA-256 | ✅ (local) / ⏸ (racine au serveur) | Altération d'1 octet détectée (module pur et fichier chiffré) |
| S6.04 Storage Supabase, envoi reprenable | ⏸ | Sprint 3 |
| S6.05 Priorités d'envoi | ⏸ | Sprint 3 |
| S6.06 Partage de bout en bout aux proches | ⏸ | Sprint 3 (clés publiques des proches) |
| S6.07 Coffre-fort | 🟡 | Onglet « Preuves » : PIN, `FLAG_SECURE`, écouter, vérifier, conserver 30/90 j, supprimer, exporter, interrupteur. Biométrie : plus tard |
| S6.08 Rétention + purge | ✅ (logique) / 🟡 (alarme) | Horloge accélérée → plus rien (test) ; alarme horaire + démarrage app et téléphone |
| S6.09 Export chiffré + outil de vérification | ✅ | Code à usage unique, `VerifyTool` hors ligne |
| Vidéo | V2 | — |

## À vérifier sur un vrai téléphone (👤)

1. SOS → laisser tourner 1 min → « Je vais bien » : dans « Preuves », un enregistrement d'environ 1 min 05 s (avec le pré-tampon si l'écoute était active), lisible.
2. « Vérifier » : intégrité OK.
3. Exporter : le fichier s'enregistre ; le code s'affiche une seule fois.
4. Choisir « Effacer à la fin de l'alerte » : l'enregistrement disparaît dans l'heure qui suit la clôture.
5. Couper l'interrupteur : un nouveau SOS n'enregistre rien.

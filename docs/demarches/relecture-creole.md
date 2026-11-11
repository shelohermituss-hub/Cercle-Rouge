# Relecture du créole (et du français)

Tous les textes ont été écrits par une IA : **la version créole doit être relue par des locuteurs natifs** avant tout test public.

## Quoi relire
1. **`relecture-creole.csv`** — les 281 textes de l'app (ouvrable dans Google Sheets ou Excel). Colonnes : `cle` (ne pas toucher), `francais`, `creole_actuel`, `anglais`, **`creole_corrige`** (à remplir si besoin), **`commentaire`**.
2. **Les SMS d'alerte** (`android/contacts-engine/src/main/kotlin/com/cerclerouge/contacts/Messages.kt`, objet `Ht`) : ce sont les messages les plus importants, lus par les proches en urgence. Contrainte : **alphabet GSM-7** (pas de ê, â, î, ô, û ; è et é sont permis) et le plus court possible (un SMS = 160 caractères).
3. **La page des proches** (`web/track/track.js`, bloc `ht:`).

## Consignes pour les relecteurs
- Privilégier un créole **parlé de tous les jours**, compréhensible par un jeune de 12 ans et par une personne âgée.
- Les messages d'urgence doivent être **courts et sans ambiguïté**.
- Garder les marques `%1$s`, `%1$d`, `%d` (elles sont remplacées par un prénom ou un nombre).
- Signaler aussi les erreurs en français et en anglais.
- Mots-clés de détresse (Q20) : la liste prévue est « Anmwe », « Ede m », « Lage m », « Sekou », « Au secours », « À l'aide », « Lâche-moi ». Lesquels se disent vraiment ? Lesquels manquent ? Variantes régionales ?

## Organisation suggérée
- 2 à 3 relecteurs indépendants (âges et régions différents), puis une mise en commun.
- Durée : 2 à 4 h chacun.

→ **Me renvoyer** le CSV rempli : j'intègre les corrections dans l'app, les SMS et la page.

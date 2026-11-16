# ADR-0027 — Rétention « Manuel » des preuves

- Statut : **Acceptée** · Date : 2026-11-16 · Étape 1.13 du plan · Amende ADR-0021 (Rétention) et le non-objectif N6 · Lié à ADR-0017

## Contexte
Les preuves étaient toujours supprimées automatiquement : 24 h, 7 j ou 30 j après la clôture de l'alerte, ou dès la clôture. Le porteur du projet a demandé une option supplémentaire : garder les preuves jusqu'à ce que l'utilisateur les supprime, par exemple le temps d'une plainte ou d'une procédure qui dépasse 30 jours.

## Décision
- Nouvelle valeur `Retention.MANUAL` : **aucune suppression automatique**. Les preuves partent seulement quand l'utilisateur les supprime dans le coffre-fort, par l'effacement d'urgence ou par la suppression du compte.
- Elle n'est **jamais présélectionnée**. Sans choix, la valeur reste 24 h (ADR-0017).
- Quand elle est choisie, un **avertissement visible** s'affiche à l'onboarding et dans les Réglages : conservation sans limite de temps, risque si le PIN est connu d'un tiers, rappel de supprimer, effacement d'urgence.
- Le coffre-fort indique « Gardée jusqu'à ce que tu la supprimes » au lieu d'une date.
- Le reste ne change pas : chiffrement par alerte, crypto-shredding, verrouillage par PIN, `FLAG_SECURE`.

## Conséquences
- N6 devient : « pas de conservation sans limite **par défaut** » ; une conservation illimitée n'existe que sur choix explicite et averti.
- Le stockage du téléphone peut grossir : un enregistrement fait au plus 60 min (≈ 15 Mo par alerte), et l'enregistrement s'arrête sous 20 Mo libres (R-EVD-08).
- À faire avant le lancement : le texte de l'avertissement doit passer par l'avis juridique déjà prévu pour Q23.

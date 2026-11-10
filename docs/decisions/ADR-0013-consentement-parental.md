# ADR-0013 — Consentement parental (12–15 ans)

- Statut : **Acceptée** · Date : 2026-11-10

## Décision
Pour un utilisateur de 12 à 15 ans, un **parent ou tuteur inscrit sur Cercle Rouge** confirme le consentement **depuis son propre téléphone**, dans l'application, après vérification de son numéro par OTP.

Flux détaillé : [produit/11-consentement-parental.md](../produit/11-consentement-parental.md).

## Garde-fous
- **La sécurité ne se bloque jamais** : en attendant la confirmation, le SOS manuel (SMS direct + composeur) reste disponible (N8, R-ONB-06).
- **Pas de surveillance parentale** : le parent ne voit ni la position, ni le journal, ni les trajets en dehors des alertes où il est contact de confiance (N9).
- Le numéro vérifié du parent doit correspondre au numéro saisi par le jeune, être différent du sien, et le parent atteste avoir 18 ans ou plus.
- À 16 ans (calculé à partir de l'année et du mois de naissance), le lien n'est plus exigé.

## Risque résiduel accepté
Un jeune peut faire confirmer par un adulte qui n'est pas son parent. Impossible à vérifier sans pièce d'identité, ce qui serait disproportionné. Ce point est documenté dans les conditions d'utilisation.

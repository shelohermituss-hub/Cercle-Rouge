# ADR-0013 — Consentement parental (12–15 ans)

- Statut : **Acceptée**, amendée le 2026-11-10 (Q3c, Q3d)

## Décision
Pour un utilisateur de 12 à 15 ans, un **adulte référent inscrit sur Cercle Rouge** confirme le consentement **depuis son propre téléphone**, dans l'application, après vérification de son numéro par OTP.

Flux détaillé : [produit/11-consentement-parental.md](../produit/11-consentement-parental.md).

## Amendements (Q3c, Q3d)
- **Adulte référent** (Q3d) : le parent en priorité, mais tout adulte inscrit peut confirmer (tuteur, membre de la famille, enseignant, voisin), car beaucoup de parents en Haïti n'ont pas de smartphone. L'écran du jeune dit « un parent ou un adulte de confiance ».
- **Contact par défaut** (Q3c) : l'adulte référent est **ajouté par défaut** comme contact de confiance du jeune. Sa confirmation vaut aussi acceptation de recevoir les alertes (texte explicite sur l'écran de confirmation). Le jeune peut le retirer à tout moment, **sans que l'adulte en soit averti** (protection en cas de violences intrafamiliales). Retirer l'adulte des contacts ne retire **pas** le consentement.

## Garde-fous
- **La sécurité ne se bloque jamais** : en attendant la confirmation, le SOS manuel (SMS direct + composeur) reste disponible (N8, R-ONB-06).
- **Pas de surveillance parentale** : le parent ne voit ni la position, ni le journal, ni les trajets en dehors des alertes où il est contact de confiance (N9).
- Le numéro vérifié du parent doit correspondre au numéro saisi par le jeune, être différent du sien, et le parent atteste avoir 18 ans ou plus.
- À 16 ans (calculé à partir de l'année et du mois de naissance), le lien n'est plus exigé.

## Risques résiduels acceptés
- Un jeune peut faire confirmer par un adulte qui n'est pas son parent : c'est désormais **voulu** (Q3d). L'adulte atteste sa majorité et son lien (« parent », « tuteur », « autre adulte de confiance ») ; le lien est enregistré.
- ⚠ **À valider par le juriste (S0.12)** : un consentement donné par un adulte qui n'est pas le représentant légal est-il suffisant pour la politique Play « Familles » et pour la loi haïtienne ? Si non, en V2 : consentement du représentant légal par code SMS, même sans smartphone.
- Un adulte malveillant pourrait se faire désigner : il ne reçoit que les alertes (pas de surveillance), et le jeune peut le retirer en silence.

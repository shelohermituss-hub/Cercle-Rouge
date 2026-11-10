# ADR-0008 — Formule du score de risque

- Statut : **Acceptée** · Date : 2026-11-10

## Décision
`Risk = w₁·A + w₂·B + w₃·C + w₄·D + w₅·E`. C'est une somme pondérée linéaire, avec Σwᵢ = 1, donc Risk ∈ [0, 1]. Les poids dépendent du profil (voir [ia/modele-ia-locale.md](../ia/modele-ia-locale.md#score-de-risque)).

## Garde-fous ajoutés
- E (contexte de vulnérabilité) n'est pas un signal : il ne compte pas dans la règle des deux signaux indépendants, et un score fait de E seul ne déclenche rien (R-RISK-01).
- w₅ ≤ 0,15 (R-RISK-02).
- Poids et seuils versionnés et journalisés (R-RISK-03).

## Conséquences
+ Simple, explicable (« 60 % mot-clé + 20 % déviation… »), réglable sans réentraînement.
− Linéaire : ne capte pas les interactions (cri **et** chute bien plus graves que chacun seul). C'est compensé par la règle des deux signaux ; un modèle de fusion appris pourra être étudié en V2 avec des données réelles.

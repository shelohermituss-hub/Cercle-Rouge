# ADR-0012 — Architecture UI : MVVM avec flux unidirectionnel

- Statut : **Acceptée** · Date : 2026-11-10

## Décision
Chaque écran a un `ViewModel` qui expose un `StateFlow<UiState>` immuable et reçoit des intentions (`onIntent(UiIntent)`). Pas de framework MVI complet.

## Raison
La logique critique est déjà une machine à états pure (`alert-engine:logic`, de type MVI). L'UI n'affiche que l'état dérivé (R-HOME-02). Un MVI complet dans l'UI dupliquerait ce modèle, avec plus de code et sans bénéfice (`/ponytail`).

# ADR-0004 — Langues : français, créole haïtien, anglais

- Statut : **Acceptée** · Date : 2026-11-10

## Décision
- Interface, messages d'alerte, politique de confidentialité et mots-clés de détresse en **FR (par défaut), HT (`ht`) et EN**.
- Ressources Android `values/` (fr), `values-ht/`, `values-en/` ; contrôle CI des clés manquantes.
- Chaque proche reçoit les alertes dans **sa** langue.
- Le créole est relu par des locuteurs natifs ; les mots-clés de détresse HT sont validés sur le terrain.

## Conséquences
− Trois jeux de données KWS ; le créole est une langue peu dotée → risque de qualité moindre, mesuré séparément (équité, voir ia/).

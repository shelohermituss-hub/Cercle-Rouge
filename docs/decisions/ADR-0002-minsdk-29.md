# ADR-0002 — minSdk 29 (Android 10)

- Statut : **Acceptée** · Date : 2026-11-10

## Contexte
Il fallait choisir entre l'API 26 (couverture maximale, appareils anciens fréquents en Haïti) et des versions plus récentes.

## Décision
minSdk **29**.

## Conséquences
+ Stockage restreint natif, gestion de la concurrence micro (Android 10), API de confidentialité plus récentes, moins de branches de compatibilité.
− Exclut les appareils sous Android 8 et 9. ❓ À mesurer sur le marché cible (statistiques des opérateurs ou de terrain) ; on pourra réévaluer.

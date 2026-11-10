# ADR-0003 — IA embarquée avec LiteRT (TensorFlow Lite)

- Statut : **Acceptée** · Date : 2026-11-10

## Contexte
L'analyse audio doit être 100 % locale (P2), sur des appareils d'entrée de gamme, avec des modèles personnalisés (KWS en créole).

## Décision
- Moteur principal : **LiteRT** (CPU/XNNPACK par défaut ; GPU ou NNAPI optionnels).
- Classification audio de départ : YAMNet quantifié, puis réapprentissage d'une tête sur nos classes.
- ONNX Runtime admis **uniquement** si un modèle clé (ex. Silero VAD) n'est pas convertible ; à justifier par une mesure.
- Fusion du risque par **règles** en V1 (pas de ML).
- Modèles embarqués dans l'AAB ; pas de téléchargement dynamique en V1.

## Conséquences
+ Hors ligne, faible latence, pas de flux audio vers un serveur.
− Taille de l'APK (+5 à 10 Mo) ; collecte de données pour le créole nécessaire.

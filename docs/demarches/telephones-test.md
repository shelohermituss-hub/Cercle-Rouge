# Téléphones de test

Objectif : couvrir les marques les plus vendues en Haïti et les versions d'Android que nos utilisateurs ont (minimum Android 10). Les marques chinoises d'entrée de gamme ferment souvent les apps en arrière-plan : ce sont les plus importantes à tester.

| Priorité | Marque (exemples de modèles) | Android | Pourquoi |
|---|---|---|---|
| 1 | **Tecno** (Spark, Pop) | 10 à 14 | Très répandu ; HiOS ferme les apps en arrière-plan |
| 1 | **Infinix** (Hot, Smart) | 11 à 14 | Même groupe que Tecno, XOS |
| 1 | **Itel** (A-series) | 10 à 13 | Entrée de gamme, peu de mémoire |
| 2 | **Samsung** (Galaxy A0x/A1x) | 11 à 15 | Gestion de batterie agressive (« mise en veille ») |
| 2 | **Xiaomi / Redmi** | 11 à 15 | MIUI/HyperOS : autorisations « démarrage automatique » |
| 3 | Un téléphone **sans Google Play Services** (si disponible) | — | Repli position et absence de push (Q15) |

**Minimum pour commencer** : 1 Tecno ou Infinix + 1 Samsung, avec une puce **Digicel** et une puce **Natcom**.

Pour chaque téléphone, noter : marque, modèle, Android, mémoire, opérateur. Utiliser la fiche `tests-telephone.md`.

Plus tard (S8) : Firebase Test Lab pour élargir à 20–50 modèles sans les acheter.

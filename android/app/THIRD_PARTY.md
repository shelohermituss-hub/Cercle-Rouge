# Composants tiers de l'application

| Composant | Licence | Usage |
|---|---|---|
| MapLibre Native Android | BSD-2-Clause | Carte de l'accueil (ADR-0018) |
| Données OpenStreetMap via OpenFreeMap / OpenMapTiles | ODbL / CC-BY | Tuiles ; attribution « © OpenMapTiles © OpenStreetMap » affichée sur la carte |
| OrbMotion (Valentine Rutto) | MIT | Orbes à points |
| Thinking Orbs (Jakub Antalik), dont OrbMotion est un portage | MIT | — |
| Inter, JetBrains Mono | SIL OFL 1.1 | Polices (voir FONTS.md) |
| YAMNet (Google, `yamnet.tflite`, classes AudioSet) | Apache 2.0 | Familles de sons reconnues dans le téléphone (IA.2), SHA-256 vérifié au chargement |
| LiteRT (`com.google.ai.edge.litert:litert`) | Apache 2.0 | Exécution de YAMNet dans le téléphone |
| LiteRT-LM (`com.google.ai.edge.litertlm:litertlm-android`) | Apache 2.0 | Exécution du modèle de langage dans le téléphone (IA.5) |
| Gemma 3 270M (Google), fichier `.litertlm` téléchargé ou importé, non inclus dans l'APK | Conditions d'utilisation de Gemma (ai.google.dev/gemma/terms) et politique d'usage interdit | Modèle de langage du veilleur ; la redistribution doit transmettre ces conditions (rappelées dans Réglages › Cerveau IA) |
| Icônes en verre, bouclier 3D, orbe « AI Voice Assistant », animation Face ID | Fournis par le porteur du projet | Design « Verre » (droits d'usage à confirmer par le porteur avant publication) |

Les licences MIT et BSD exigent de reproduire l'avis de copyright : à afficher dans un écran « Licences » des Réglages avant la publication.

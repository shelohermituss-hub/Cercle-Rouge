# Rapport des prototypes — Sprint 0

## 1. Essais sur émulateur (2026-11-10)

Émulateur Android 14 (API 34, x86_64) **sans accélération matérielle** : les essais valident que le code fonctionne de bout en bout, **pas** les performances ni la batterie. Les temps mesurés ici sont 10 à 50 fois plus lents qu'un vrai téléphone et ne doivent pas être retenus.

| Prototype | Résultat | Constat |
|---|---|---|
| S0.18 Micro + IA | ✅ Fonctionne | Service de premier plan de type `microphone` actif (`types=0x80`), notification visible. YAMNet se charge (entrée `[15600]`, sortie `[1, 521]`). Inférence vérifiée sur des signaux de test : sinusoïde de 1 kHz → « Beep, bleep » à 0,74 ; bruit blanc → « Static / Noise ». Le micro de l'émulateur étant muet, la porte d'énergie ne s'ouvre jamais (comportement attendu). |
| S0.19 Audio chiffré | ✅ Fonctionne | Opus (encodeur `c2.android.opus.encoder`) → Tink StreamingAead → déchiffrement → décodage → lecture : OK. `fuite_en_clair=false`. Seul fichier créé : le fichier chiffré (plus `profileInstalled`, un fichier technique d'AndroidX, désormais exclu de la mesure). L'entropie (7,6–7,7) est sous-estimée sur un fichier de moins d'1 Ko ; le seuil > 7,9 vaut pour les 30 s du vrai test. |
| S0.20 SMS | ✅ Fonctionne | Envoi + accusés d'envoi et de remise reçus pour chaque segment : message court, long (2 segments), UCS-2 simple et multiple, message d'alerte complet (4 segments). Un premier essai a échoué avec le code 31 juste après le démarrage de l'émulateur (radio pas encore prête) ; il n'a pas été reproduit. |
| S0.21 GPS | ✅ Fonctionne | Repli `LocationManager` (l'émulateur n'a pas Google Play Services) : une position toutes les 9 à 11 s, coordonnées simulées sur Port-au-Prince suivies correctement. |

### Découverte : l'encodage des SMS

Le message d'alerte d'essai faisait **4 SMS** au lieu de 2, à cause du tiret long (—) et du « ê » de « peut-être », qui forcent l'encodage UCS-2 (70 caractères par segment au lieu de 153).
- é è à ù ì ò et Ç font partie de l'alphabet GSM-7 : **le créole passe sans surcoût**.
- ê â î ô û, « », ’ et — font basculer tout le message.

Conséquence ajoutée à la spécification : **R-ALR-08**, les gabarits SMS restent en GSM-7 et un test CI le vérifie (moins de segments, donc moins de coût et moins de risque de perte).

## 2. Mesures sur vrais téléphones

⏳ À faire selon [protocole-prototypes-S0.md](protocole-prototypes-S0.md), sur au moins 4 téléphones. Les CSV partagés depuis l'application seront compilés ici.

| Téléphone | Android | S0.18 survie 2 h | S0.18 %/h | S0.18 ms moy. | S0.19 | S0.20 Digicel | S0.20 Natcom | S0.21 précision | S0.21 %/h |
|---|---|---|---|---|---|---|---|---|---|
| | | | | | | | | | |

## 3. Décision go / no-go

⏳ Après les mesures réelles.

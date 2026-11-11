# ADR-0021 — Preuves chiffrées sur le téléphone : AAC, chaîne d'intégrité, purge par alarme

- Statut : **Acceptée** · Date : 2026-11-11 · Sprint 6 (S6.01 à S6.03, S6.07 à S6.09) · Lié à Q23, ADR-0007, ADR-0016, ADR-0017

## Contexte
Le Sprint 6 enregistre des preuves pendant une alerte. La partie serveur (envoi, partage aux proches, ADR-0016) attend Supabase (Sprint 3). Q23 (légalité d'enregistrer des tiers en Haïti) reste ouverte : le porteur du projet choisit d'appliquer la spécification avec un avis clair, en attendant un avis juridique.

## Décisions
| Sujet | Décision |
|---|---|
| Déclenchement | Effet `StartRecording` du moteur : **niveau 2 ou SOS seulement** ; arrêt à la clôture. Activé par défaut, **interrupteur** dans « Preuves » |
| Avis (Q23) | À l'onboarding (écran de durée de conservation) : le micro peut capter d'autres voix, l'enregistrement reste chiffré sur le téléphone, sa valeur devant un juge n'est pas garantie, il peut être coupé. Rappel dans le coffre-fort. **Avis d'un juriste à obtenir avant le lancement** |
| Capture | Le même `AudioRecord` que l'écoute IA (un seul micro ouvert) ; **pré-tampon de 5 s** en mémoire (R-AE-01), joint au début de l'enregistrement |
| Format | **AAC-LC 32 kbit/s mono 16 kHz en trames ADTS** (MediaCodec), au lieu d'Opus (R-EVD-07) : Opus demande un conteneur Ogg écrit à la main pour rester dans un flux chiffrant ; AAC/ADTS se lit trame par trame. ≈ 240 Ko/min au lieu de 180. Opus reste possible plus tard sans toucher au reste |
| Segments | 30 s, plafond de 60 min ; chaque segment reste **en mémoire** jusqu'à son écriture |
| Chiffrement | Tink `StreamingAead` AES-256-GCM-HKDF (segments de 1 Mo), **une clé par alerte** ; keyset chiffré par une clé maîtresse non exportable du Keystore, rangé dans la base SQLCipher. Données associées : alerte + numéro de segment. Noms de dossiers hachés |
| Intégrité | SHA-256 du contenu en clair avant chiffrement ; chaîne `H(prev ‖ alerte ‖ n° ‖ début ‖ durée ‖ hachage)` (S6.03). Envoi et contresignature de la racine : Sprint 3 |
| Suppression | Crypto-shredding : clé, puis fichiers, puis métadonnées ; fichiers orphelins retirés à la purge suivante (R-EE-02, R-EE-05) |
| Rétention | Choix de l'onboarding (24 h par défaut) **après la clôture** ; alerte jamais close : dernière écriture + 1 h ; « Conserver » : 30 ou 90 jours au plus |
| Purge | **Alarme inexacte toutes les heures** + rattrapage au démarrage de l'app et du téléphone (R-EVD-03). Pas de WorkManager : même garantie ici, sans dépendance de plus |
| Coffre-fort | PIN (avec l'animation biométrique), `FLAG_SECURE`, verrouillé en quittant ; écouter (déchiffré en mémoire), vérifier, conserver, supprimer, exporter |
| Export | Archive AES-256-GCM, clé PBKDF2-HMAC-SHA256 (210 000 tours) d'un **code à usage unique** de 12 caractères montré une seule fois ; outil de vérification hors ligne `VerifyTool` (Java) |

## Reporté
- S6.04, S6.05, S6.06 (envoi, priorités réseau, partage de bout en bout aux proches) : avec Supabase.
- Signature de la racine par `K_device` : avec l'enregistrement des appareils (Sprint 3).
- Déverrouillage par `BiometricPrompt` : PIN seulement pour l'instant.
- Vidéo : V2.

## Conséquences
- Rien en clair sur le disque, même temporairement (vérifié par test sur le répertoire).
- Le PIN protège le coffre-fort, mais un téléphone déverrouillé et ouvert sur l'app pendant une alerte reste un risque : à revoir au Sprint 7 (effacement d'urgence, mode leurre en V2).

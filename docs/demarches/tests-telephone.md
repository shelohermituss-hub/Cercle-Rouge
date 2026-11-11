# Fiche de tests sur téléphone (phase 1)

APK : la dernière version de test envoyée (Sprint 3, synchronisation). Coche chaque ligne : ✅ OK · ❌ problème (note ce qui s'est passé) · ⏭ pas testé.

**Avant de commencer**
- [ ] GitHub Pages activé (voir `comptes-a-ouvrir.md`, étape 1), sinon le lien du SMS n'ouvre rien.
- [ ] Un 2ᵉ téléphone (le tien ou celui d'un proche) pour recevoir les SMS.
- [ ] Du crédit SMS sur le téléphone de test.
- [ ] Noter : marque, modèle, version d'Android, opérateur (Digicel / Natcom).

Si l'app plante : rouvre-la, une fenêtre propose de **partager la trace du plantage** → envoie-la-moi.

## A. Installation et réglages (10 min)
| # | Faire | Attendu | Résultat |
|---|---|---|---|
| A1 | Installer, faire l'onboarding jusqu'au bout (choisir une durée de conservation, un PIN) | Arrivée sur l'accueil, pas de fermeture | |
| A2 | Contacts → ajouter le 2ᵉ téléphone ; « Simuler accepté » | Le contact apparaît, statut accepté | |
| A3 | Contacts → bouton « Test » | Le 2ᵉ téléphone reçoit un SMS « [TEST] » | |

## B. SOS et page des proches (15 min)
| # | Faire | Attendu | Résultat |
|---|---|---|---|
| B1 | Accueil → appui long sur SOS → laisser le compte à rebours finir | Écran d'urgence ; le 2ᵉ téléphone reçoit un SMS avec un lien `…github.io/Cercle-Rouge/#t=…` en moins d'1 min | |
| B2 | Sur le 2ᵉ téléphone, ouvrir le lien | Page « [prénom] a besoin d'aide », carte avec la position | |
| B3 | Sur la page, « J'ai pris contact » | Dans l'app, en 15 s : « [contact] a pris contact » | |
| B4 | Dans l'app, « Je vais bien » + PIN | Le 2ᵉ téléphone reçoit « … byen / va bien » ; la page affiche « va bien », sans position | |
| B5 | Refaire B1 en **mode avion** sur le téléphone de test, puis le couper au bout d'1 min | Le SMS part au retour du réseau, une seule fois ; sans lien si le serveur n'a pas répondu (coordonnées à la place) | |
| B6 | Retirer la permission SMS (Réglages Android) puis SOS | L'app de SMS s'ouvre avec le message pré-rempli | |

## C. Écran éteint (1 h, pendant que tu fais autre chose)
| # | Faire | Attendu | Résultat |
|---|---|---|---|
| C1 | Activer la protection, verrouiller, laisser 30 min | La notification « Protection active » est toujours là | |
| C2 | Toujours verrouillé : tuile SOS (réglages rapides) ou widget | Le compte à rebours s'ouvre par-dessus l'écran verrouillé | |
| C3 | Noter le % de batterie au début et après 1 h de protection | Écrire les deux chiffres | |

## D. Trajet (20 min de marche)
| # | Faire | Attendu | Résultat |
|---|---|---|---|
| D1 | Trajet → toucher la carte pour la destination, « À pied », démarrer (PIN) | Écran « Trajet en cours », distance qui baisse | |
| D2 | Marcher normalement, écran éteint | Aucune vérification « Es-tu en sécurité ? » injustifiée | |
| D3 | S'arrêter 12 min sans « Je fais un arrêt » | Vérification « Es-tu en sécurité ? » | |
| D4 | Arrivé : « Je suis arrivé·e » + PIN | Fin du trajet | |
| D5 | Batterie avant / après | Écrire les deux chiffres | |

## E. Phrase secrète (15 min)
| # | Faire | Attendu | Résultat |
|---|---|---|---|
| E1 | Bouton « Écoute IA » → enregistrer une phrase (3 fois) | « Phrase enregistrée » | |
| E2 | « Tester ma phrase » avec la bonne phrase, puis avec une autre | Reconnue / pas reconnue | |
| E3 | Protection active, dire la phrase à 1 m du téléphone | Alerte **silencieuse** (rien à l'écran), SMS reçu par le proche | |
| E4 | 1 h de conversation normale, protection active | Aucune fausse alerte | |
| E5 | Recevoir un appel pendant la protection | « Écoute IA en pause », puis reprise | |

## F. Preuves (10 min)
| # | Faire | Attendu | Résultat |
|---|---|---|---|
| F1 | SOS, attendre 1 min, « Je vais bien » | Onglet « Preuves » (PIN) : un enregistrement d'environ 1 min | |
| F2 | « Écouter », puis « Vérifier » | Le son est audible ; « Intégrité vérifiée » | |
| F3 | « Exporter » → enregistrer le fichier | Un code de 12 caractères s'affiche une fois ; le fichier est enregistré | |
| F4 | Couper « Enregistrer pendant une alerte », refaire un SOS | Aucun nouvel enregistrement | |

## À me renvoyer
Le tableau rempli (une photo suffit), les traces de plantage, les chiffres de batterie, et ton ressenti sur ce qui était lent, confus ou mal traduit.

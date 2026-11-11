# Collecte d'enregistrements en créole (Q19)

**But** : entraîner la détection des mots de détresse en créole et en français (S5.04), et calibrer l'écoute contre les bruits d'Haïti (S5.10, S5.12). Sans ces enregistrements, l'app ne reconnaît que la phrase secrète de chaque utilisateur.

## Partenaire
Une université (linguistique, informatique), une ONG ou une radio communautaire, capable de recruter des volontaires et de garder les fichiers en sécurité. Budget indicatif à prévoir : rémunération des participants et des enregistreurs.

## Quoi enregistrer
| Lot | Contenu | Volume visé |
|---|---|---|
| Mots-clés | Chaque mot de la liste validée (voir `relecture-creole.md`), dit 10 fois sur des tons différents (normal, chuchoté, crié, essoufflé) | 200 à 400 personnes |
| Phrases neutres | Phrases courantes contenant des mots proches (« sekou » dans une autre phrase…) pour apprendre ce qui **n'est pas** une alerte | Mêmes personnes |
| Ambiances | Tap-tap, marché, rara, église, pluie sur la tôle, rue, école, maison ; 10 à 30 min chacun, sans conversation identifiable | 20 h au total |

Diversité : âges (12 ans et plus, avec accord parental pour les mineurs), genres, régions, créole et français. Téléphones d'entrée de gamme (les mêmes que nos utilisateurs).

## Règles
- **Consentement écrit** de chaque participant (formulaire ci-dessous), parental pour les mineurs.
- Aucun nom dans les fichiers : un code par participant ; âge en tranche, genre, région seulement.
- Stockage chiffré, accès limité ; durée de conservation fixée (ex. 5 ans) ; suppression sur demande.
- Usage limité à l'entraînement et à l'évaluation des modèles de Cercle Rouge ; pas de revente.

## Formulaire de consentement (brouillon, à faire valider par le juriste et traduire)
> J'accepte d'être enregistré(e) pour aider à créer Cercle Rouge, une application de sécurité. Mes enregistrements serviront uniquement à apprendre à l'application à reconnaître des appels à l'aide. Mon nom ne sera pas associé aux enregistrements. Je peux demander leur suppression à tout moment en donnant mon code participant. Ils seront conservés au plus ___ ans, de façon sécurisée.
> Code participant : ____ · Tranche d'âge : ____ · Date : ____ · Signature : ____
> (Mineur) Nom et signature du parent ou tuteur : ____

## Format technique
WAV ou FLAC, 16 kHz, mono, 16 bits ; un fichier par mot ; nommage `code_mot_ton_numero.wav` ; tableau de métadonnées (CSV) livré avec.

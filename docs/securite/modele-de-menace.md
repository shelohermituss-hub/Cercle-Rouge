# Modèle de menace

Méthode : STRIDE par acteur, centrée sur les personnes. Les mesures renvoient aux règles des autres documents.

## Actifs à protéger

1. **La vie et l'intégrité physique de l'utilisateur** (l'alerte doit partir).
2. La position (en temps réel et historique).
3. L'audio, la vidéo et les preuves.
4. Les contacts de confiance et leurs numéros.
5. Le fait même d'utiliser Cercle Rouge (le révéler peut mettre en danger).
6. L'intégrité des preuves (non-altération, horodatage).

## Acteurs malveillants

| Acteur | Capacités | Objectifs |
|---|---|---|
| **Agresseur présent physiquement** | A le téléphone en main, peut forcer le déverrouillage (PIN observé, doigt) | Arrêter l'alerte, effacer les preuves, désinstaller l'app |
| **Partenaire ou proche abusif** | Accès régulier au téléphone, connaît parfois le PIN | Détourner l'app pour **surveiller** la victime ; savoir si elle a l'app |
| **Contact de confiance malveillant** | Reçoit les alertes et les liens | Suivre la personne au-delà de l'alerte, annuler une alerte |
| **Personne ayant obtenu un lien de suivi** | URL transférée ou interceptée | Localiser l'utilisateur |
| **Attaquant réseau** | Wi-Fi public, opérateur | Intercepter ou modifier les alertes |
| **Attaquant serveur** | Fuite de base de données, compte cloud compromis | Positions, numéros, preuves en masse |
| **Abuseur du service** | Script, comptes multiples | Spam SMS vers des tiers, coûts, harcèlement via les « alertes » |
| **Initié** | Employé ou prestataire | Consulter des alertes ou des positions |

## Menaces et mesures

| # | Menace | Mesures | Réf. |
|---|---|---|---|
| T1 | L'agresseur désarme la protection | PIN pour désarmer pendant une alerte ou un trajet ; PIN de contrainte ; notification « protection désactivée » aux proches ; journal chaîné | R-SM-04, R-SEC-02/03, P8 |
| T2 | L'agresseur éteint le téléphone ou tue l'app | Dernier message à l'extinction ; perte de contact détectée par le serveur ; preuves déjà envoyées | R-TRIP-05, module 9 |
| T3 | L'agresseur supprime les preuves locales | Envoi progressif dès le niveau 2 ; la racine de la chaîne est sur le serveur | R-EVD-04 |
| T4 | **Détournement en outil de surveillance (stalkerware)** | Armement uniquement par le détenteur ; indicateur micro et notification permanents ; pas de mode caché ; pas de suivi en dehors des alertes ou trajets ; attestation à l'onboarding ; contacts limités à l'alerte active | N2, N9, R-DASH-01 |
| T5 | Révéler l'usage de l'app | Mode leurre ; notifications neutres sur l'écran de verrouillage ; ❓ alias d'icône neutre | R-SEC-05/06 |
| T6 | Contact malveillant qui suit ou annule | Liens à durée limitée ; « Alerte fausse » d'un proche ne clôt pas l'alerte ; consultations journalisées et visibles par l'utilisateur ; retrait d'un contact effectif immédiatement | R-DASH-02/03, R-ESC-03 |
| T7 | Lien de suivi fuité | Jeton ≥ 128 bits, TTL ≤ 6 h, révocable ; ❓ code SMS séparé ; `noindex`, `no-referrer` | R-DASH-04 |
| T8 | Interception réseau | TLS 1.2+ avec épinglage ; requêtes signées par une clé du Keystore ; SMS : contenu minimal (le SMS n'est pas chiffré, c'est une limite connue) | R-NET-07, R-SY-02 |
| T9 | Fuite du serveur | Minimisation ; rétention courte ; preuves chiffrées côté client ; numéros chiffrés au niveau applicatif ; ❓ chiffrement de bout en bout des preuves vers les proches | R-BE-*, ADR-0007 |
| T10 | Abus et spam SMS | Consentement du contact ; limites par appareil et par contact ; STOP ; Play Integrity ; surveillance des volumes | R-CON-03, R-ALR-06, R-BE-05 |
| T11 | Initié | Pas d'accès au contenu ; accès support limité dans le temps et journalisé | R-ADM-* |
| T12 | Faux positifs répétés (épuisement des proches) | Anti-faux positifs ; période réfractaire ; statistiques de fausses alertes visibles par l'utilisateur | alert-engine |
| T13 | Faux négatifs (danger non détecté) | Ne jamais promettre la détection ; le SOS est toujours disponible ; l'utilisateur est informé des limites | N1 |
| T14 | Téléphone volé hors alerte | Données chiffrées au repos ; coffre protégé par la biométrie ou le PIN ; révocation de l'appareil depuis un autre appareil (❓) | R-EVD-02 |

## Risques résiduels acceptés (à communiquer)

- Un agresseur qui connaît le PIN **et** le PIN de contrainte peut désarmer silencieusement.
- Le SMS circule en clair sur le réseau de l'opérateur.
- Un arrêt forcé de l'app n'est pas détectable localement ; on ne le détecte que par l'absence de battement, avec un délai.
- La détection audio peut manquer un danger réel.

# Play Console : `SEND_SMS` et « Sécurité des données » (Q11)

⚠ **À vérifier avant de déposer** : je n'ai pas pu ouvrir la page officielle depuis mon environnement. D'après une version antérieure de la règle « SMS et journal d'appels », `SEND_SMS` faisait partie des exceptions pour les **alertes de sécurité physique / d'urgence** ; les exceptions sont accordées **au cas par cas**, et une version 2026 de la règle est en cours de déploiement. Ouvre le Centre d'aide Play Console → « Use of SMS or Call Log permission groups » et vérifie que la catégorie « safety / emergency » existe toujours.

**Plan B déjà en place dans l'app** : si Play refuse, la version Play retire `SEND_SMS` et ouvre l'app de SMS avec le message **pré-rempli** (un geste de plus pour l'utilisateur) ; les SMS de secours passent par le serveur (fournisseur SMS).

## 1. Déclaration de l'autorisation (à coller, en anglais : la console est relue en anglais)

**Core functionality / use case** : Safety and emergency — sending SMS alerts to user-designated emergency contacts.

**Description** :
> Cercle Rouge is a personal safety app for people in Haiti, where mobile data is often unavailable or unaffordable. When the user triggers an SOS (long press, quick-settings tile, widget, or a secret phrase they recorded), or when a protected trip or an emergency escalates and the user does not answer a safety check, the app sends an SMS alert to up to 3 trusted contacts that the user chose and who accepted to receive alerts. The SMS contains the user's first name, the reason, the time and a link to a temporary tracking page (or GPS coordinates when offline). SMS is the only channel that works without internet in our target market; delay can put a life at risk. The app never reads SMS, never sends to contacts the user did not add, rate-limits sends (1 alert per contact per 2 minutes, 12 SMS per alert, 3 tests per day), and shows a persistent notification while protection is active. If SEND_SMS is denied, the app opens the SMS app with a prefilled message instead.

**Vidéo de démonstration** (à tourner, 1 à 2 min) : ouvrir l'app → montrer l'ajout d'un contact → appui long sur SOS → compte à rebours de 5 s → SMS reçu sur un 2ᵉ téléphone → « Je vais bien » → SMS de fin. Mettre en ligne sur YouTube en « non répertoriée ».

## 2. Autres autorisations à déclarer
| Autorisation | Justification courte |
|---|---|
| `FOREGROUND_SERVICE_MICROPHONE` | Écoute locale de la phrase secrète pendant la protection, démarrée par l'utilisateur, notification visible ; aucun audio ne quitte le téléphone. Vidéo : activer la protection, montrer la notification et l'indicateur micro |
| `FOREGROUND_SERVICE_LOCATION` | Trajet protégé et position pendant une alerte, écran éteint ; pas de localisation en arrière-plan. Vidéo : démarrer un trajet |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Minuteries d'escalade d'une alerte en cours (Android 14+) |
| `ACCESS_FINE_LOCATION` | Position pendant un trajet ou une alerte, partagée aux proches seulement pendant l'alerte |
| `RECORD_AUDIO` | Phrase secrète (analyse locale) ; enregistrement chiffré pendant une alerte (désactivable) |

## 3. Sécurité des données (réponses proposées)
| Question | Réponse |
|---|---|
| Collecte ou partage de données ? | Oui |
| Chiffrées en transit ? | Oui (HTTPS) |
| L'utilisateur peut demander la suppression ? | Oui (dans l'app, et compte supprimé côté serveur) |
| **Position précise** | Collectée ; partagée avec les proches choisis **pendant une alerte** ; finalité : fonctionnalité de l'app (sécurité) ; pas de publicité |
| **Nom (prénom)** | Collecté ; partagé avec les proches ; fonctionnalité |
| **Numéros des contacts de confiance** | Restent sur le téléphone (non collectés par le serveur pour l'instant) |
| **Enregistrements audio** | Restent sur le téléphone, chiffrés ; non collectés (le partage aux proches viendra plus tard : mettre à jour alors) |
| Identifiants, publicité, analytics | Aucun |
| Données d'un mineur | L'app accepte les 12 ans et plus, avec consentement d'un adulte référent pour les 12–15 ans → remplir la section « Public cible » honnêtement (pas d'inscription au programme « Familles » sans l'avis du juriste) |

## 4. Fiche Play (brouillon)
- **Titre** : Cercle Rouge — Sécurité personnelle
- **Description courte** (80 caractères) : SOS discret, trajets protégés et alerte de tes proches, même sans Internet.
- **À éviter** (règles Play et honnêteté) : « prévient les agressions », « preuve légale », « la police est prévenue automatiquement ».

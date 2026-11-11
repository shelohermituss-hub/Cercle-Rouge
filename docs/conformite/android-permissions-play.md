# Permissions Android et règles Google Play

> Ces règles changent régulièrement. **À revérifier avant chaque version** sur la documentation officielle Android et sur le Centre de règles Google Play. Cible : minSdk 29, targetSdk = niveau exigé par Play au moment de la publication.

## Manifeste prévu

| Permission | Type | Usage | Justification Play |
|---|---|---|---|
| `RECORD_AUDIO` | Dangereuse (runtime) | Écoute IA, preuves audio | Déclaration de la fonction principale, vidéo de démonstration si demandée |
| `FOREGROUND_SERVICE` | Normale | `ProtectionService` | — |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Normale (API 34+) | Type provisoire du `ProtectionService` au Sprint 2 (aucun micro ni position encore) ; remplacé par `microphone` et `location` aux Sprints 4 et 5 | Déclaration du type « usage spécial » et de sa justification (propriété `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`) dans la Play Console, à retirer ensuite |
| `FOREGROUND_SERVICE_MICROPHONE` | Normale (API 34+) | Type de service `microphone` | Déclaration des types de service dans la Play Console |
| `FOREGROUND_SERVICE_LOCATION` | Normale (API 34+) | Type de service `location` : **déclaré au Sprint 4** (`specialUse\|location`), demandé au démarrage du service seulement si la localisation est accordée ; suivi du trajet écran éteint sans position en arrière-plan (ADR-0019) | Déclaration du type `location` dans la Play Console, avec vidéo du trajet protégé |
| `FOREGROUND_SERVICE_CAMERA` | Normale (API 34+) | Vidéo de preuve | Idem |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Dangereuses | Trajet, position d'alerte ; point sur la carte d'accueil, protection active et premier plan seulement (ADR-0018) | Formulaire de déclaration de la localisation si nécessaire |
| `POST_NOTIFICATIONS` | Dangereuse (API 33+) | Vérification, état | — |
| `SEND_SMS` | Dangereuse, **restreinte par Play** | SMS automatique | ⚠ Voir ci-dessous |
| `CALL_PHONE` | Dangereuse | Appel automatique d'un proche (niveau 3) | — |
| `CAMERA` | Dangereuse | Vidéo de preuve | — |
| `VIBRATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` | Normales | Vibration ; minuteries ; rappel « réarmer » après redémarrage | — |
| `USE_BIOMETRIC` | Normale | Coffre-fort, désarmement | — |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Normales | Sync ; tuiles de la carte d'accueil (OpenFreeMap) | — |
| Google Play Services (`play-services-location`) | Bibliothèque, pas une permission | Fused Location ; repli `LocationManager` sans Play Services (ADR-0019) | Section « Sécurité des données » : SDK Google déclaré |
| ~~`ACCESS_BACKGROUND_LOCATION`~~ | — | **Non demandée** (ADR-0006) | — |
| ~~`READ_CONTACTS`~~ | — | Remplacée par le sélecteur système | — |
| ~~`SYSTEM_ALERT_WINDOW`, accessibilité, admin d'appareil~~ | — | **Interdites** dans ce projet (N9, R-SEC-04) | — |

## Contraintes plateforme qui modifient la spécification

| # | Contrainte | Impact | Décision |
|---|---|---|---|
| C1 | Un service de premier plan `microphone`, `camera` ou `location` ne peut pas être démarré depuis l'arrière-plan (restrictions « while-in-use »), sauf exceptions étroites | Pas d'activation automatique du micro par minuterie ou au démarrage du téléphone | L'armement se fait toujours par une action visible (app, notification, widget, tuile). Après un redémarrage du téléphone, une notification « Réarmer la protection » |
| C2 | Indicateur système du micro (API 31+), notification obligatoire du service | Impossible d'écouter discrètement (c'est voulu) | Le mode discret masque **notre** interface, pas l'indicateur système |
| C3 | `ACCESS_BACKGROUND_LOCATION` exige une justification forte, une déclaration et un écran séparé dans les réglages (API 30+) | Friction, risque de rejet | Service de premier plan `location` pendant le trajet, sans permission de position en arrière-plan |
| C4 | Politique Play « SMS et journal d'appels » : `SEND_SMS` n'est autorisée qu'aux applications SMS par défaut ou aux exceptions listées | Le SMS automatique depuis l'app Play n'est **pas garanti** | Voir ADR-0005 : soumettre une demande d'exception ; plan B : SMS envoyé par le serveur + SMS pré-rempli hors ligne ; ❓ variante APK hors Play |
| C5 | Les numéros d'urgence appelés via `ACTION_CALL` sont redirigés vers le composeur | Pas d'appel automatique aux secours | Conforme à R-ESC-02 |
| C6 | `USE_FULL_SCREEN_INTENT` est limité aux appels et alarmes (API 34+) | La vérification n'est pas en plein écran | Notification à priorité haute + vibration codée |
| C7 | Optimisations batterie des fabricants (Xiaomi, Tecno, Infinix, Itel, Samsung…) qui tuent les services | Protection interrompue | Guide d'onboarding par fabricant (liste blanche batterie) ; détection de l'interruption ; perte de contact côté serveur. ❓ Demande d'`IGNORE_BATTERY_OPTIMIZATIONS` : tolérée par Play pour certains cas, à justifier |
| C8 | Touches de volume et d'alimentation non accessibles en arrière-plan | SOS matériel impossible sans accessibilité | Tuile, widget et notification ; guide vers l'Urgence SOS du système |
| C9 | Politique Play sur les applications de surveillance (stalkerware) | Risque de classement comme stalkerware | Notification permanente, aucune dissimulation, pas de suivi d'un tiers ; « safety app » clairement décrite dans la fiche Play |
| C10 | Section « Sécurité des données » de Play | Doit refléter exactement la collecte | Tableau des données de [vie-privee-donnees.md](vie-privee-donnees.md) |
| C11 | Android 14+ : informations obligatoires sur l'usage des types de service de premier plan | Vidéo et justification à fournir | Préparer une vidéo de démonstration |

## Règles

| ID | Règle |
|---|---|
| R-PLAY-01 | **DOIT** : toute nouvelle permission passe par une revue (ce document + la fiche Play + la politique de confidentialité). |
| R-PLAY-02 | **DOIT** : chaque permission dangereuse a un écran explicatif préalable et un comportement documenté en cas de refus (module produit 1). |
| R-PLAY-03 | **DOIT** : tests manuels sur au moins Android 10, 12, 14 et la dernière version, et sur des appareils d'entrée de gamme courants sur le marché cible (❓ liste : Tecno, Infinix, Itel, Samsung A). |

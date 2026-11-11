# ADR-0023 — Réglages, biométrie, effacement d'urgence, mode dégradé

- Statut : **Acceptée** · Date : 2026-11-11 · S3.12, S5.13, S7.01, S7.02, S7.04, S7.05, S7.06 · Lié à ADR-0015

## Décisions
| Sujet | Décision |
|---|---|
| 5 PIN faux pendant une alerte (S7.01, R-SEC-02) | Le moteur émettait déjà `PIN_FAILURES` ; il part maintenant par SMS (FR/HT/EN, GSM-7, 1 SMS) aux proches qui ont reçu l'alerte : « plusieurs tentatives de désactiver l'alerte… L'alerte continue. » |
| Biométrie (S7.02) | Empreinte ou visage **classe Strong** (`BiometricPrompt`), **désactivée par défaut**. Si activée : « Preuves », désactivation de la protection, début et fin de trajet. **Annuler une alerte** (« Je vais bien ») : réglage séparé, désactivé par défaut et activable seulement avec le PIN, parce qu'un agresseur peut forcer un doigt ou un visage plus facilement qu'un code. Le PIN reste toujours possible. `MainActivity` devient une `FragmentActivity` (exigence de `BiometricPrompt`) |
| Captures d'écran (S7.04) | `FLAG_SECURE` sur l'écran d'urgence, « Preuves » et Réglages |
| Effacement d'urgence (S7.05, R-EVD-06) | Réglages → confirmation → PIN. Efface : preuves (clés détruites d'abord), journal, lieux favoris, accord de recherche, phrase secrète. **Garde** compte, contacts et PIN : la protection continue. Seule trace : une entrée « emergency_wipe » sans contenu |
| Suppression du compte (S3.12) | Réglages → confirmation → PIN → `delete_my_account` sur le serveur, puis preuves et toutes les tables locales ; l'app redémarre sur l'inscription. Si le serveur ne répond pas, **rien n'est effacé** et l'écran le dit (pas de compte orphelin) |
| Export (droit d'accès) | Fichier JSON lisible (compte, contacts, consentements, favoris, résumé des preuves, journal), enregistré où l'utilisateur choisit. Les enregistrements s'exportent à part, chiffrés (ADR-0021) |
| Mode dégradé (S5.13, R-AE-05) | Batterie < 10 % hors charge (reprise à 15 %) ou surchauffe ≥ `THERMAL_STATUS_SEVERE` : l'écoute de la phrase se met en pause (« Écoute IA en pause »). Un enregistrement d'alerte en cours **continue**. SOS et SMS non affectés |
| Fiabilité en arrière-plan (S7.06) | Consigne par marque (Tecno/Infinix/Itel, Samsung, Xiaomi, Huawei, Oppo/Realme/Vivo, autres) + bouton vers les réglages de batterie, **sans** demander `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (Q16 ouverte, risque Play) |
| Langue | Android 13+ : réglage « Langue de l'application » (FR, HT, EN déclarées dans `locales_config.xml`) |
| Confidentialité | Politique provisoire publiée avec la page des proches (`web/track/confidentialite.html`), liée depuis Réglages ; **à valider par le juriste**, versions HT et EN à venir |

## Reporté
- PIN de contrainte (V2, ADR-0015) ; écran de verrouillage neutre / mode leurre (V2, ADR-0017).
- Détection serveur de la fermeture forcée (S7.03) : avec le battement de trajet et `notify-alert`.
- Console admin (S7.07), audit MASVS et test d'intrusion (S7.08, S7.09).

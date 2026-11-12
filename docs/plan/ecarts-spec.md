# Écarts entre la spécification du MVP et le code

Audit du 2026-11-12 : lecture de `sprints-mvp.md`, des modules (`docs/modules/`), des modules produit (`docs/produit/`), de `docs/ia/modele-ia-locale.md` et des 171 exigences `R-XXX-nn`, comparées au code. 83 exigences ne sont citées nulle part dans le code ; la plupart sont transversales ou déjà respectées sans être citées. Ci-dessous : ce qui **manque vraiment**.

Légende : ✅ fait · 🟡 partiel · ❌ pas fait · 👤 dépend d'une action humaine · ⏸ mis de côté par le porteur (Firebase)

## 1. Le « cerveau » et la détection (le plus gros écart)

| Réf. | Prévu | État | Reste |
|---|---|---|---|
| S5.08, R-AL-06, R-RISK-03 | `alert-engine` reçoit **tous** les signaux, calcule `Risk = w₁A + w₂B + w₃C + w₄D + w₅E` avec les poids du profil, applique deux signaux indépendants (fenêtre de 10 s), hystérésis (0,6 / 0,4), période réfractaire (2 min), journalise chaque décision avec A–E, seuils et version | ❌ `RiskFusion` existe et est testé mais **n'est branché nulle part** ; l'app n'envoie au moteur que SOS, phrase secrète et incidents de trajet | **Décideur** (fenêtre glissante, signaux, hystérésis, réfractaire), configuration versionnée, journal des décisions, branchement dans `ProtectionController` |
| `sensor-engine` (S5.08 : B = chute) | Module `:sensor-engine` : chute, mouvement violent, arrêt brusque, immobilité ; `DeviceStatus` (batterie, réseau, GPS, mode avion, économie d'énergie) ; R-SE-01 à 04 | ❌ module absent | Créer le module (règles sans ML, rejeu de traces en JVM), branchement au décideur (B et D) |
| S4.09 | Dernier message à l'extinction (`ACTION_SHUTDOWN`) | ❌ | Récepteur + SMS (opt-in) |
| S5.07 | YAMNet (LiteRT) derrière un interrupteur éteint par défaut ; empreinte de modèle vérifiée (R-AE-02) | ❌ (chargé seulement au prototype S0) | Intégrer le modèle, classes `scream`, `glass_break`, `impact`, `alarm`, `horn_brake`, `gunshot`, classes d'inhibition ; composantes A et B. **Le fichier du modèle doit être téléchargeable ou fourni** |
| S5.04, S5.05 | KWS FR + HT entraîné | ❌ 👤 | Attend les enregistrements (Q19) ; en attendant : phrase secrète |
| S5.09 | Sensibilité (faible/normale/élevée), contextes à ignorer (concert, sport, conduite) avec durée, mots-clés personnalisés, cases par type de son | ❌ (seul l'interrupteur existe) | Réglages + effet sur les seuils (+0,2 pour les classes concernées ; jamais sur phrase secrète ni SOS) |
| S5.10, S5.11, S5.12 | Banc `tools/audio-eval/`, test « aucun audio ne sort », calibration 20 h | ❌ | Banc et test réseau faisables ; calibration 👤 |
| C, D, E du score | Déviation (C), perte de réseau/mouvement (D), contexte (E : nuit, profil, zone non sûre) | 🟡 le trajet produit des incidents mais pas des composantes continues | Composantes calculées par le décideur |

## 2. Alertes et serveur

| Réf. | Prévu | État | Reste |
|---|---|---|---|
| S3.04, S3.05, R-SY-03 | `notify-alert` : push FCM + SMS serveur + e-mail, `deliveries`, réessais, p95 < 5 s | ⏸ FCM mis de côté ; 🟡 SMS serveur à la demande (`alert-sms`) | SMS serveur automatique avec suivi de livraison (sans Firebase) |
| S3.06, R-SY-05, R-NET-03 | SMS direct si le serveur n'a pas accusé réception en 20 s ; déduplication par `alertId` | 🟡 relais serveur en cas d'échec local ; pas d'accusé de 20 s ni de déduplication croisée | Compléter |
| S3.09 | Vue « proche » dans l'app (parent inscrit), Realtime | ❌ | Écran + Realtime |
| S3.10 | « J'ai pris contact » arrête l'escalade ; « Alerte fausse » informe sans clôturer | 🟡 à vérifier de bout en bout | Test de bout en bout |
| R-DASH-05 | Actions des proches visibles par les autres proches en temps réel | ❌ | Realtime ou polling |
| S4.10 | Carte du trajet dans la page des proches | ❌ | Lien de suivi de trajet + page |
| S6.04, S6.05, R-EVD-04/05 | Preuves envoyées (bucket privé, reprise TUS, priorités, Wi-Fi hors urgence) | ❌ | Storage + RLS + envoi reprenable |
| S6.03 | Racine de chaîne signée par `K_device` → `register_chain_root` | ❌ | RPC + signature |
| S6.06, S1.13b, ADR-0016 | Partage de bout en bout des preuves (paire de clés Tink hybride, `evidence_key_wraps`) | ❌ | À faire après le point précédent |
| R-NET-05, R-TRIP-05 | Battement des trajets | ✅ (ADR-0024) | — |
| R-NET-07, ADR-0014 | Épinglage des clés publiques | ❌ (interdit le clair seulement) | Broches du domaine Supabase + secours |

## 3. Sécurité et conformité

| Réf. | Prévu | État | Reste |
|---|---|---|---|
| S7.03 | « Protection interrompue à HH:MM » au redémarrage, détection serveur | ❌ | Battement de protection (pas seulement de trajet) + message |
| R-SEC-03 | Désarmer pendant un trajet ou une alerte prévient les proches | 🟡 à vérifier | Vérifier et compléter |
| R-SEC-06, R-UI-06 | Notifications sans contenu sensible sur l'écran verrouillé | 🟡 à vérifier | Audit des notifications |
| R-HOME-03, R-AL-05 | Mode discret : aucun texte « alerte » à l'écran, vibration codée | 🟡 phrase secrète silencieuse ; pas de mode discret à l'écran | À préciser et faire |
| S7.07, R-ADM-* | Console admin minimale | ❌ | `web/admin` + RLS admin + MFA |
| S7.08, S7.09 | Modèle de menace T1–T14, MASVS L2, test d'intrusion RLS/Edge, audit des SDK | ❌ | Rapport + tests d'intrusion automatisés |
| S1.02 | detekt (`!!` interdit) | ❌ (ktlint et Lint seulement) | Ajouter detekt |
| S7.06 | Urgence SOS d'Android dans l'onboarding | 🟡 guides batterie faits | Écran de guidage |
| R-ONB-07 | Résumé final « actif / inactif » | ❌ | Écran |
| R-CON-01 | Avertissement si aucun contact accepté | 🟡 à vérifier | Vérifier |
| R-UI-01/02/04/07 | Accessibilité (TalkBack, 200 %, 48 dp, thème clair) | 🟡 thème sombre seulement | Audit + thème clair |

## 4. Qualité et lancement

| Réf. | État | Reste |
|---|---|---|
| S8.01 couverture ≥ 90 % de la logique | ❌ non mesurée | Jacoco + trous |
| S8.02 tests de bout en bout sur staging | ❌ pas de staging | Projet staging 👤 |
| S8.03 à S8.12 | ⏳ | Tests téléphone, campagne, Play, juriste, bêta : 👤 |

## Ordre d'implémentation retenu

1. **Décideur** (cerveau) + journal des décisions + configuration versionnée.
2. **`:sensor-engine`** (chute, mouvement violent, immobilité, état de l'appareil) branché au décideur.
3. **Réglages d'écoute** : sensibilité, contextes à ignorer, mots-clés, cases par type de son.
4. **YAMNet** (si le modèle est disponible) derrière un interrupteur, composantes A et B.
5. **Protection interrompue** (battement de protection, S7.03), dernier message à l'extinction (S4.09).
6. **Suivi de trajet en direct** pour les proches (S4.10) et **vue proche** dans l'app (S3.09).
7. **Preuves vers le cloud**, racine signée, partage de bout en bout.
8. **SMS serveur automatique** avec accusé de 20 s et déduplication.
9. **Sécurité** : épinglage, detekt, console admin, audit.
10. **Qualité** : couverture, banc audio, thème clair, accessibilité.

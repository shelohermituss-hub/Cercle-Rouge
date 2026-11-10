# Phases 5 à 7 — Preuves, anti-manipulation, lancement, V2 (S11 à S15+)

---

## S11 — Preuves chiffrées et coffre-fort (semaines 23–24)

**Objectif** : à partir du niveau 2, l'audio et la position sont enregistrés chiffrés, intègres, envoyés progressivement et supprimés à l'échéance.

**Prérequis** : S1.2 validé ; Q12 tranchée ; S7.

| # | Tâche | Détail | Réf. |
|---|---|---|---|
| S11.1 | Enregistrement audio | `MediaCodec` Opus 24 kbit/s → Ogg → Tink `StreamingAead` ; segments de 30 s ; pré-tampon de 5 s ; plafond de 60 min | R-EVD-01/07, R-EE-01 |
| S11.2 | Position et capteurs | Trace de l'alerte + résumé capteurs ±30 s comme preuves | module 6 |
| S11.3 | Chaîne d'intégrité | SHA-256 avant chiffrement ; `prevHash` ; racine signée par K_device ; envoi de la racine au serveur ; ❓ horodatage RFC 3161 | module 6, R-EE-03 |
| S11.4 | Rétention | 24 h, 7 j, 30 j, ou à la clôture ; conservation manuelle ≤ 90 j ; `purgeExpired` (WorkManager toutes les heures + au démarrage) ; crypto-shredding | R-EVD-03, R-EE-02/05 |
| S11.5 | Envoi progressif | Position > métadonnées > audio ; morceaux reprenables (tus ou URL pré-signées) ; Wi-Fi seulement pour la vidéo hors urgence | R-EVD-04, R-NET-04, R-SY-04 |
| S11.6 | Partage aux proches | Option retenue en Q12 (recommandé : clé dans le fragment de l'URL, déchiffrement WebCrypto dans la page) ; lecture en streaming | crypto |
| S11.7 | Coffre-fort | Biométrie ou PIN ; liste ; lecture ; conserver ; supprimer ; export chiffré ; `FLAG_SECURE` | R-SEC-05, module 6 |
| S11.8 | Outil de vérification | Script ou CLI qui vérifie une chaîne exportée hors ligne | R-EE-03 |
| S11.9 | Stockage faible | < 200 Mo → audio seulement, avec avertissement | R-EVD-08 |

**Tests** : aucun fichier en clair pendant un enregistrement ; altération d'1 octet → détectée ; purge à l'heure exacte (horloge virtuelle) ; reprise d'envoi après une coupure.
**Démo** : alerte → enregistrement → le proche écoute l'extrait dans la page web → 24 h plus tard (horloge accélérée), plus rien sur le téléphone ni sur le serveur.
**Skills** : `/security-and-hardening`, `/test-driven-development`.

---

## S12 — Anti-manipulation et modes discrets (semaines 25–26)

**Objectif** : un agresseur qui prend le téléphone ne peut pas arrêter l'alerte sans que les proches le sachent.

**Prérequis** : S3 (PIN), S7, S11.

| # | Tâche | Détail | Réf. |
|---|---|---|---|
| S12.1 | PIN partout où c'est requis | Désarmer, terminer un trajet, annuler une alerte ; 5 échecs → message « tentatives de désactivation » | R-SM-04, R-SEC-02 |
| S12.2 | PIN de contrainte | Semble désarmer ; alerte niveau 2 silencieuse ; l'interface affiche « désactivé » | R-SEC (Q5) |
| S12.3 | Mode discret | Aucun son ni texte d'alerte ; vibrations codées ; tests de capture d'écran | R-HOME-03, R-AL-05 |
| S12.4 | Mode leurre | Écran neutre ; ❓ alias d'icône neutre déclaré ; `FLAG_SECURE` ; écran de verrouillage neutre | R-SEC-05/06, module 9 |
| S12.5 | Effacement d'urgence | Geste + PIN → suppression des clés et des fichiers en < 2 s | R-EVD-06 |
| S12.6 | Guide Urgence SOS système | Écran d'onboarding qui explique comment activer l'Urgence SOS d'Android selon le fabricant | C8 |
| S12.7 | Guide batterie par fabricant | Écran adapté (Tecno, Infinix, Itel, Samsung, Xiaomi) + détection d'une interruption de service | C7 |

**Démo** : un « agresseur » prend le téléphone pendant une alerte et entre le PIN de contrainte → l'écran indique « protection désactivée », pendant que les proches reçoivent une alerte silencieuse.
**Skills** : `/security-and-hardening`, `security-auditor` (agent), `/ui-ux-pro-max`.

---

## S13 — Durcissement : sécurité, performance, accessibilité, console admin (semaines 27–28)

**Objectif** : l'application est prête pour une bêta publique.

| # | Tâche | Détail | Réf. |
|---|---|---|---|
| S13.1 | Audit sécurité | Revue du modèle de menace T1 à T14 ; contrôle OWASP MASVS (L2 visé) ; test d'intrusion de l'API et de la page de suivi ; énumération des jetons ; analyse de l'APK (secrets, logs) | securite/ |
| S13.2 | Admin console minimale | Santé des envois, incidents, abus (bloquer ou limiter), support par code utilisateur ; SSO + FIDO2 ; journal append-only ; derrière un VPN | R-ADM-01..05 |
| S13.3 | Performance et batterie | Matrice d'appareils : démarrage < 500 ms (R-HOME-06), batterie par mode, mémoire, ANR à 0 ; profils de base (Baseline Profiles) | R-HOME-06, R-AUD-08 |
| S13.4 | Accessibilité | TalkBack sur tous les parcours critiques ; police à 200 % ; contraste ; captures de référence | R-UI-01/02 |
| S13.5 | Langues | Relecture native du créole et de l'anglais ; tests avec des utilisateurs peu à l'aise avec la lecture | R-ONB-05 |
| S13.6 | Résilience | Tests de chaos : coupures réseau, mort du processus, redémarrage, mode avion, batterie à 5 %, stockage plein | module 8 |
| S13.7 | Observabilité | Métriques serveur (livraison, latence p95 < 5 s) ; alertes d'astreinte ; ❓ crash reporting sans données personnelles | R-BE-07 |

**Jalon** : 0 vulnérabilité haute ou critique ouverte.
**Skills** : `/security-and-hardening`, `security-auditor`, `test-engineer`, `web-performance-auditor`, `/performance-optimization`.

---

## S14 — Bêta pilote et conformité Play (semaines 29–30)

| # | Tâche | Détail |
|---|---|---|
| S14.1 | Bêta fermée Play (test interne puis fermé) | 50 à 200 utilisateurs en Haïti + diaspora ; parcours d'inscription accompagné |
| S14.2 | Mesures | Faux positifs par heure, taux de livraison par canal et par opérateur, temps de réponse des proches, batterie réelle, abandon de l'onboarding |
| S14.3 | Ajustements | Seuils, textes, délais par défaut, selon les mesures |
| S14.4 | Dossier Play | Section « Sécurité des données » ; déclarations des types de service de premier plan + vidéos ; formulaire `SEND_SMS` (si l'exception n'est pas encore obtenue) ; localisation ; fiche Play honnête (« peut ne pas détecter ») |
| S14.5 | Juridique | Conditions d'utilisation, politique de confidentialité finale, procédure de réquisition, contrats des sous-traitants |
| S14.6 | Support | FAQ en 3 langues, canal de support, procédure d'incident |

**Démo** : rapport de pilote + dossier Play prêt.

---

## S15 — Lancement 1.0 (semaines 31–32)

| # | Tâche |
|---|---|
| S15.1 | Corrections issues de la bêta ; gel des fonctionnalités |
| S15.2 | Déploiement progressif (staged rollout) : 5 % → 20 % → 50 % → 100 %, avec des seuils d'arrêt (plantages, échecs de livraison) |
| S15.3 | Page de statut, astreinte, procédure de retour arrière |
| S15.4 | Rétrospective, mise à jour de la doc et des ADR, priorisation V2 |

**Skills** : `/shipping-and-launch`, `/git-workflow-and-versioning`, `/documentation-and-adrs`.

---

## Phase 7 — Après 1.0 (V2, à prioriser)

| Sujet | Contenu | Dépendance |
|---|---|---|
| Vidéo de preuve | Service de premier plan `camera`, clips de 15 s, Wi-Fi par défaut | S11 |
| WhatsApp Business | Modèles approuvés, envoi serveur automatique | Q13, coût |
| KWS créole et anglais complets | Si reportés de S10 | Données |
| Modèle de détresse vocale | Évaluation par langue et revue juridique avant activation | Q22 |
| Partage de bout en bout (Signal Protocol) | Entre applications | Q12 option C |
| Plans de contacts par contexte | Contacts différents selon la situation | module 7 |
| Profils enfant | Consentement parental, politique Play Familles | Q3 |
| Intégration officielle avec les secours (niveau 4) | Si un interlocuteur existe | Q27 |
| iOS (Swift et SwiftUI) | ❓ Kotlin Multiplatform pour `core-domain` | Q17 |

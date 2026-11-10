# Plan de développement — Cercle Rouge 1.0

> Plan d'exécution de la spécification `docs/`. Aucun sprint ne commence sans que ses **prérequis** (questions 🔴 de [questions-ouvertes.md](../questions-ouvertes.md)) soient tranchés.

## Hypothèses (❓ à confirmer)

| Hypothèse | Valeur retenue | Si c'est faux |
|---|---|---|
| Équipe | 1 à 2 développeurs + Claude Code ; un designer à temps partiel ; un juriste ponctuel | Avec 3 développeurs ou plus, les phases 3 et 4 peuvent se faire en parallèle (−6 semaines) |
| Durée d'un sprint | 2 semaines | — |
| Marché pilote | Haïti (FR + HT), puis la diaspora (EN, FR) | Changer les numéros d'urgence, les fournisseurs SMS et le juridique |
| Backend | Go (recommandé) ; Node.js possible | Les sprints S5 et S7 restent identiques ; seule la stack change |
| Appareils de test | Au moins 4 téléphones physiques : 1 entrée de gamme Android 10 (2 Go), 1 Tecno ou Infinix, 1 Samsung A, 1 Pixel récent | **Obligatoire** : services de premier plan, batterie et micro ne se testent pas sur émulateur |

## Feuille de route

| Phase | Sprints | Résultat démontrable | Semaines |
|---|---|---|---|
| **0 — Cadrage et dérisquage** | S0, S1 | Décisions bloquantes prises ; 4 prototypes jetables qui prouvent que l'essentiel est faisable sur un vrai téléphone | 1–4 |
| **1 — Socle** | S2, S3 | Projet multi-modules, CI verte, machine à états testée, stockage chiffré, onboarding et consentements | 5–8 |
| **2 — MVP SOS** | S4, S5 | **Un SOS envoie un SMS et un lien de suivi aux proches, même hors ligne** | 9–12 |
| **3 — Trajet protégé** | S6, S7 | Trajet déclaré, déviation détectée, vérification, escalade 1→3, tableau de bord des proches, perte de contact | 13–16 |
| **4 — Écoute IA locale** | S8, S9, S10 | Cri, vitre, alarme, mot-clé FR et phrase secrète détectés localement ; score fusionné ; faux positifs mesurés | 17–22 |
| **5 — Preuves et anti-manipulation** | S11, S12 | Preuves chiffrées, intègres, à durée limitée ; PIN de contrainte, leurre, effacement d'urgence | 23–26 |
| **6 — Durcissement et lancement** | S13, S14, S15 | Audit sécurité, batterie, accessibilité ; bêta pilote ; publication Play 1.0 | 27–32 |
| **7 — Après 1.0** | V2 | Vidéo, WhatsApp Business, créole et anglais complets, modèle de détresse, iOS | — |

**Jalons de décision (go / no-go)**
- **Fin de S1** : les prototypes valident l'audio en arrière-plan ≤ 4 %/h de batterie, l'enregistrement chiffré en flux et l'envoi de SMS. Sinon, on revoit la spécification **avant** d'écrire le socle.
- **Fin de S5** : MVP SOS utilisable → bêta interne possible dès ce point (valeur livrée même si l'IA prend du retard).
- **Fin de S10** : si le taux de faux positifs dépasse 1 par 8 h, l'IA sort en « bêta » désactivée par défaut et 1.0 sort avec SOS + trajet.
- **Fin de S13** : aucune vulnérabilité haute ou critique ouverte, sinon pas de bêta publique.

Détail des sprints :
- [Phase 0 et 1 — Cadrage, dérisquage, socle (S0 à S3)](phase-0-1-socle.md)
- [Phase 2 et 3 — MVP SOS et trajet protégé (S4 à S7)](phase-2-3-sos-trajet.md)
- [Phase 4 — Écoute IA locale (S8 à S10)](phase-4-ia.md)
- [Phase 5 à 7 — Preuves, anti-manipulation, lancement, V2 (S11 à S15+)](phase-5-7-preuves-lancement.md)
- [Prompts Claude Code](prompts.md)

## Structure de chaque sprint

Chaque sprint est décrit avec les mêmes rubriques :

1. **Objectif** : une phrase, démontrable.
2. **Prérequis** : décisions et sprints dont il dépend.
3. **Tâches** : numérotées `S<n>.<m>`, chacune avec ses exigences `R-…` couvertes.
4. **Tests** : ce qui doit exister pour que la tâche soit finie.
5. **Livrables**.
6. **Démo de fin de sprint** : le scénario à montrer sur un vrai téléphone.
7. **Risques** et parade.
8. **Skills** à charger.
9. **Prompt** à donner à Claude Code (dans [prompts.md](prompts.md)).

## Définition de « terminé » (toutes les tâches)

- [ ] Le code référence les exigences `R-…` qu'il implémente (KDoc et nom des tests).
- [ ] Tests unitaires JVM pour toute la logique ; tests instrumentés pour ce qui touche Android (service, permissions, Room, Keystore).
- [ ] `./gradlew check` vert : ktlint, detekt, Android Lint en erreur, tests, vérification des clés i18n.
- [ ] Les textes existent en FR, HT et EN (le HT peut être marqué « à relire », mais la clé doit exister).
- [ ] Aucune donnée personnelle dans les logs ; aucun secret dans le dépôt.
- [ ] Si une permission est ajoutée ou modifiée : `docs/conformite/android-permissions-play.md` est à jour.
- [ ] Si une règle change : la doc est mise à jour **dans le même commit**.
- [ ] Revue : `/code-review-and-quality` ; `/security-and-hardening` si la tâche touche à la crypto, au réseau, aux permissions ou au backend ; `/ponytail-review` pour la logique.
- [ ] Test manuel sur au moins **2 téléphones physiques**, dont l'entrée de gamme.
- [ ] Commits atomiques ; une PR par tâche ou groupe cohérent ; la CI est verte avant la fusion.

## Règles de travail avec Claude Code

1. Un sprint = une branche `sprint/S<n>-<sujet>` ; une tâche = un ou plusieurs commits ; une PR par tâche significative.
2. Toujours commencer une session par le **prompt maître** ([prompts.md](prompts.md#prompt-maître)), puis le prompt du sprint.
3. Claude **pose des questions** dès qu'une décision n'est pas dans `docs/` ; il ne suppose pas.
4. Écrire les tests d'abord pour la logique pure (`core-domain`, `alert-engine/logic`, `location-engine` algorithmes, `contacts-engine` résolution).
5. Tranches verticales minces : chaque PR laisse l'application compilable et utilisable.
6. Toute décision nouvelle → un ADR dans `docs/decisions/`.

## Tableau de suivi des risques

| Risque | Probabilité | Impact | Parade | Sprint de vérification |
|---|---|---|---|---|
| Refus Play de `SEND_SMS` | Élevée | Élevé | Soumission dès S0 ; SMS serveur ; SMS pré-rempli | S0, S5, S14 |
| Optimisations batterie des fabricants qui tuent le service | Élevée | Élevé | Guide par fabricant ; détection d'interruption ; battement serveur | S1, S6, S13 |
| Faux positifs audio en milieu bruyant | Élevée | Élevé | Deux signaux, vérification, calibration terrain, IA désactivable | S9, S10, S14 |
| Pas de données audio en créole | Moyenne | Moyen | Collecte dès S0 ; FR d'abord | S0, S10 |
| Batterie > 4 %/h | Moyenne | Élevé | Porte VAD, mode dégradé, profils | S1, S8, S13 |
| Chiffrement en flux de l'audio complexe (`MediaRecorder` écrit dans un fichier) | Moyenne | Moyen | Prototype S1 ; `MediaCodec` + Ogg | S1, S11 |
| Coûts SMS et téléphonie | Moyenne | Moyen | Limites ; SMS direct privilégié | S5 |
| Blocage juridique (enregistrement de tiers) | Faible à moyenne | Élevé | Revue juridique dès S0 ; preuves désactivables | S0, S14 |

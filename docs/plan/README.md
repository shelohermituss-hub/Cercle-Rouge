# Plan de développement — Cercle Rouge MVP (1.0)

> Version 2, du 2026-11-10. Elle fusionne ta proposition de 9 sprints avec la spécification `docs/`. Tous tes éléments sont conservés. Marquage : **➕** ajouté · **↪** déplacé d'un autre sprint · **V2** reporté après le MVP (selon ta définition du MVP, ADR-0010).

## Décisions intégrées

| Sujet | Décision | Réf. |
|---|---|---|
| Score de risque | `Risk = w₁A + w₂B + w₃C + w₄D + w₅E` | ADR-0008 |
| Pays de lancement | Haïti (+509, 114/115/116, Digicel et Natcom) | ADR-0009 |
| Âge minimum | 12 ans ; consentement d'un parent ou tuteur pour les 12–15 ans ; politique Play « Familles » | ADR-0009 |
| Périmètre MVP | Tes 10 points + PIN et journal local | ADR-0010 |
| Plateforme | Android d'abord (Kotlin + Compose), iOS en V2 | ADR-0001 |

## Pourquoi j'ai réordonné tes sprints

| Ta proposition | Problème | Changement |
|---|---|---|
| Machine à états au Sprint 4 | Les sprints 2 (IA) et 3 (trajet) produisent des alertes, mais rien ne les traite encore | ↪ La machine à états passe au **Sprint 1** : c'est le cœur, tout le reste s'y branche |
| Backend au Sprint 6 | Les notifications push, le lien de suivi et la carte des sprints 3 et 4 en ont besoin | ↪ Le backend passe au **Sprint 3**, juste après les alertes locales |
| PIN au Sprint 7 | « Je vais bien » (Sprint 4) sans PIN permet à un agresseur d'annuler l'alerte | ↪ PIN de base au **Sprint 1** ; PIN de contrainte et biométrie restent au Sprint 7 |
| File hors-ligne au Sprint 5 | Le SMS doit déjà fonctionner hors ligne au moment des alertes | ↪ File d'attente au **Sprint 2** ; le Sprint 6 l'étend aux fichiers audio |
| IA au Sprint 2 | Tâche la plus risquée, et sans aucun chemin d'alerte pour en tirer de la valeur | ↪ IA au **Sprint 5**. Le risque technique (batterie, micro en arrière-plan) est quand même vérifié au **Sprint 0** par un prototype |
| Appel automatisé au Sprint 4 | Ta définition du MVP le met en V2 | **V2** ; seul l'appel manuel (composeur) reste |
| Vidéo au Sprint 5 | En V2 selon ta définition du MVP | **V2** |
| Tests seulement au Sprint 8 | Bugs découverts trop tard | ➕ Tests **à chaque sprint** (définition de « terminé ») ; le Sprint 8 devient la campagne de terrain sur 20 à 50 téléphones |

## Feuille de route

| Sprint | Thème | Démo de fin de sprint (sur un vrai téléphone) | Durée |
|---|---|---|---|
| **S0** | Cadrage + prototypes | Décisions prises, maquettes Figma testées, 4 prototypes mesurés | 3 sem. |
| **S1** | Socle Android | App installée, onboarding avec consentements, PIN, SOS qui passe en `EMERGENCY_MODE` (journal + notification locale) | 2 sem. |
| **S2** | Alertes locales | **Mode avion : SOS → les proches reçoivent un SMS avec la position** ; vérification « Es-tu en sécurité ? » | 2 sem. |
| **S3** | Backend + proches | Notification push + SMS serveur + **lien de suivi** ouvert par un proche ; consentement du contact | 2 sem. |
| **S4** | Localisation et trajet | Trajet réel → détour → vérification → alerte avec la carte ; téléphone éteint → « perte de contact » | 2 sem. |
| **S5** | Écoute IA locale | « Au secours » ou « Anmwe » → vérification ; phrase secrète → alerte silencieuse ; musique forte → rien | 2 sem. |
| **S6** | Preuves | Alerte → audio chiffré → le proche écoute l'extrait → supprimé automatiquement après 24 h | 2 sem. |
| **S7** | Sécurité | PIN de contrainte, mode leurre, effacement d'urgence ; audit sans faille haute | 2 sem. |
| **S8** | Tests et lancement | Rapport de terrain sur 20 à 50 téléphones ; bêta fermée ; pilote | 3 sem. |

**Total : environ 20 semaines (5 mois)** avec 2 développeurs + Claude Code. Avec 1 seul développeur, il faut compter des sprints de 3 semaines, soit environ 7 mois.

### Points de décision (go / no-go)

| Quand | Critère | Si non |
|---|---|---|
| Fin S0 | Prototypes : micro écran éteint ≥ 2 h sur 3 appareils sur 4, ≤ 4 %/h de batterie ; SMS reçu < 10 s ; audio chiffré sans fichier en clair | Revoir la spécification avant le socle |
| Fin S3 | SOS + suivi fonctionnels | **Bêta interne possible** (10 personnes) |
| Fin S5 | ≤ 1 vérification injustifiée par 8 h d'écoute | L'IA sort désactivée par défaut, avec la mention « bêta » |
| Fin S7 | 0 vulnérabilité haute ou critique | Pas de bêta publique |

Détail : **[sprints-mvp.md](sprints-mvp.md)** · Après le MVP : **[v2.md](v2.md)** · Prompts : **[prompts.md](prompts.md)**

## Définition de « terminé » (chaque tâche, chaque sprint)

- [ ] Exigences `R-…` citées dans le code (KDoc) et dans le nom des tests.
- [ ] Tests unitaires JVM pour la logique ; tests instrumentés pour le service, les permissions, Room et le Keystore.
- [ ] `./gradlew check` vert (ktlint, detekt, Lint en erreur, tests, clés i18n FR, HT et EN).
- [ ] Aucune donnée personnelle dans les logs ; aucun secret dans le dépôt.
- [ ] Permission ajoutée → `conformite/android-permissions-play.md` mis à jour ; règle changée → doc mise à jour dans le même commit.
- [ ] Revue : `/code-review-and-quality` ; `/security-and-hardening` (crypto, réseau, permissions, backend) ; `/ponytail-review` (logique).
- [ ] Testé sur au moins 2 téléphones physiques, dont 1 entrée de gamme sous Android 10.
- [ ] CI verte avant la fusion ; une PR par tâche significative.

## Équipe et outils (❓ à confirmer en S0)

| Rôle | Charge |
|---|---|
| Développeur Android principal | Plein temps |
| Développeur backend et web (ou le même) | Plein temps à partir de S3 |
| Designer (Figma) | S0, puis 2 jours par sprint |
| Testeurs terrain à Haïti | S5 (calibration), S8 (campagne) |
| Juriste | S0 (lancement), S8 (validation) |
| Locuteurs créoles | S0 (mots-clés), S5 (enregistrements), S8 (relecture) |

# Plan de développement — Cercle Rouge MVP (1.0)

> Version 3, du 2026-11-10 : backend **Supabase** et consentement parental intégrés. Elle fusionne ta proposition de 9 sprints avec la spécification `docs/`. Tous tes éléments sont conservés. Marquage : **➕** ajouté · **↪** déplacé d'un autre sprint · **V2** reporté après le MVP (selon ta définition du MVP, ADR-0010).

## Décisions intégrées

| Sujet | Décision | Réf. |
|---|---|---|
| Score de risque | `Risk = w₁A + w₂B + w₃C + w₄D + w₅E` | ADR-0008 |
| Pays de lancement | Haïti (+509, 114/115/116, Digicel et Natcom) | ADR-0009 |
| Âge minimum | 12 ans ; consentement d'un parent ou tuteur pour les 12–15 ans ; politique Play « Familles » | ADR-0009 |
| Périmètre MVP | Tes 10 points + PIN et journal local | ADR-0010 |
| Plateforme | Android d'abord (Kotlin + Compose), iOS en V2 | ADR-0001 |
| Backend | Supabase (Auth OTP, Postgres + RLS, RPC, Edge Functions, Realtime, Storage, pg_cron) | ADR-0011 |
| Consentement parental | Confirmation dans l'app, sur le téléphone du parent inscrit | ADR-0013 |

## Pourquoi j'ai réordonné tes sprints

| Ta proposition | Problème | Changement |
|---|---|---|
| Machine à états au Sprint 4 | Les sprints 2 (IA) et 3 (trajet) produisent des alertes, mais rien ne les traite encore | ↪ La machine à états passe au **Sprint 1** : c'est le cœur, tout le reste s'y branche |
| Backend au Sprint 6 | Les notifications push, le lien de suivi et la carte des sprints 3 et 4 en ont besoin | ↪ Le backend passe au **Sprint 3**, juste après les alertes locales |
| PIN au Sprint 7 | « Je vais bien » (Sprint 4) sans PIN permet à un agresseur d'annuler l'alerte | ↪ PIN au **Sprint 1** (obligatoire en trajet) ; biométrie et durcissement au Sprint 7 ; PIN de contrainte en V2 |
| File hors-ligne au Sprint 5 | Le SMS doit déjà fonctionner hors ligne au moment des alertes | ↪ File d'attente au **Sprint 2** ; le Sprint 6 l'étend aux fichiers audio |
| IA au Sprint 2 | Tâche la plus risquée, et sans aucun chemin d'alerte pour en tirer de la valeur | ↪ IA au **Sprint 5**. Le risque technique (batterie, micro en arrière-plan) est quand même vérifié au **Sprint 0** par un prototype |
| Appel automatisé au Sprint 4 | Ta définition du MVP le met en V2 | **V2** ; seul l'appel manuel (composeur) reste |
| Vidéo au Sprint 5 | En V2 selon ta définition du MVP | **V2** |
| Tests seulement au Sprint 8 | Bugs découverts trop tard | ➕ Tests **à chaque sprint** (définition de « terminé ») ; le Sprint 8 devient la campagne de terrain sur 20 à 50 téléphones |

## Feuille de route

| Sprint | Thème | Démo de fin de sprint |
|---|---|---|
| **S0** (3 sem.) | Cadrage, comptes (Supabase, SMS, Firebase, Play), prototypes | Décisions, maquettes testées, 5 prototypes mesurés |
| **S1** | Socle Android + socle Supabase | Inscription OTP, **consentement parental de bout en bout**, PIN, machine à états |
| **S2** | Alertes locales | Mode avion : SOS → SMS aux proches avec la position |
| **S3** | Alertes via Supabase + page des proches | Push, SMS serveur, lien de suivi, « J'ai pris contact » |
| **S4** | Trajet protégé | Détour → vérification → alerte avec carte ; perte de contact |
| **S5** | Écoute IA locale | Mots-clés FR et HT, phrase secrète ; faux positifs mesurés |
| **S6** | Preuves | Audio chiffré, écouté par le proche, supprimé à l'échéance choisie |
| **S7** | Sécurité | Biométrie, effacement d'urgence ; audit |
| **S8** (3 sem.) | Tests terrain et lancement | 20 à 50 téléphones, bêta, pilote Haïti |

**≈ 20 semaines** avec 2 développeurs + Claude Code (≈ 7 mois avec 1 seul).

### Points de décision (go / no-go)

| Quand | Critère | Si non |
|---|---|---|
| Fin S0 | Prototypes : micro écran éteint ≥ 2 h sur 3 appareils sur 4, ≤ 4 %/h de batterie ; SMS reçu < 10 s ; audio chiffré sans fichier en clair | Revoir la spécification avant le socle |
| Fin S3 | SOS + suivi + consentement parental fonctionnels | **Bêta interne possible** (10 personnes) |
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
- [ ] Migration Supabase : RLS + `supabase/tests/security.sql` vert + Security Advisor sans nouvelle alerte.
- [ ] CI verte avant la fusion ; une PR par tâche significative.

## Équipe et outils (❓ à confirmer en S0)

| Rôle | Charge |
|---|---|
| Développeur Android principal | Plein temps |
| Développeur Supabase et web (ou le même) | Mi-temps en S1, plein temps à partir de S3 |
| Designer (Figma) | S0, puis 2 jours par sprint |
| Testeurs terrain à Haïti | S5 (calibration), S8 (campagne) |
| Juriste | S0 (lancement), S8 (validation) |
| Locuteurs créoles | S0 (mots-clés), S5 (enregistrements), S8 (relecture) |

# Ce qui reste à faire avant le lancement (MVP)

Mis à jour le 2026-11-11. Détail par sprint : `suivi-S0.md` à `suivi-S6.md` ; plan d'origine : `sprints-mvp.md` ; après le MVP : `v2.md`.

Légende : 🧑 action du porteur du projet · 🤖 développement · 📱 test sur un vrai téléphone · ⏸ bloqué par une autre étape

## Où on en est

| Sprint | État |
|---|---|
| S1 Socle Android + Supabase | ✅ fait (inscription par SMS et parcours parent : en attente du fournisseur SMS) |
| S2 Alertes locales, SMS | ✅ code fait — **jamais testé avec un vrai SMS** |
| S3 Serveur et page des proches | 🟡 ~60 % : alertes, liens de suivi, page web, synchronisation faits ; notifications push, SMS serveur, compte vérifié restent |
| S4 Trajet protégé | 🟡 partie téléphone faite ; partie serveur (battement, perte de contact) reste |
| S5 Écoute IA | 🟡 phrase secrète faite ; mots-clés en créole attendent des enregistrements |
| S6 Preuves chiffrées | 🟡 partie téléphone faite ; envoi et partage aux proches restent |
| S7 Sécurité | 🟡 fait le 2026-11-11 : PIN faux → proches, biométrie, captures bloquées, effacement d'urgence, guides batterie ; console admin et audit restent |
| S8 Tests terrain et lancement | ⏳ pas commencé |

**Le plus gros risque aujourd'hui** : rien n'a encore été vérifié sur un vrai téléphone (SMS, écran éteint, micro, GPS, batterie).

---

## Phase 1 — Valider ce qui existe (1 à 2 semaines) · priorité absolue

### 📱 Tests sur téléphone (toi, avec l'APK)
1. **SMS réels** (S2) : SOS → SMS reçu par un proche, avec le lien de suivi ; test d'alerte « [TEST] » ; SMS pré-rempli si la permission est refusée.
2. **Page des proches** (S3) : ouvrir le lien reçu, « J'ai pris contact » → visible dans l'app en 15 s ; « Je vais bien » → page « … va bien ».
3. **Écran éteint 30 min à 1 h** (S2, S4) : la notification reste, l'alerte part, les minuteries tiennent en Doze.
4. **Trajet à pied de 15 min** (S4) : pas de faux incident ; arrêt non déclaré de plus de 10 min → « Es-tu en sécurité ? ».
5. **Phrase secrète** (S5) : enregistrer, tester, la dire protection active → alerte silencieuse ; 1 h de conversation sans fausse alerte ; appel téléphonique → « Écoute en pause ».
6. **Preuves** (S6) : SOS d'1 min → enregistrement lisible dans « Preuves », vérification, export.
7. **Tuile, widget, notification SOS** (S2) par-dessus l'écran verrouillé.
8. **Batterie** : noter la consommation pendant 1 h protection active, puis 1 h de trajet.

### 🤖 Corrections (moi)
- Tout ce que tes tests remontent (trace de plantage partageable dans la version de test).

### 🧑 Petites actions
- **Activer GitHub Pages** (Settings → Pages → Source « GitHub Actions ») : sans ça, le lien du SMS n'ouvre rien.

---

## Phase 2 — Comptes et décisions (à lancer tout de suite : délais longs) 🧑

| Action | Débloque | Délai typique |
|---|---|---|
| **Fournisseur SMS** (Twilio ou Vonage) couvrant Digicel et Natcom, expéditeur autorisé en Haïti (Q13) | Connexion par code SMS, invitation du parent, SMS envoyés par le serveur (secours si le téléphone n'a plus de crédit) | 1 à 3 semaines |
| **Firebase** (projet + FCM) | Notifications push aux proches qui ont l'app, réveil du téléphone | 1 jour |
| **Google Play Console** + **dossier d'exception `SEND_SMS`** (Q11) | Publication avec SMS automatiques (sinon SMS pré-rempli seulement) | 2 à 6 semaines, refus possible |
| **Juriste** : enregistrement de tiers (Q23), loi haïtienne sur les données (Q24), conditions d'utilisation (Q26), consentement parental | Lancement public | 2 à 6 semaines |
| **Supabase plan Pro** + projet de **staging** séparé, sauvegardes (PITR) | Pas de mise en pause du projet, tests sans toucher la production | 1 jour |
| **Numéros d'urgence** 114 / 115 / 116 confirmés pour Haïti | Bouton « Appeler les secours » | — |
| **Atelier créole** : relecture de tous les textes, liste des mots-clés (Q20) | Lancement, mots-clés IA | 1 à 2 semaines |
| **Collecte d'enregistrements** consentis en créole et sons d'Haïti (Q19) | Détection des mots-clés, calibration de l'IA | 1 à 3 mois |
| **Téléphones de test** (Tecno, Infinix, Itel, Samsung, Xiaomi ; Android 10 à 15) | Campagne de tests S8 | — |

---

## Phase 3 — Terminer les sprints 3, 4, 6 (≈ 3 semaines de dev) 🤖

### Dès maintenant (sans compte supplémentaire)
- ~~**Écran Réglages**~~ ✅ fait (suppression du compte, export, langue, conservation, interrupteurs, biométrie, effacement d'urgence, batterie). Sensibilité de l'écoute : avec les mots-clés.
- ✅ (2026-11-12, ADR-0024 ; SMS réel à vérifier) **Trajet suivi par le serveur** (S4.07, S4.08) : table `trips`, battement toutes les 2 min (opt-in), **chien de garde `pg_cron`** → alerte « perte de contact » si le téléphone se tait 10 min. C'est la protection quand le téléphone est éteint ou cassé.
- **Carte dans la page des proches pendant un trajet** (S4.10).
- **Dernier message à l'extinction** du téléphone (S4.09), si tu le veux (opt-in).
- **Envoi des preuves** (S6.04, S6.05) : bucket privé chiffré, envoi reprenable, priorités réseau (position > texte > audio), Wi-Fi seulement hors urgence.

### ⏸ Après le fournisseur SMS
- **Connexion par code SMS** (S1.20) : le compte anonyme devient un compte vérifié, sans perte de données.
- **Parcours parent réel** (S1.17, S1.23, S1.24) : invitation SMS, confirmation dans l'app du parent, retrait ; remplace la confirmation simulée.
- **Contacts côté serveur + consentement des proches** (S3.03) : page « accepter / refuser / STOP ».
- **Vue « proche » dans l'app** (S3.09) pour un parent inscrit.

### ⏸ Après Firebase
- **`notify-alert`** (S3.04, S3.05) : push + SMS serveur + e-mail, avec réessais et suivi des livraisons ; le SMS du téléphone reste le premier canal.
- **Partage des preuves de bout en bout** (S6.06) : seul le proche qui a l'app peut écouter (ADR-0016).

---

## Phase 4 — Écoute IA, suite (S5) ⏸ données
- Détection des mots-clés FR + HT entraînée (S5.04, S5.05) ← enregistrements Q19.
- Banc de sons d'Haïti (tap-tap, marché, rara, pluie sur tôle, église) (S5.10).
- Calibration terrain 20 h (S5.12) et seuils de la phrase secrète sur de vraies voix.
- ~~Mode dégradé : surchauffe, batterie < 10 % (S5.13)~~ ✅ fait.
- Contextes à ignorer (« Je suis en concert »…) et sensibilité (S5.09) 🤖 faisable maintenant.
- **Jalon** : si trop de fausses alertes → écoute « bêta », désactivée par défaut.

---

## Phase 5 — Sécurité (S7, ≈ 2 semaines) 🤖
1. 5 PIN faux pendant une alerte → message « tentatives de désactivation » aux proches (S7.01).
2. **Biométrie** (empreinte, visage) en plus du PIN (S7.02).
3. Fermeture forcée détectée par le serveur + « Protection interrompue à HH:MM » (S7.03).
4. `FLAG_SECURE` sur les écrans d'alerte, écran de verrouillage neutre (S7.04).
5. **Effacement d'urgence** (geste + PIN, < 2 s) (S7.05).
6. Guides batterie par marque (Tecno, Infinix, Itel, Samsung, Xiaomi) (S7.06).
7. Console admin minimale (`web/admin`) : santé des envois, abus, support ; aucun accès au contenu (S7.07).
8. Revue du modèle de menace, OWASP MASVS L2, test d'intrusion de la RLS et des fonctions (S7.08, S7.09).

---

## Phase 6 — Tests terrain et lancement (S8, ≈ 3 semaines) 🧑 + 🤖
1. Couverture de tests ≥ 90 % sur la logique ; tests de bout en bout sur staging.
2. **Campagne 20 à 50 téléphones**, dont 20 à Haïti ; réseau faible 2G/EDGE, Digicel et Natcom réels ; mode avion.
3. Batterie : ≤ 4 %/h en écoute, ≤ 3 %/h en trajet.
4. 40 h d'écoute réelle pour les fausses alertes.
5. Accessibilité (TalkBack, texte à 200 %) ; relecture native du créole.
6. **Dossier Play** : sécurité des données, types de service (micro, position) avec vidéos, `SEND_SMS`, « Familles », fiche honnête.
7. Juridique signé : conditions, confidentialité (FR, HT, EN), consentement parental.
8. **Bêta fermée** 50 à 200 personnes, support en 3 langues.
9. **Lancement pilote** progressif 5 → 20 → 50 → 100 %, avec seuils d'arrêt et retour arrière.

---

## Après le MVP (V2, `v2.md`)
IA audio avancée (cris, vitres, coups de feu), appels automatiques, vidéo de preuve, WhatsApp, itinéraire routier, analyse émotionnelle, intégration aux autorités (Q27), PIN de contrainte, mode leurre, iOS.

---

## Ordre recommandé
1. **Cette semaine** : tests téléphone (phase 1) + lancer les démarches longues (Play `SEND_SMS`, fournisseur SMS, juriste, collecte audio).
2. **Pendant les démarches** : écran Réglages, trajet suivi par le serveur, envoi des preuves, mode dégradé, puis sécurité S7.
3. **Dès les comptes reçus** : code SMS, parcours parent réel, notifications push, partage des preuves.
4. **Puis** : campagne terrain, bêta fermée, lancement pilote.

Estimation de développement restant (hors attentes) : **environ 8 à 10 semaines**, plus les délais des démarches (Play, fournisseur SMS, juriste) qui peuvent dépasser.

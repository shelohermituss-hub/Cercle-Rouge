# Comptes à ouvrir ou régler (phase 2)

Dans l'ordre. Les menus peuvent changer de nom : si un intitulé n'existe plus, cherche le mot-clé indiqué. **Ne colle jamais une clé secrète dans le chat.**

## 1. GitHub Pages — 2 min, gratuit · urgent
Débloque : le lien de suivi dans les SMS.
1. github.com → dépôt **shelohermituss-hub/Cercle-Rouge** → **Settings** → **Pages**.
2. **Build and deployment → Source** : **GitHub Actions**.
3. Onglet **Actions** → workflow **pages** → **Run workflow** (branche `ccr-38a56721-9a8jjx`).
4. Vérifier que `https://shelohermituss-hub.github.io/Cercle-Rouge/` affiche « Lien invalide ou expiré » (normal sans jeton).

→ **Me dire** : « Pages OK ».

## 2. Supabase : plan Pro + projet de staging — 15 min, ≈ 25 $/mois
Débloque : pas de mise en pause du projet après 7 jours d'inactivité (un projet gratuit se met en pause : les alertes ne passeraient plus par le serveur), sauvegardes, essais sans toucher la production.
1. supabase.com/dashboard → organisation → **Billing** → passer en **Pro**.
2. Projet « Cercle rouge » → **Database → Backups** : activer **Point in Time Recovery** si le budget le permet (option payante).
3. **New project** : nom « Cercle rouge staging », même région (`us-east-1`), mot de passe de base fort gardé dans un gestionnaire de mots de passe.
4. Dans le projet staging : **Authentication → Sign In / Providers → Allow anonymous sign-ins**.

→ **Me dire** : « staging créé ». J'y appliquerai les mêmes migrations et tests ; la production ne recevra que ce qui est passé par le staging.

## 3. Firebase (notifications push) — 15 min, gratuit
Débloque : réveiller l'app des proches qui l'ont installée, notifications (S3.04, S3.05).
1. console.firebase.google.com → **Ajouter un projet** « Cercle Rouge » ; Google Analytics : **désactivé** (pas de pistage).
2. **Ajouter une application Android** : nom du paquet `com.cerclerouge.app`.
3. Télécharger **google-services.json** → **ne pas me l'envoyer dans le chat** : dépose-le dans `android/app/` sur ton ordinateur et pousse-le sur la branche, ou dis-moi qu'il est prêt et je te dirai comment me le transmettre. (Il n'est pas secret au sens strict, mais il identifie ton projet.)
4. **Paramètres du projet → Comptes de service → Générer une clé privée** (fichier JSON). **Celui-là est secret** : à ajouter toi-même dans Supabase → Edge Functions → **Secrets** sous le nom `FCM_SERVICE_ACCOUNT` (contenu du fichier). Jamais dans le dépôt ni dans le chat.

→ **Me dire** : « Firebase prêt ».

## 4. Google Play Console — 30 min, 25 $ une fois
Débloque : publication, bêta fermée, demande d'exception `SEND_SMS` (délai long : à lancer tôt).
1. play.google.com/console → créer un compte développeur (**Organisation** si tu as une structure légale, sinon Personnel). Vérification d'identité : quelques jours.
2. Attention : les comptes **personnels récents** doivent faire un **test fermé** (environ 12 testeurs pendant 14 jours) avant la production. Prévoir la liste de testeurs.
3. **Créer une application** « Cercle Rouge », gratuite, catégorie **Outils** (ou Style de vie).
4. **Politique de confidentialité** : URL obligatoire (je peux la rédiger et la publier sur GitHub Pages ; à faire valider par le juriste).
5. Remplir **Sécurité des données** et la **déclaration des autorisations** avec `play-send-sms.md`.

→ **Me dire** : « Play Console ouvert » ; je préparerai la version signée (`.aab`) et les fiches.

## 5. Fournisseur SMS
Voir `fournisseur-sms.md`.

# ADR-0009 — Lancement en Haïti, âge minimum 12 ans

- Statut : **Acceptée** · Date : 2026-11-10

## Décision
- Pays de lancement : **Haïti**. Numéros d'urgence par défaut : police 114, pompiers 115, ambulance 116 (à confirmer avant la bêta). Préfixe +509. Opérateurs : Digicel, Natcom.
- Langue par défaut : français ; créole haïtien au même niveau ; anglais pour la diaspora.
- Âge minimum : **12 ans**. Profils : jeune 12–14, ado 15–17, adulte, personne isolée.
- 12–15 ans : consentement d'un parent ou tuteur (mécanisme : Q3b).

## Conséquences
- Le public visé inclut des moins de 13 ans → **politique Play « Familles »** : déclaration du public cible, uniquement des SDK certifiés Familles (à vérifier pour Firebase Cloud Messaging), aucune publicité, politique de confidentialité adaptée aux enfants.
- Anti-surveillance (N9) renforcé : le parent qui consent n'obtient **pas** de suivi permanent ; il reçoit les alertes comme n'importe quel contact, et seulement si le jeune l'ajoute.
- Utilisateurs de la diaspora mineurs : COPPA (États-Unis) et RGPD art. 8 (UE) → à instruire avant d'ouvrir ces pays.

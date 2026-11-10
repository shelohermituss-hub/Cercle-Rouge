# ADR-0014 — Épinglage TLS prudent

- Statut : **Acceptée** · Date : 2026-11-10

## Contexte
L'épinglage protège contre l'interception (fausse autorité de certification installée sur le téléphone, réseau hostile). Mais `*.supabase.co` renouvelle ses certificats : épingler le certificat final couperait toutes les alertes envoyées via le serveur le jour du renouvellement.

## Décision
- Épingler les clés publiques (SPKI) des **autorités de certification** utilisées par Supabase (intermédiaires + racines), avec au moins **2 broches de secours** d'autres autorités crédibles.
- `<pin-set expiration>` à **6 mois** dans la Network Security Config : après cette date, Android revient à la validation TLS standard plutôt que de bloquer.
- Liste des broches vérifiée en S0 (chaîne réelle de `<projet>.supabase.co`) et revue à chaque version de l'app (tâche de release).
- En cas d'échec TLS : **SMS direct** immédiat (R-ALR-04) + événement journalisé.

## Conséquences
+ Protection contre l'interception sans risque de coupure prolongée.
− Mettre à jour l'app au moins tous les 6 mois (déjà nécessaire pour le targetSdk de Play).

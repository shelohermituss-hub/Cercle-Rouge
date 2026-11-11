# Fournisseur SMS pour Haïti (Q13)

## À quoi il sert
1. **Code de connexion par SMS** (OTP Supabase) : le compte anonyme devient un compte vérifié.
2. **Invitation du parent** (12–15 ans) et des proches.
3. **SMS envoyés par le serveur** en secours (téléphone sans crédit, éteint, perte de contact pendant un trajet).

Les SMS d'alerte normaux continuent de partir **du téléphone** de l'utilisateur (gratuit pour nous, marche sans Internet).

## Candidats
| | Twilio | Vonage |
|---|---|---|
| Branché nativement sur l'OTP Supabase | Oui | Oui |
| Haïti | Couvert (pages de conformité par pays) | Couvert, à confirmer avec eux |
| Démarrage | Compte en ligne, carte bancaire | Compte en ligne, carte bancaire |

Ce que j'ai trouvé (sources : guides de Twilio, Telnyx, Messente, Miatel, sent.dm, novembre 2026) — **à faire confirmer par le fournisseur choisi** :
- **Natcom** : les noms d'expéditeur alphabétiques (« CercleRouge ») semblent exiger un **enregistrement préalable** ; sans lui, le nom est remplacé par un expéditeur générique ou le SMS n'arrive pas. Plusieurs sources se contredisent.
- **Digicel** : les expéditeurs alphabétiques semblent acceptés sans enregistrement.
- Un agrégateur mentionne un enregistrement auprès du **CONATEL** pour les envois en nombre (non confirmé par une source officielle).

## Questions à leur envoyer (copier-coller)
> Bonjour, nous lançons en Haïti une application de sécurité personnelle (alertes d'urgence, codes de vérification). Volumes : quelques centaines de SMS par mois au début.
> 1. Livrez-vous les SMS vers **Digicel Haïti** et **Natcom** ? Taux de remise constaté et délai moyen ?
> 2. Quel type d'expéditeur recommandez-vous pour chaque opérateur (alphanumérique « CercleRouge », numéro long, numéro court) ? Un enregistrement est-il nécessaire, avec quels documents et quel délai ?
> 3. Un enregistrement auprès du CONATEL est-il exigé ?
> 4. Prix par SMS vers chaque opérateur ; frais fixes ?
> 5. Rapports de remise (DLR) disponibles pour les deux opérateurs ?
> 6. Les SMS de type « alerte d'urgence » ou « code de vérification » ont-ils une catégorie ou une priorité particulière ?

## Ensuite
1. Ouvrir le compte chez celui qui répond le mieux (remise Natcom = critère n° 1).
2. Dans Supabase : **Authentication → Sign In / Providers → Phone** → choisir le fournisseur → coller l'identifiant et la clé **directement dans Supabase** (jamais dans le chat).
3. Envoyer 20 codes de test vers des numéros Digicel et Natcom ; noter le délai et les échecs (étape S0.09).
4. Me dire « SMS prêt » : je branche la connexion par code, l'invitation du parent et les SMS serveur.

Sources : [Twilio — Haïti](https://www.twilio.com/en-us/guidelines/ht/sms) · [Twilio — expéditeurs alphanumériques](https://support.twilio.com/hc/en-us/articles/223133767-International-support-for-Alphanumeric-Sender-ID) · [Telnyx — Haïti](https://support.telnyx.com/en/articles/6674331-haiti-sms-guidelines) · [Messente — Haïti](https://support.messente.com/haiti-sms-regulations) · [Miatel — Haïti](https://docs.miatel.com/docs/country-specific-features-and-restrictions/americas/haiti) · [sent.dm — Haïti](https://www.sent.dm/resources/haiti-sms-guide)

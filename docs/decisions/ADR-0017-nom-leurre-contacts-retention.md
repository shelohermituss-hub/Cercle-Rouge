# ADR-0017 — Nom, mode leurre, limites de contacts, choix de conservation

- Statut : **Acceptée** · Date : 2026-11-10

## Décisions
| Sujet | Décision |
|---|---|
| Nom (S0.01) | **« Cercle Rouge »**, nom et icône fixes, sur Google Play comme sur l'écran d'accueil |
| Mode leurre (Q8) | **Reporté en V2** (écran leurre interne et icône neutre) |
| Contacts (Q7) | **5 contacts de confiance au maximum ; 3 alertés à la fois au niveau 2** ; les 5 au niveau 3. L'adulte référent d'un mineur compte parmi les 5 |
| Conservation (S0.04) | **Choisie par l'utilisateur pendant l'onboarding** (24 h, 7 j, 30 j ou suppression à la clôture), **sans option présélectionnée**. Modifiable dans les réglages. « Conserver » manuel jusqu'à 90 j pour une plainte |

## Conséquences
- **Visibilité** : sans nom ni icône neutres, et sans mode leurre, quelqu'un qui fouille le téléphone voit que Cercle Rouge est installé (menace T5). Mesures restantes : notifications neutres sur l'écran de verrouillage, `FLAG_SECURE` sur le coffre-fort, mode discret (pas de son ni de texte d'alerte). À rouvrir en V2 si le pilote montre ce besoin (violences conjugales notamment).
- **Conservation** : un écran de plus dans l'onboarding. Si une alerte survient **avant** que l'utilisateur ait choisi (onboarding interrompu, SOS dès le premier écran), on applique **24 h** par défaut technique, la durée la plus protectrice ; l'utilisateur pourra la changer ensuite. Le principe N6 (jamais de conservation illimitée) reste respecté.
- **Contacts** : moins de SMS par alerte (coût) ; le plafond anti-spam passe à 12 SMS par alerte.

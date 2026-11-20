# Phase 1 — Tester l'écoute en local (jalon G1)

**Critère de réussite du jalon G1 : 8 heures d'écoute sans coupure, latence inférieure à 500 ms.**

## Matériel

- Deux téléphones Android 8 ou plus, sur **le même Wi-Fi** (pas de réseau invité, qui isole souvent les appareils).
- Le téléphone bébé branché sur secteur.
- L'APK de la PR ou de la release (`babyphone-*.apk`), installé sur les deux.

## Mise en route

1. **Téléphone bébé** : choisir « Téléphone bébé », puis « Démarrer la surveillance », et accepter le micro et les notifications.
2. Sur ce même téléphone, désactiver l'optimisation de batterie pour Babyphone : *Paramètres → Applications → Babyphone → Batterie → Sans restriction*. Sur Samsung, Xiaomi ou Huawei, voir aussi [dontkillmyapp.com](https://dontkillmyapp.com).
3. **Téléphone parent** : choisir « Téléphone parent ». Le téléphone bébé apparaît dans la liste au bout de quelques secondes ; toucher son nom.
   S'il n'apparaît pas, saisir l'adresse affichée sur l'écran du téléphone bébé (ex. `192.168.1.20:41234`).
4. Le statut passe à **« À l'écoute »**, et le téléphone bébé affiche « 1 parent à l'écoute ».

## Test 1 — Latence (test du clap)

L'estimation affichée sur le téléphone parent est indicative. La mesure de référence :

1. Poser les deux téléphones côte à côte, volume du parent au maximum.
2. Enregistrer la scène avec un troisième appareil (ordinateur ou autre téléphone) qui capte les deux.
3. Taper dans les mains près du téléphone bébé.
4. Dans l'enregistrement (Audacity par exemple), mesurer l'écart entre le clap direct et son écho dans le téléphone parent.

Répéter 5 fois. **Réussi si la moyenne est inférieure à 500 ms.** Noter aussi la valeur estimée affichée, pour l'étalonner.

> Placer les téléphones côte à côte crée un effet Larsen si le volume est trop fort : baisser légèrement si ça siffle.

## Test 2 — Endurance de 8 heures

1. Lancer l'écoute le soir, téléphone parent **écran éteint**, dans une autre pièce.
2. Le matin, ouvrir l'app parent et relever :

| Mesure | Où | Réussi si |
| --- | --- | --- |
| Durée d'écoute | Écran parent | 8 h ou plus |
| Coupures | Écran parent | 0 (ou toutes rétablies seules en moins de 30 s) |
| Notification « Téléphone bébé injoignable » | Notifications parent | Aucune |
| Batterie du téléphone bébé | Écran parent | En charge, 100 % |
| Batterie du téléphone parent | Système | À noter (consommation sur la nuit) |

## Test 3 — Coupures volontaires

| Action | Comportement attendu |
| --- | --- |
| Couper le Wi-Fi du téléphone parent 10 s, puis le rétablir | « Coupure, reconnexion… », puis retour à « À l'écoute » sans rien toucher ; compteur de coupures +1 |
| Couper le Wi-Fi du téléphone bébé plus de 30 s | Notification prioritaire « Téléphone bébé injoignable » sur le parent |
| Arrêter la surveillance sur le téléphone bébé | Même notification au bout de 30 s |
| Redémarrer la box | Reconnexion automatique une fois le Wi-Fi revenu |
| Deux téléphones parents en même temps | Les deux entendent ; le bébé affiche « 2 parents à l'écoute » |

## Remonter un résultat

Ouvrir une issue avec : modèles et versions d'Android des deux téléphones, modèle de box, résultats des trois tests. En cas de coupure, préciser l'heure : elle permettra de retrouver la cause dans les journaux (`adb logcat`).

## Limite de sécurité de cette phase

La connexion locale n'est pas encore authentifiée : tout appareil sur le même Wi-Fi peut se connecter au téléphone bébé (ADR 0007). À n'utiliser que sur le réseau de la maison, jusqu'à l'appairage par QR code de la phase 2.

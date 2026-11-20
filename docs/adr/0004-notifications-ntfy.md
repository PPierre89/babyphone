# ADR 0004 — Notifications auto-hébergées avec ntfy plutôt que Firebase

- Statut : accepté, à réévaluer en phase 3
- Date : 2026-11-20

## Contexte

Le téléphone parent doit recevoir une alerte de pleurs même écran éteint. Sur Android, deux voies existent : Firebase Cloud Messaging (FCM), ou une connexion permanente maintenue par l'app ou par un distributeur UnifiedPush.

## Options étudiées

| Option | Pour | Contre |
| --- | --- | --- |
| FCM | Très fiable, économe en batterie | Dépendance à Google et aux services Play ; les métadonnées passent par Google |
| **ntfy sur le NAS** (UnifiedPush) | Auto-hébergé, aucune donnée chez un tiers | Connexion permanente ; certains fabricants la coupent |

## Décision

ntfy sur le NAS, avec un contenu de notification chiffré par la clé de la famille : ntfy ne voit qu'un message opaque. L'app parent maintient aussi sa connexion de signalisation pendant la nuit, ce qui donne un second canal d'alerte.

## Conséquences

- Pas de dépendance à Google : l'APK fonctionne sur un téléphone sans services Play.
- Le risque de coupure par les gestionnaires de batterie est traité par l'assistant d'exemption et par l'alerte de perte de connexion.
- Si les tests de la phase 3 montrent des alertes manquées, FCM pourra être ajouté en option, avec un message vide qui sert uniquement à réveiller l'app.

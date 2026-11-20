# Sécurité

Un babyphone diffuse le son, et bientôt l'image, d'une chambre d'enfant. Toute faille est traitée en priorité.

## Signaler une vulnérabilité

**Ne pas ouvrir d'issue publique.** Utiliser le signalement privé de GitHub : onglet *Security → Report a vulnerability* du dépôt. Indiquer la version concernée, les étapes pour reproduire et l'impact supposé.

Réponse visée sous 7 jours ; correctif publié dans une version dédiée, avec une note dans la rubrique *Sécurité* des notes de version.

## Versions maintenues

Seule la dernière version publiée reçoit des correctifs. Mettre à jour l'APK et l'image du NAS ensemble (même numéro de version).

## Vérifier ce que vous installez

Chaque release publie des empreintes et des attestations de provenance, qui prouvent que l'APK et l'image ont été construits par la CI de ce dépôt à partir du tag indiqué.

```bash
sha256sum -c SHA256SUMS
gh attestation verify babyphone-X.Y.Z.apk --repo PPierre89/babyphone
gh attestation verify oci://ghcr.io/ppierre89/babyphone-signaling:X.Y.Z --repo PPierre89/babyphone
```

## Modèle de sécurité

Le NAS, le relais TURN et le réseau sont considérés comme non fiables : seuls les téléphones appairés détiennent les clés qui permettent d'écouter la chambre. Détails dans les ADR [0002](docs/adr/0002-webrtc-chiffrement-bout-en-bout.md) et [0005](docs/adr/0005-cle-appareil-ecdsa-p256.md), et dans la section *Sécurité* du [README](README.md#sécurité).

Limites connues de la version actuelle (0.x) :

- l'authentification des appareils auprès du serveur arrive en phase 2 ; d'ici là, l'accès est limité aux familles listées dans `ALLOWED_FAMILIES` ;
- l'épinglage du certificat du NAS dans l'app arrive aussi en phase 2.

# Babyphone

Application babyphone pour Android : un vieux téléphone posé dans la chambre écoute le bébé, le téléphone des parents reçoit les alertes et permet d'écouter. Le serveur est auto-hébergé sur un NAS et ne voit jamais le son ni l'image.

## Principes

- **Audio d'abord** : détection des pleurs sur le téléphone bébé, vidéo uniquement à la demande.
- **Chiffrement de bout en bout** : WebRTC (DTLS-SRTP) entre les téléphones ; le NAS relaie sans déchiffrer.
- **Fonctionne sans le NAS en local** : sur le même Wi-Fi, les téléphones se parlent directement.
- **Aucune dépendance Google obligatoire** : APK hors Play Store, notifications auto-hébergées.
- **Rien n'est enregistré** par défaut.

## État

Phase 0, le socle, est en place : structure du projet, choix du rôle, mesure du niveau sonore en local, serveur de signalisation, déploiement NAS et CI. Le flux audio entre téléphones arrive en phase 1.

| Phase | Contenu | État |
| --- | --- | --- |
| 0 | Socle : dépôt, CI, ADR, NAS | fait |
| 1 | PoC audio WebRTC en local | à faire |
| 2 | Appairage QR et sécurité | à faire |
| 3 | Détection des pleurs et alertes | à faire |
| 4 | Accès à distance (4G) | à faire |
| 5 | V1 : vidéo, talk-back, veilleuse, berceuses | à faire |
| 6 | Bêta et audit de sécurité | à faire |

## Structure

```
android/                 Application Android (Kotlin, Jetpack Compose, Hilt)
├── app/                 Point d'entrée, choix du rôle
├── core/crypto/         Clé d'appareil (Android Keystore), vérification de signatures
├── core/detection/      Détection de bruit (niveau sonore, seuil, délai de garde)
├── core/network/        Client et messages de signalisation
├── feature/pairing/     Format et validation du QR code d'appairage
├── feature/baby/        Service au premier plan qui écoute la chambre
└── feature/parent/      Écran parent
server/signaling/        Serveur de signalisation (Go), voir PROTOCOL.md
deploy/                  Docker Compose pour le NAS : Caddy, signalisation, ntfy, coturn
docs/adr/                Décisions d'architecture
```

## Démarrer

### Application Android

Prérequis : JDK 17 ou plus, SDK Android (plateforme 36).

```bash
cd android
./gradlew testDebugUnitTest assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

L'APK de debug de chaque build est aussi téléchargeable dans l'onglet *Actions* de GitHub.

### Serveur de signalisation

```bash
cd server/signaling
go test -race ./...
ALLOWED_FAMILIES=famille_dev01 go run .
```

### Sur le NAS

Voir [deploy/README.md](deploy/README.md).

## Sécurité

Le modèle de menace et les mesures prévues sont décrits dans le document d'architecture du projet. Déjà en place dans le socle :

- Aucun trafic en clair (Android `networkSecurityConfig`, client `wss://` uniquement), autorités de certification ajoutées par l'utilisateur refusées.
- Aucune sauvegarde cloud ni transfert des données de l'app (`allowBackup=false`, `dataExtractionRules`).
- Clé d'appareil non exportable dans le Keystore (StrongBox si disponible).
- Serveur : familles autorisées uniquement, émetteur imposé par le serveur, limites de taille et de débit, refus des connexions venant d'un navigateur.
- Conteneurs en lecture seule, non root, sans capacités, sur un réseau interne.
- CI : lint, tests, `govulncheck`, Dependabot.

## Décisions d'architecture

- [0001 — Un seul dépôt](docs/adr/0001-monorepo.md)
- [0002 — WebRTC avec chiffrement de bout en bout](docs/adr/0002-webrtc-chiffrement-bout-en-bout.md)
- [0003 — Signalisation en Go](docs/adr/0003-signalisation-go.md)
- [0004 — Notifications avec ntfy](docs/adr/0004-notifications-ntfy.md)
- [0005 — Clé d'appareil ECDSA P-256](docs/adr/0005-cle-appareil-ecdsa-p256.md)

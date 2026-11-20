# Babyphone

[![Android](https://github.com/PPierre89/babyphone/actions/workflows/android.yml/badge.svg)](https://github.com/PPierre89/babyphone/actions/workflows/android.yml)
[![Serveur](https://github.com/PPierre89/babyphone/actions/workflows/server.yml/badge.svg)](https://github.com/PPierre89/babyphone/actions/workflows/server.yml)
[![Release](https://img.shields.io/github/v/release/PPierre89/babyphone?include_prereleases&label=version)](https://github.com/PPierre89/babyphone/releases)

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

## Installer

Télécharger la dernière version sur la page [Releases](https://github.com/PPierre89/babyphone/releases) :

- `babyphone-X.Y.Z.apk` sur les deux téléphones (autoriser l'installation depuis cette source) ;
- sur le NAS, suivre [deploy/README.md](deploy/README.md) avec la même version.

Chaque release est signée et accompagnée d'empreintes et d'attestations de provenance : voir [SECURITY.md](SECURITY.md#vérifier-ce-que-vous-installez).

## Développer

### Application Android

Prérequis : JDK 17 ou plus, SDK Android (plateforme 36).

```bash
cd android
./gradlew testDebugUnitTest assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

L'APK de debug de chaque build est aussi téléchargeable dans l'onglet *Actions* de GitHub. Un build local porte la version `0.0.0-dev`.

### Serveur de signalisation

```bash
cd server/signaling
go test -race ./...
ALLOWED_FAMILIES=famille_dev01 go run .
```

### Sur le NAS

Voir [deploy/README.md](deploy/README.md).

## Documentation

| Document | Contenu |
| --- | --- |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Branches, titres de PR, vérifications locales, ce que fait la CI |
| [docs/RELEASING.md](docs/RELEASING.md) | Publier une version, clé de signature, mise à jour du NAS |
| [SECURITY.md](SECURITY.md) | Signaler une faille, vérifier une release, limites connues |
| [deploy/README.md](deploy/README.md) | Installer et mettre à jour le NAS |
| [server/signaling/PROTOCOL.md](server/signaling/PROTOCOL.md) | Protocole de signalisation |
| [docs/adr/](docs/adr/) | Décisions d'architecture |

## Versions

Une seule version pour l'app, le serveur et le déploiement, au format `X.Y.Z`, fixée par le tag git (ADR 0006). Les notes de chaque version sont sur la page [Releases](https://github.com/PPierre89/babyphone/releases).

## Sécurité

Le modèle de menace et les mesures prévues sont décrits dans le document d'architecture du projet. Déjà en place dans le socle :

- Aucun trafic en clair (Android `networkSecurityConfig`, client `wss://` uniquement), autorités de certification ajoutées par l'utilisateur refusées.
- Aucune sauvegarde cloud ni transfert des données de l'app (`allowBackup=false`, `dataExtractionRules`).
- Clé d'appareil non exportable dans le Keystore (StrongBox si disponible).
- Serveur : familles autorisées uniquement, émetteur imposé par le serveur, limites de taille et de débit, refus des connexions venant d'un navigateur.
- Conteneurs en lecture seule, non root, sans capacités, sur un réseau interne.
- CI : lint, tests, `govulncheck`, actions épinglées par empreinte, Dependabot.
- Releases : APK signé (schéma v3), image avec SBOM, empreintes SHA-256 et attestations de provenance.

## Décisions d'architecture

- [0001 — Un seul dépôt](docs/adr/0001-monorepo.md)
- [0002 — WebRTC avec chiffrement de bout en bout](docs/adr/0002-webrtc-chiffrement-bout-en-bout.md)
- [0003 — Signalisation en Go](docs/adr/0003-signalisation-go.md)
- [0004 — Notifications avec ntfy](docs/adr/0004-notifications-ntfy.md)
- [0005 — Clé d'appareil ECDSA P-256](docs/adr/0005-cle-appareil-ecdsa-p256.md)
- [0006 — Une seule version issue du tag git](docs/adr/0006-versioning.md)

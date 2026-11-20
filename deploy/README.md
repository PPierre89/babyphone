# Déploiement sur le NAS

Trois services dans Docker Compose : **Caddy** (seul exposé, TLS automatique), **signalisation** (Go) et **ntfy** (notifications). Le relais **coturn** s'ajoute en phase 4 pour l'accès en 4G.

## Prérequis

- Un NAS capable de faire tourner Docker Compose (Synology Container Manager, QNAP Container Station, ou Docker installé à la main).
- Deux noms de domaine (ou sous-domaines) pointant vers l'IP publique de la box : un pour la signalisation, un pour les notifications. Un DNS dynamique fait l'affaire.
- Sur la box, redirection vers le NAS des ports **80/tcp** et **443/tcp+udp**. En phase 4 seulement : **3478/udp** et **49160-49200/udp**.

## Installation

Récupérer le paquet `babyphone-deploy-X.Y.Z.tar.gz` de la [dernière release](https://github.com/PPierre89/babyphone/releases) (ou ce dossier du dépôt), puis :

```bash
tar -xzf babyphone-deploy-X.Y.Z.tar.gz && cd deploy
cp .env.example .env
# Renseigner les domaines, l'e-mail, et générer un identifiant de famille :
openssl rand -hex 16
chmod 600 .env

docker compose --env-file .env pull     # image publiée, amd64 ou arm64 selon le NAS
docker compose --env-file .env up -d
docker compose ps        # tous les services doivent être "healthy"
curl https://<SIGNALING_DOMAIN>/healthz   # doit répondre "ok"
```

Créer un compte ntfy par téléphone (l'accès anonyme est refusé) :

```bash
docker compose exec ntfy ntfy user add telephone-parent
docker compose exec ntfy ntfy access telephone-parent "babyphone-<famille>-*" read-only
docker compose exec ntfy ntfy user add telephone-bebe
docker compose exec ntfy ntfy access telephone-bebe "babyphone-<famille>-*" write-only
```

Sans release publiée, ou pour tester une modification locale : `docker compose --env-file .env up -d --build` compile l'image sur le NAS.

## Mettre à jour

Changer `SIGNALING_VERSION` dans `.env`, puis `pull` et `up -d` (détails dans [docs/RELEASING.md](../docs/RELEASING.md#procédure)). La version effectivement lancée apparaît dans `docker compose logs signaling`.

## Phase 4 : accès en 4G

```bash
openssl rand -base64 32   # à mettre dans TURN_SECRET
docker compose --env-file .env --profile remote up -d
```

## Sécurité appliquée

| Mesure | Où |
| --- | --- |
| TLS 1.3 uniquement, HSTS, certificat Let's Encrypt renouvelé automatiquement | `caddy/Caddyfile` |
| Seules `/ws` et `/healthz` publiées pour la signalisation, administration Caddy désactivée | `caddy/Caddyfile` |
| Signalisation et ntfy sur un réseau interne sans accès Internet sortant | `docker-compose.yml` |
| Conteneurs en lecture seule, sans capacités Linux, `no-new-privileges`, mémoire limitée | `docker-compose.yml` |
| Image de signalisation distroless, utilisateur non root, 16 Mo | `server/signaling/Dockerfile` |
| ntfy : accès refusé par défaut, inscriptions fermées | `docker-compose.yml` |
| TURN : identifiants temporaires, relais interdit vers les adresses privées, quotas | `coturn/turnserver.conf` |

À faire côté NAS : mises à jour automatiques du système, NAS dans un VLAN dédié si la box le permet, et sauvegarde du fichier `.env` hors du NAS.

# Contribuer

## Flux de travail

1. Une branche par sujet depuis `main` : `feat/ecoute-audio`, `fix/service-android-14`, `docs/nas-synology`.
2. Une pull request vers `main`. Son **titre** suit [Conventional Commits](https://www.conventionalcommits.org/fr/) : il devient la ligne des notes de version.
3. La CI doit être verte, puis fusion en **squash** : un commit par PR sur `main`, avec le titre de la PR.
4. Pour publier : voir [docs/RELEASING.md](docs/RELEASING.md).

## Titres de PR

```
type(portée): description à l'impératif, en français
```

| Type | Usage | Rubrique des notes |
| --- | --- | --- |
| `feat` | Nouvelle fonctionnalité | Nouveautés |
| `fix` | Correction de bug | Correctifs |
| `sec` | Correctif ou durcissement de sécurité | Sécurité |
| `perf` | Performance, batterie | Performances |
| `docs` | Documentation seule | Documentation |
| `refactor`, `test`, `build`, `ci`, `chore`, `revert` | Le reste | Maintenance |

Portées usuelles : `android`, `server`, `deploy`, `ci`, `protocol`. Un `!` après le type signale un changement incompatible : `feat(protocol)!: authentification par défi signé`.

Le workflow [`pr.yml`](.github/workflows/pr.yml) refuse un titre mal formé et pose l'étiquette correspondante.

## Vérifier avant de pousser

La CI lance les mêmes commandes.

```bash
# Android
cd android && ./gradlew lint testDebugUnitTest assembleDebug

# Serveur
cd server/signaling && gofmt -l . && go vet ./... && go test -race ./...

# Déploiement
cd deploy && docker compose --env-file .env.example config -q
```

## Ce que fait la CI

| Workflow | Quand | Ce qu'il vérifie |
| --- | --- | --- |
| `android.yml` | `android/**` modifié | Lint, tests unitaires, APK debug téléchargeable ; build release (R8) sur `main` uniquement |
| `server.yml` | `server/**` ou `deploy/**` modifié | `gofmt`, `go vet`, tests avec détecteur de concurrence et couverture, `govulncheck`, image Docker (arm64 en plus sur `main`), `docker compose` et Caddyfile valides |
| `pr.yml` | Toute PR | Format du titre, étiquette |
| `release.yml` | Tag `v*` | Voir [docs/RELEASING.md](docs/RELEASING.md) |

Les actions GitHub sont **épinglées par empreinte de commit** (et non par tag, qui peut être déplacé) ; Dependabot les met à jour chaque semaine avec les dépendances Gradle, Go et Docker.

## Règles du code

- **Protocole** : toute évolution modifie dans la même PR `server/signaling/PROTOCOL.md`, le code Go et `SignalingMessage.kt`.
- **Décision structurante** (nouvelle dépendance importante, changement d'architecture ou de sécurité) : un ADR dans `docs/adr/`.
- **Secrets** : jamais dans le dépôt. Configuration par variables d'environnement, `.env` et clés ignorés par git.
- **Journaux** : jamais de contenu de message, de jeton ni de clé.
- Les avertissements Kotlin sont des erreurs ; le lint Android bloque sur toute erreur.

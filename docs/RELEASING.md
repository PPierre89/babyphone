# Publier une version

Une release = un tag `vX.Y.Z` poussé sur `main`. Le workflow [`release.yml`](../.github/workflows/release.yml) fait le reste en 10 minutes environ. Les règles de numérotation sont dans l'[ADR 0006](adr/0006-versioning.md).

## Ce que produit une release

| Livrable | Où |
| --- | --- |
| `babyphone-X.Y.Z.apk`, signé avec la clé de release, vérifié par `apksigner` | Page de la release GitHub |
| Image `ghcr.io/ppierre89/babyphone-signaling:X.Y.Z` (+ `X.Y` et `latest`), amd64 et arm64, avec SBOM | GitHub Container Registry |
| `babyphone-deploy-X.Y.Z.tar.gz` : le dossier `deploy/`, `SIGNALING_VERSION` déjà renseigné | Page de la release |
| `SHA256SUMS` et attestations de provenance (APK et image) | Page de la release, GitHub |
| Notes de version classées (nouveautés, correctifs, sécurité…) | Générées depuis les titres des PR |

## Choisir le numéro

| Changement depuis la dernière version | Exemple | Nouvelle version |
| --- | --- | --- |
| Correctifs uniquement | 0.3.1 → | 0.3.2 |
| Nouvelle fonctionnalité compatible | 0.3.2 → | 0.4.0 |
| Protocole ou format incompatible (tant qu'on est en 0.x) | 0.4.0 → | 0.5.0 |
| Préversion à tester sur les téléphones | | 0.5.0-rc.1 |

## Procédure

```bash
git switch main && git pull
git log --oneline $(git describe --tags --abbrev=0 2>/dev/null)..HEAD   # ce qui part dans la version
git tag -a v0.2.0 -m "Babyphone 0.2.0"
git push origin v0.2.0
```

Suivre l'avancement dans l'onglet *Actions*. Le workflow refuse un tag mal formé ou qui ne pointe pas sur `main`.

Ensuite, sur le NAS :

```bash
cd deploy
sed -i 's/^SIGNALING_VERSION=.*/SIGNALING_VERSION=0.2.0/' .env
docker compose --env-file .env pull && docker compose --env-file .env up -d
docker compose logs signaling | grep version
```

Et sur les téléphones : installer `babyphone-0.2.0.apk` par-dessus l'ancienne version (les données sont conservées tant que la clé de signature ne change pas).

## Annuler une release ratée

```bash
gh release delete v0.2.0 --yes
git push --delete origin v0.2.0 && git tag -d v0.2.0
```

Corriger, puis republier sous un **nouveau** numéro (0.2.1) : un numéro déjà installé sur un téléphone ne doit jamais désigner un autre APK.

## Clé de signature (à faire une seule fois)

Android n'accepte une mise à jour que si elle est signée avec la même clé que la version installée. **Perdre cette clé oblige à désinstaller l'app sur tous les téléphones** (et donc à refaire l'appairage).

1. Créer la clé, sur votre poste et non sur le NAS :

   ```bash
   keytool -genkeypair -v -keystore babyphone-release.jks -alias babyphone \
     -keyalg EC -groupname secp256r1 -validity 10000 \
     -dname "CN=Babyphone, O=Penpenic, C=FR"
   ```

2. La sauvegarder dans le gestionnaire de mots de passe, avec ses deux mots de passe, **et** sur un support hors ligne.

3. La déclarer dans GitHub (*Settings → Secrets and variables → Actions*), ou en ligne de commande :

   ```bash
   base64 -w0 babyphone-release.jks | gh secret set BABYPHONE_KEYSTORE_BASE64 --repo PPierre89/babyphone
   gh secret set BABYPHONE_KEYSTORE_PASSWORD --repo PPierre89/babyphone
   gh secret set BABYPHONE_KEY_ALIAS --repo PPierre89/babyphone --body babyphone
   gh secret set BABYPHONE_KEY_PASSWORD --repo PPierre89/babyphone
   ```

4. Supprimer le fichier `.jks` du dossier de travail. Il ne doit jamais être commité (`.gitignore` le bloque).

## Image du NAS : visibilité

À la première release, le paquet `babyphone-signaling` est créé **privé** sur GitHub. Deux possibilités :

- le rendre public (*Packages → babyphone-signaling → Package settings → Change visibility*) : il ne contient aucun secret ;
- ou le laisser privé et connecter le NAS : `docker login ghcr.io -u PPierre89` avec un jeton limité à `read:packages`.

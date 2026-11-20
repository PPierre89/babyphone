# ADR 0006 — Une seule version pour tout le projet, issue du tag git

- Statut : accepté
- Date : 2026-11-20

## Contexte

L'application et le serveur partagent un protocole : une app 0.3 doit fonctionner avec le serveur 0.3. Il faut aussi qu'Android accepte chaque mise à jour (`versionCode` strictement croissant) et que le NAS sache quelle image installer.

## Options étudiées

| Option | Pour | Contre |
| --- | --- | --- |
| Versions séparées app / serveur | Chaque partie évolue à son rythme | Matrice de compatibilité à tenir, confusion pour un seul mainteneur |
| release-please (PR de release automatique) | Changelog et version calculés depuis les commits | Outil de plus ; les PR ouvertes par le jeton GitHub ne déclenchent pas la CI |
| **Tag git `vX.Y.Z` comme source unique** | Simple, aucun fichier de version à modifier, impossible d'oublier un endroit | Le changelog dépend de titres de PR bien écrits |

## Décision

- **Une seule version** pour l'app, le serveur et la configuration de déploiement, au format [SemVer](https://semver.org/lang/fr/) `X.Y.Z` (suffixe `-rc.N` pour les préversions).
- **Le tag `vX.Y.Z` sur `main` est la source de vérité.** Le workflow de release le lit et l'injecte :
  - Android : `versionName = X.Y.Z`, `versionCode = X × 1 000 000 + Y × 1 000 + Z` ;
  - serveur : `-ldflags "-X main.version=X.Y.Z"`, visible avec `signaling -version` et dans les logs ;
  - image : `ghcr.io/ppierre89/babyphone-signaling:X.Y.Z`, `X.Y` et `latest` (sauf préversion).
- **Titres de PR au format Conventional Commits** (`feat:`, `fix:`, `docs:`…), vérifiés par la CI et transformés en étiquettes. Les notes de version GitHub sont générées à partir de ces PR, classées par étiquette (`.github/release.yml`).
- **Tant que la version est 0.x**, une évolution incompatible du protocole incrémente Y. À partir de 1.0, elle incrémente X.

## Conséquences

- Aucun fichier de version à modifier à la main ; un build local s'appelle `0.0.0-dev`.
- Le `versionCode` limite Y et Z à 999, ce qui laisse de la marge.
- Le NAS épingle une version précise (`SIGNALING_VERSION` dans `.env`) : une mise à jour est un choix explicite.

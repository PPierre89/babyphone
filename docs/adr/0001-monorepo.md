# ADR 0001 — Un seul dépôt pour l'app, le serveur et le déploiement

- Statut : accepté
- Date : 2026-11-20

## Contexte

Le projet a trois parties qui évoluent ensemble : l'application Android, le serveur de signalisation et la configuration du NAS. Le protocole de signalisation est partagé entre l'app et le serveur.

## Décision

Un seul dépôt `babyphone` : `android/`, `server/signaling/`, `deploy/`, `docs/`. Chaque partie a son propre workflow CI, déclenché uniquement quand ses fichiers changent.

## Conséquences

- Un changement de protocole se fait dans une seule PR, app et serveur ensemble.
- La CI reste rapide grâce au filtrage par chemin.
- Si une partie devient publique ou partagée seule, il faudra la séparer.

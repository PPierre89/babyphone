# ADR 0003 — Serveur de signalisation en Go

- Statut : accepté
- Date : 2026-11-20

## Contexte

Le serveur tourne en continu sur un NAS aux ressources limitées et sera exposé sur Internet. Il fait peu de choses : authentifier les appareils, relayer de petits messages JSON entre appareils d'une même famille.

## Options étudiées

| Option | Pour | Contre |
| --- | --- | --- |
| Kotlin / Ktor | Même langage que l'app, modèles partagés | JVM : 150 à 250 Mo de RAM, image lourde |
| Node.js | Écosystème WebSocket riche | Beaucoup de dépendances à surveiller |
| **Go** | Binaire statique, environ 10 Mo de RAM, image distroless de 16 Mo, peu de dépendances | Modèles du protocole dupliqués entre Kotlin et Go |

## Décision

Go, avec deux dépendances seulement : `github.com/coder/websocket` et `golang.org/x/time/rate`. Le protocole est documenté dans `server/signaling/PROTOCOL.md` et testé des deux côtés.

## Conséquences

- Surface d'attaque réduite : pas de shell dans l'image, utilisateur non root, système de fichiers en lecture seule.
- Toute évolution du protocole doit mettre à jour `PROTOCOL.md`, le code Go et `SignalingMessage.kt` dans la même PR.

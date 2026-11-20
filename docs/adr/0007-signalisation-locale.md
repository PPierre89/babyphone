# ADR 0007 — Signalisation directe sur le Wi-Fi, sans le NAS

- Statut : accepté
- Date : 2026-11-20

## Contexte

La phase 1 doit prouver qu'on transmet le son de la chambre de façon fiable et rapide. Le serveur du NAS existe, mais il n'est pas encore installé, n'a pas de nom de domaine ni de certificat, et l'authentification des appareils n'arrivera qu'en phase 2. Le plan prévoit de toute façon un **mode local qui fonctionne sans le NAS** (principe d'architecture).

## Décision

1. **Le téléphone bébé ouvre un petit serveur de signalisation** sur le Wi-Fi (TCP, port choisi par le système) et s'annonce en mDNS (`_babyphone._tcp`). Le téléphone parent le découvre, ou l'adresse est saisie à la main.
2. **Messages** : le même format JSON que la signalisation du NAS (`SignalingMessage`), un par ligne, plus deux types propres au mode local : `ping` (battement de cœur toutes les 10 s, coupure après 30 s de silence) et `hangup`.
3. **WebRTC sans serveur STUN ni TURN** : seules les adresses locales sont proposées. Aucun service tiers n'est contacté et rien ne sort du réseau domestique.
4. **Garde-fous en attendant l'authentification** : connexions acceptées uniquement depuis des adresses privées, 4 connexions au plus, trames limitées à 64 Kio, émetteur imposé par la connexion.

## Risque accepté jusqu'à la phase 2

La signalisation locale n'est **ni chiffrée ni authentifiée**. Le son reste chiffré par WebRTC (DTLS-SRTP), mais **un appareil malveillant présent sur le même Wi-Fi pourrait se connecter au téléphone bébé et écouter**, ou s'interposer pendant la négociation.

Ce risque est acceptable pour un test sur le réseau de la maison, pas au-delà. Il disparaît en phase 2 :

- seuls les téléphones appairés par QR code seront acceptés (signature d'un défi avec la clé Keystore) ;
- les offres et réponses WebRTC seront signées, et l'empreinte DTLS vérifiée par le pair.

## Conséquences

- Le PoC fonctionne avec deux téléphones et une box, sans NAS.
- Le mode local restera en production comme mode de secours quand le NAS est injoignable.
- Le code de session WebRTC (`AudioPeerSession`) ne dépend pas du transport : la phase 4 réutilisera le même code avec la signalisation du NAS et le relais TURN.

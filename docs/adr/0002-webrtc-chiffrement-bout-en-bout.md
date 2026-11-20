# ADR 0002 — WebRTC avec chiffrement de bout en bout, le NAS ne déchiffre rien

- Statut : accepté
- Date : 2026-11-20

## Contexte

L'audio et la vidéo de la chambre sont les données les plus sensibles du projet. Il faut une faible latence, fonctionner en Wi-Fi comme en 4G derrière un NAT, et résister à une compromission du NAS.

## Options étudiées

| Option | Pour | Contre |
| --- | --- | --- |
| Flux relayé et déchiffré par le NAS (RTSP, HLS) | Simple | Le NAS voit tout ; latence élevée (HLS) |
| WebSocket avec chiffrement maison | Contrôle total | Cryptographie maison, gestion du NAT à refaire |
| **WebRTC** | DTLS-SRTP obligatoire, traversée de NAT (ICE/STUN/TURN), latence < 500 ms | Bibliothèque native lourde, signalisation à fournir |

## Décision

WebRTC entre les deux téléphones. Le NAS fournit la signalisation et, si nécessaire, un relais TURN qui ne voit que des paquets chiffrés.

Pour empêcher le NAS de s'interposer (en substituant ses propres clés DTLS), les offres et réponses WebRTC seront **signées par la clé d'appareil** stockée dans l'Android Keystore (phase 2), et l'empreinte DTLS vérifiée par le pair.

## Conséquences

- Une compromission du NAS ne permet pas d'écouter la chambre ; au pire, elle coupe le service.
- Sur le même Wi-Fi, les téléphones se connectent directement, sans passer par le NAS.
- L'APK grossit d'environ 10 Mo par architecture à cause de la bibliothèque WebRTC.

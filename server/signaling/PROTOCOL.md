# Protocole de signalisation

Connexion WebSocket : `wss://<domaine>/ws?family=<id>&device=<id>`

- Identifiants : 8 à 64 caractères parmi `A-Z a-z 0-9 _ -`.
- Phase 0 : la famille doit figurer dans `ALLOWED_FAMILIES`.
- Phase 2 : l'appareil prouvera son identité en signant un défi avec sa clé Keystore.
- Une requête portant un en-tête `Origin` (page web) est refusée.

## Enveloppe

Tous les messages sont des objets JSON texte :

```json
{ "type": "offer", "to": "bebe_chambre", "from": "parent_pierre", "payload": { } }
```

| Champ | Rôle |
| --- | --- |
| `type` | Type du message (voir ci-dessous). |
| `to` | Appareil destinataire dans la même famille. Absent = toute la famille sauf l'émetteur. |
| `from` | Toujours renseigné par le serveur ; la valeur envoyée par le client est écrasée. |
| `payload` | Contenu opaque pour le serveur, jamais journalisé. Signé dès la phase 2, chiffré pour les alertes. |

## Messages relayés (client → client)

| Type | Usage |
| --- | --- |
| `offer` / `answer` | Description de session WebRTC (SDP). |
| `ice` | Candidat ICE. |
| `alert` | Pleurs ou bruit détectés par le téléphone bébé. |
| `status` | Batterie, charge, qualité réseau du téléphone bébé. |

Tout autre type envoyé par un client est refusé (`error` / `invalid_message`).

## Messages émis par le serveur

| Type | Quand |
| --- | --- |
| `peers` | À la connexion : `{"peers": ["id1", "id2"]}`. |
| `peer_joined` / `peer_left` | Un appareil de la famille se connecte ou se déconnecte (`from`). |
| `error` | `{"code": "peer_offline" \| "invalid_message" \| "invalid_recipient" \| "text_only"}`. |

## Limites

| Limite | Valeur | Dépassement |
| --- | --- | --- |
| Taille d'un message | 64 Kio | fermeture 1009 |
| Débit | 20 messages/s, rafale de 40 | fermeture 1008 |
| Appareils par famille | 8 | fermeture 1008 |
| Reconnexion d'un même appareil | — | l'ancienne connexion est fermée |

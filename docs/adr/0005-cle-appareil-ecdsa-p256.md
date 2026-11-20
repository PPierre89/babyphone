# ADR 0005 — Clé d'appareil ECDSA P-256 plutôt qu'Ed25519

- Statut : accepté
- Date : 2026-11-20

## Contexte

Le document d'architecture prévoyait des clés Ed25519. L'application cible Android 8 et plus (minSdk 26).

## Décision

ECDSA sur la courbe P-256 avec SHA-256, générée dans l'Android Keystore (StrongBox quand le téléphone en dispose), non exportable.

L'Android Keystore ne propose Ed25519 qu'à partir d'Android 13. Garder Ed25519 aurait obligé soit à exclure les téléphones plus anciens, souvent recyclés en téléphone bébé, soit à stocker la clé privée hors du matériel sécurisé.

## Conséquences

- Toutes les clés restent dans le matériel sécurisé, sur tous les téléphones pris en charge.
- Signatures un peu plus longues (environ 70 octets en DER contre 64), sans impact pratique.
- Vérification côté serveur possible avec la bibliothèque standard de Go (`crypto/ecdsa`).

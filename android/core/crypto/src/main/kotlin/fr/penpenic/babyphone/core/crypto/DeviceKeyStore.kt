package fr.penpenic.babyphone.core.crypto

/**
 * Identité cryptographique de l'appareil.
 *
 * La clé privée ne quitte jamais le matériel sécurisé du téléphone : on ne peut
 * qu'obtenir la clé publique (à partager lors de l'appairage) et faire signer des données.
 */
interface DeviceKeyStore {
    /** Clé publique encodée en X.509 (SubjectPublicKeyInfo). Crée la paire de clés au premier appel. */
    fun publicKey(): ByteArray

    /** Signature ECDSA P-256 / SHA-256 au format DER. */
    fun sign(data: ByteArray): ByteArray
}

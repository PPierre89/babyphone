package fr.penpenic.babyphone.core.crypto

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** Vérifie les signatures produites par un appareil appairé, à partir de sa clé publique. */
object SignatureVerifier {

    fun verify(publicKeyX509: ByteArray, data: ByteArray, signature: ByteArray): Boolean = try {
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKeyX509))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(data)
            verify(signature)
        }
    } catch (e: GeneralSecurityException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }

    /**
     * Empreinte courte d'une clé publique (SHA-256, 8 premiers octets en hexadécimal),
     * affichée aux parents pour comparer visuellement deux appareils.
     */
    fun fingerprint(publicKeyX509: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(publicKeyX509)
            .take(8)
            .joinToString(":") { "%02X".format(it) }
}

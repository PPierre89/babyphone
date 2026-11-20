package fr.penpenic.babyphone.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class SignatureVerifierTest {

    private val keyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun sign(data: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(keyPair.private)
        update(data)
        sign()
    }

    @Test
    fun `accepte une signature valide`() {
        val data = "offre-webrtc".toByteArray()
        assertTrue(SignatureVerifier.verify(keyPair.public.encoded, data, sign(data)))
    }

    @Test
    fun `refuse des données modifiées`() {
        val signature = sign("offre-webrtc".toByteArray())
        assertFalse(SignatureVerifier.verify(keyPair.public.encoded, "offre-pirate".toByteArray(), signature))
    }

    @Test
    fun `refuse une clé publique invalide sans planter`() {
        assertFalse(SignatureVerifier.verify(byteArrayOf(1, 2, 3), "x".toByteArray(), byteArrayOf(4, 5)))
    }

    @Test
    fun `empreinte stable de 8 octets`() {
        val fp = SignatureVerifier.fingerprint(keyPair.public.encoded)
        assertEquals(8, fp.split(":").size)
        assertEquals(fp, SignatureVerifier.fingerprint(keyPair.public.encoded))
    }
}

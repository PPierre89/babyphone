package fr.penpenic.babyphone.feature.pairing

import fr.penpenic.babyphone.feature.pairing.PairingPayload.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingPayloadTest {

    private val now = 1_800_000_000L
    private val payload = PairingPayload(
        familyId = "famille_0123",
        deviceId = "bebe-chambre-1",
        publicKey = ByteArray(91) { it.toByte() },
        serverUrl = "wss://babyphone.exemple.fr",
        token = "AbCdEfGhIjKlMnOpQrStUvWx",
        expiresAtEpochSec = now + PairingPayload.VALIDITY_SECONDS,
    )

    private fun reason(text: String) = (PairingPayload.parse(text, now) as ParseResult.Invalid).reason

    @Test
    fun `aller-retour par le QR code`() {
        val result = PairingPayload.parse(payload.toUri(), now)
        assertEquals(ParseResult.Valid(payload), result)
    }

    @Test
    fun `refuse un QR code expiré`() {
        val result = PairingPayload.parse(payload.toUri(), now + 121)
        assertTrue(result is ParseResult.Invalid)
    }

    @Test
    fun `refuse un serveur non chiffré`() {
        val uri = payload.copy(serverUrl = "ws://babyphone.exemple.fr").toUri()
        assertTrue(reason(uri).contains("wss://"))
    }

    @Test
    fun `refuse un jeton trop court`() {
        assertEquals("Jeton d'appairage invalide", reason(payload.copy(token = "court").toUri()))
    }

    @Test
    fun `refuse une validité anormalement longue`() {
        assertEquals("Durée de validité anormale", reason(payload.copy(expiresAtEpochSec = now + 86_400).toUri()))
    }

    @Test
    fun `refuse un autre schéma`() {
        assertEquals("Ce n'est pas un QR code Babyphone", reason("https://exemple.fr/pair?v=1"))
    }

    @Test
    fun `refuse un identifiant avec caractères spéciaux`() {
        assertEquals("Identifiant de famille invalide", reason(payload.copy(familyId = "a/../../etc").toUri()))
    }

    @Test
    fun `le jeton n'apparaît pas dans toString`() {
        assertFalse(payload.toString().contains(payload.token))
    }
}

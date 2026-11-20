package fr.penpenic.babyphone.core.network

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SignalingMessageTest {

    @Test
    fun `aller-retour JSON`() {
        val msg = SignalingMessage(
            type = SignalingMessage.OFFER,
            to = "parent-1",
            payload = buildJsonObject { put("sdp", JsonPrimitive("v=0")) },
        )
        assertEquals(msg, SignalingMessage.decode(msg.encode()))
    }

    @Test
    fun `les champs nuls ne sont pas envoyés`() {
        val text = SignalingMessage(type = SignalingMessage.ALERT).encode()
        assertFalse(text.contains("null"))
        assertEquals("""{"type":"alert"}""", text)
    }

    @Test
    fun `les champs inconnus du serveur sont ignorés`() {
        val msg = SignalingMessage.decode("""{"type":"peer_left","from":"bebe","extra":42}""")
        assertEquals(SignalingMessage.PEER_LEFT, msg.type)
        assertEquals("bebe", msg.from)
        assertNull(msg.payload)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `refuse une adresse non chiffrée`() {
        SignalingClient("ws://nas.local")
    }
}

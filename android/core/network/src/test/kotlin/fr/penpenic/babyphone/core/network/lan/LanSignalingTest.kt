package fr.penpenic.babyphone.core.network.lan

import fr.penpenic.babyphone.core.network.SignalingMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.Socket
import kotlin.time.Duration.Companion.milliseconds

class LanSignalingTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `echange de messages dans les deux sens`() = runBlocking {
        val accepted = CompletableDeferred<LanConnection>()
        val server = LanSignalingServer(scope, { accepted.complete(it) })
        val port = server.start()

        val client = LanSignalingClient.connect("127.0.0.1", port, scope)
        val babySide = withTimeout(2_000) { accepted.await() }

        val offer = SignalingMessage(
            type = SignalingMessage.OFFER,
            payload = buildJsonObject { put("sdp", JsonPrimitive("v=0")) },
        )
        assertTrue(client.send(offer))
        val received = withTimeout(2_000) { babySide.incoming.first() }
        assertEquals(SignalingMessage.OFFER, received.type)
        assertEquals(offer.payload, received.payload)
        assertEquals("L'émetteur est imposé par la connexion", babySide.id, received.from)

        babySide.send(SignalingMessage(type = SignalingMessage.ANSWER))
        assertEquals(SignalingMessage.ANSWER, withTimeout(2_000) { client.incoming.first() }.type)

        client.close()
        server.stop()
    }

    @Test
    fun `refuse les adresses hors reseau local`() = runBlocking {
        var connections = 0
        val server = LanSignalingServer(scope, { connections++ }, isAllowed = { false })
        val port = server.start()
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 2_000
            assertEquals("La connexion doit être fermée aussitôt", -1, socket.getInputStream().read())
        }
        assertEquals(0, connections)
        server.stop()
    }

    @Test
    fun `limite le nombre de connexions simultanees`() = runBlocking {
        val server = LanSignalingServer(scope, { }, maxConnections = 1)
        val port = server.start()
        LanSignalingClient.connect("127.0.0.1", port, scope)
        Thread.sleep(200)
        Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 2_000
            assertEquals(-1, socket.getInputStream().read())
        }
        server.stop()
    }

    @Test
    fun `ferme une trame trop longue`() = runBlocking {
        val accepted = CompletableDeferred<LanConnection>()
        val server = LanSignalingServer(scope, { accepted.complete(it) })
        val port = server.start()
        Socket("127.0.0.1", port).use { socket ->
            val babySide = withTimeout(2_000) { accepted.await() }
            socket.getOutputStream().write(ByteArray(LanConnection.MAX_FRAME_BYTES + 10) { 'a'.code.toByte() })
            socket.getOutputStream().flush()
            withTimeout(2_000) { babySide.closed.first { it } }
        }
        server.stop()
    }

    @Test
    fun `ferme une connexion muette apres le delai`() = runBlocking {
        val accepted = CompletableDeferred<LanConnection>()
        val server = LanSignalingServer(scope, { accepted.complete(it) }, heartbeat = 50.milliseconds, timeout = 200.milliseconds)
        val port = server.start()
        Socket("127.0.0.1", port).use { // ce client n'envoie jamais rien, pas même de ping
            val babySide = withTimeout(2_000) { accepted.await() }
            withTimeout(2_000) { babySide.closed.first { it } }
        }
        server.stop()
    }

    @Test
    fun `les battements de coeur gardent la connexion ouverte`() = runBlocking {
        val accepted = CompletableDeferred<LanConnection>()
        val server = LanSignalingServer(scope, { accepted.complete(it) }, heartbeat = 50.milliseconds, timeout = 200.milliseconds)
        val port = server.start()
        val client = LanSignalingClient.connect("127.0.0.1", port, scope, heartbeat = 50.milliseconds, timeout = 200.milliseconds)
        val babySide = withTimeout(2_000) { accepted.await() }
        Thread.sleep(600)
        assertFalse(babySide.closed.value)
        assertFalse(client.closed.value)
        client.close()
        withTimeout(2_000) { babySide.closed.first { it } }
        server.stop()
    }

    @Test
    fun `reconnait les adresses du reseau domestique`() {
        listOf("192.168.1.20", "10.0.0.5", "172.16.3.4", "169.254.1.1", "127.0.0.1", "fd12:3456::1", "fe80::1").forEach {
            assertTrue(it, isPrivateLanAddress(InetAddress.getByName(it)))
        }
        listOf("8.8.8.8", "203.0.113.7", "2001:db8::1").forEach {
            assertFalse(it, isPrivateLanAddress(InetAddress.getByName(it)))
        }
    }
}

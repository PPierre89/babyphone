package fr.penpenic.babyphone.core.network.lan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Serveur de signalisation du téléphone bébé, joignable uniquement depuis le réseau local.
 *
 * Phase 1 (PoC) : n'authentifie pas encore le téléphone parent (voir ADR 0007). Garde-fous :
 * adresses privées uniquement, nombre de connexions limité, trames bornées. Le flux audio
 * reste chiffré par WebRTC (DTLS-SRTP).
 */
class LanSignalingServer(
    private val scope: CoroutineScope,
    private val onConnection: (LanConnection) -> Unit,
    private val maxConnections: Int = 4,
    private val isAllowed: (InetAddress) -> Boolean = ::isPrivateLanAddress,
    private val heartbeat: Duration = 10.seconds,
    private val timeout: Duration = 30.seconds,
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val active = AtomicInteger(0)
    private val counter = AtomicInteger(0)

    /** Ouvre le serveur sur [port] (0 = port libre choisi par le système) et renvoie le port effectif. */
    fun start(port: Int = 0): Int {
        val socket = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(port), 8)
        }
        serverSocket = socket
        acceptJob = scope.launch(Dispatchers.IO) { acceptLoop(socket) }
        return socket.localPort
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        acceptJob?.cancel()
        serverSocket = null
    }

    private fun acceptLoop(server: ServerSocket) {
        while (!server.isClosed) {
            val client: Socket = try {
                server.accept()
            } catch (e: IOException) {
                return
            }
            if (!isAllowed(client.inetAddress) || active.get() >= maxConnections) {
                runCatching { client.close() }
                continue
            }
            active.incrementAndGet()
            val connection = LanConnection(client, "parent-${counter.incrementAndGet()}", scope, heartbeat, timeout)
            scope.launch {
                connection.closed.first { it }
                active.decrementAndGet()
            }
            onConnection(connection)
        }
    }
}

/** Adresses du réseau domestique : privées (RFC 1918), lien local, ULA IPv6, boucle locale. */
fun isPrivateLanAddress(address: InetAddress): Boolean =
    address.isSiteLocalAddress ||
        address.isLinkLocalAddress ||
        address.isLoopbackAddress ||
        (address is Inet6Address && (address.address[0].toInt() and 0xFE) == 0xFC)

/** Connexion du téléphone parent vers le téléphone bébé. */
object LanSignalingClient {
    fun connect(
        host: String,
        port: Int,
        scope: CoroutineScope,
        connectTimeout: Duration = 5.seconds,
        heartbeat: Duration = 10.seconds,
        timeout: Duration = 30.seconds,
    ): LanConnection {
        val socket = Socket()
        socket.connect(InetSocketAddress(host, port), connectTimeout.inWholeMilliseconds.toInt())
        if (!isPrivateLanAddress(socket.inetAddress)) {
            socket.close()
            throw IOException("Le téléphone bébé doit être sur le réseau local")
        }
        return LanConnection(socket, "bebe", scope, heartbeat, timeout)
    }
}

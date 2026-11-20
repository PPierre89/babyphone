package fr.penpenic.babyphone.core.network.lan

import fr.penpenic.babyphone.core.network.SignalingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Connexion de signalisation directe entre deux téléphones du même Wi-Fi.
 *
 * Trames : un message JSON [SignalingMessage] par ligne (UTF-8, `\n`), 64 Kio maximum.
 * Battement de cœur : chaque côté envoie `ping` toutes les [heartbeat] ; sans rien recevoir
 * pendant [timeout], la connexion est fermée (le parent est alors alerté et se reconnecte).
 */
class LanConnection internal constructor(
    private val socket: Socket,
    val id: String,
    scope: CoroutineScope,
    private val heartbeat: Duration = 10.seconds,
    private val timeout: Duration = 30.seconds,
) {
    val remoteAddress: String = socket.inetAddress.hostAddress.orEmpty()

    private val inbox = Channel<SignalingMessage>(capacity = 64)
    private val output: OutputStream = socket.getOutputStream()
    private val closing = AtomicBoolean(false)
    private val _closed = MutableStateFlow(false)
    private val jobs = mutableListOf<Job>()

    @Volatile
    private var lastReceivedAt = System.nanoTime()

    /** Messages reçus (hors `ping`). Un seul collecteur. Se termine à la fermeture. */
    val incoming: Flow<SignalingMessage> = inbox.receiveAsFlow()

    /** Passe à `true` une fois la connexion fermée, quelle qu'en soit la raison. */
    val closed: StateFlow<Boolean> = _closed.asStateFlow()

    init {
        socket.tcpNoDelay = true
        socket.keepAlive = true
        jobs += scope.launch(Dispatchers.IO) { readLoop(BufferedInputStream(socket.getInputStream())) }
        jobs += scope.launch(Dispatchers.IO) { heartbeatLoop() }
    }

    fun send(message: SignalingMessage): Boolean {
        if (_closed.value) return false
        val bytes = (message.encode() + "\n").toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_FRAME_BYTES) return false
        return try {
            synchronized(output) {
                output.write(bytes)
                output.flush()
            }
            true
        } catch (e: IOException) {
            close()
            false
        }
    }

    fun close() {
        if (!closing.compareAndSet(false, true)) return
        _closed.value = true
        runCatching { socket.close() }
        inbox.close()
        jobs.forEach { it.cancel() }
    }

    private fun readLoop(input: InputStream) {
        try {
            while (!_closed.value) {
                val line = readLine(input) ?: break
                lastReceivedAt = System.nanoTime()
                if (line.isBlank()) continue
                val message = runCatching { SignalingMessage.decode(line) }.getOrNull() ?: continue
                if (message.type == SignalingMessage.PING) continue
                // Messages venus du réseau : l'émetteur est toujours cette connexion.
                inbox.trySend(message.copy(from = id))
            }
        } catch (e: IOException) {
            // Socket fermée ou trame trop longue : on ferme proprement ci-dessous.
        } finally {
            close()
        }
    }

    private suspend fun heartbeatLoop() {
        while (kotlinx.coroutines.currentCoroutineContext().isActive && !_closed.value) {
            delay(heartbeat)
            val silentFor = (System.nanoTime() - lastReceivedAt) / 1_000_000
            if (silentFor >= timeout.inWholeMilliseconds) {
                close()
                return
            }
            send(SignalingMessage(type = SignalingMessage.PING))
        }
    }

    /** Lit une ligne en refusant toute trame de plus de [MAX_FRAME_BYTES]. `null` en fin de flux. */
    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buffer.size() == 0) null else buffer.toString(Charsets.UTF_8.name())
            if (b == '\n'.code) return buffer.toString(Charsets.UTF_8.name())
            buffer.write(b)
            if (buffer.size() > MAX_FRAME_BYTES) throw IOException("Trame trop longue")
        }
    }

    companion object {
        const val MAX_FRAME_BYTES = 64 * 1024
    }
}

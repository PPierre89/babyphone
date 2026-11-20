package fr.penpenic.babyphone.core.network

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** Événements d'une connexion de signalisation. */
sealed interface SignalingEvent {
    data class Connected(val socket: SignalingSocket) : SignalingEvent
    data class Message(val message: SignalingMessage) : SignalingEvent
    data class Closed(val code: Int, val reason: String) : SignalingEvent
    data class Failed(val error: Throwable) : SignalingEvent
}

/** Envoi de messages sur une connexion ouverte. */
fun interface SignalingSocket {
    fun send(message: SignalingMessage): Boolean
}

/**
 * Client WebSocket vers le serveur de signalisation du NAS.
 *
 * N'accepte que `wss://` : aucun échange en clair, même sur le réseau local.
 * L'authentification de l'appareil (défi signé) et l'épinglage du certificat
 * arrivent en phase 2 ; [deviceId] et [familyId] sont pour l'instant déclaratifs.
 */
class SignalingClient(
    private val serverUrl: String,
    private val client: OkHttpClient = defaultClient(),
) {
    init {
        require(serverUrl.startsWith("wss://")) { "Seules les connexions wss:// sont autorisées" }
    }

    fun connect(familyId: String, deviceId: String): Flow<SignalingEvent> = callbackFlow {
        val url = serverUrl.replaceFirst("wss://", "https://").toHttpUrl().newBuilder()
            .addPathSegment("ws")
            .addQueryParameter("family", familyId)
            .addQueryParameter("device", deviceId)
            .build()
        val request = Request.Builder().url(url).build()

        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                trySend(SignalingEvent.Connected { webSocket.send(it.encode()) })
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { SignalingMessage.decode(text) }
                    .onSuccess { trySend(SignalingEvent.Message(it)) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                trySend(SignalingEvent.Closed(code, reason))
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySend(SignalingEvent.Failed(t))
                close()
            }
        })

        awaitClose { socket.close(NORMAL_CLOSURE, null) }
    }

    companion object {
        private const val NORMAL_CLOSURE = 1000

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

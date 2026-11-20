package fr.penpenic.babyphone.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Enveloppe échangée avec le serveur de signalisation (voir server/signaling/PROTOCOL.md).
 *
 * Le serveur ne lit que [type] et [to] pour router, et renseigne lui-même [from].
 * Le [payload] est opaque pour lui : à partir de la phase 2 il sera signé, puis chiffré,
 * par l'appareil émetteur.
 */
@Serializable
data class SignalingMessage(
    val type: String,
    val to: String? = null,
    val from: String? = null,
    val payload: JsonElement? = null,
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        // Messages émis par les appareils
        const val OFFER = "offer"
        const val ANSWER = "answer"
        const val ICE = "ice"
        const val ALERT = "alert"
        const val STATUS = "status"

        // Messages émis par le serveur
        const val PEERS = "peers"
        const val PEER_JOINED = "peer_joined"
        const val PEER_LEFT = "peer_left"
        const val ERROR = "error"

        internal val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun decode(text: String): SignalingMessage = json.decodeFromString(serializer(), text)
    }
}

/** Contenu du message [SignalingMessage.PEERS] envoyé par le serveur à la connexion. */
@Serializable
data class PeersPayload(@SerialName("peers") val peers: List<String>)

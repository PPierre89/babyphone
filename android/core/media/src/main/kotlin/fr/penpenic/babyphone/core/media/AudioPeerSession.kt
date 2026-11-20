package fr.penpenic.babyphone.core.media

import fr.penpenic.babyphone.core.network.SignalingMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** État d'une session d'écoute, tel qu'affiché aux parents. */
enum class SessionState { CONNECTING, CONNECTED, INTERRUPTED, FAILED, CLOSED }

/**
 * Une session audio entre le téléphone bébé (émetteur) et un téléphone parent (récepteur).
 *
 * Négociation : le parent envoie une offre en réception seule ; le bébé y attache son micro
 * et répond. Les candidats ICE arrivés avant la description distante sont mis en attente.
 *
 * @param send envoie un message de signalisation au pair.
 */
class AudioPeerSession(
    engine: WebRtcAudioEngine,
    private val send: (SignalingMessage) -> Boolean,
) {
    private val _state = MutableStateFlow(SessionState.CONNECTING)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false

    private val peer: PeerConnection = engine.createPeerConnection(object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            send(SignalingMessage(type = SignalingMessage.ICE, payload = candidate.toJson()))
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            if (_state.value == SessionState.CLOSED) return
            _state.value = when (newState) {
                PeerConnection.PeerConnectionState.NEW,
                PeerConnection.PeerConnectionState.CONNECTING -> SessionState.CONNECTING
                PeerConnection.PeerConnectionState.CONNECTED -> SessionState.CONNECTED
                PeerConnection.PeerConnectionState.DISCONNECTED -> SessionState.INTERRUPTED
                PeerConnection.PeerConnectionState.FAILED -> SessionState.FAILED
                PeerConnection.PeerConnectionState.CLOSED -> SessionState.CLOSED
            }
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) = Unit
    })

    /** Côté parent : propose une session en réception audio seule. */
    suspend fun startAsListener() {
        peer.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
        )
        val offer = peer.awaitCreate(offer = true)
        peer.awaitSetLocal(offer)
        send(SignalingMessage(type = SignalingMessage.OFFER, payload = offer.toJson()))
    }

    /** Côté bébé : répond à l'offre d'un parent en lui envoyant le micro. */
    suspend fun answerWithMicrophone(offerPayload: JsonObject, microphone: AudioTrack) {
        peer.awaitSetRemote(sessionDescription(SessionDescription.Type.OFFER, offerPayload))
        onRemoteDescriptionSet()
        peer.addTrack(microphone, listOf(STREAM_ID))
        val answer = peer.awaitCreate(offer = false)
        peer.awaitSetLocal(answer)
        send(SignalingMessage(type = SignalingMessage.ANSWER, payload = answer.toJson()))
    }

    /** Côté parent : applique la réponse du bébé. */
    suspend fun applyAnswer(answerPayload: JsonObject) {
        peer.awaitSetRemote(sessionDescription(SessionDescription.Type.ANSWER, answerPayload))
        onRemoteDescriptionSet()
    }

    fun addRemoteCandidate(payload: JsonObject) {
        val candidate = IceCandidate(
            payload.getValue("sdpMid").jsonPrimitive.content,
            payload.getValue("sdpMLineIndex").jsonPrimitive.int,
            payload.getValue("candidate").jsonPrimitive.content,
        )
        synchronized(pendingCandidates) {
            if (!remoteDescriptionSet) {
                pendingCandidates += candidate
                return
            }
        }
        peer.addIceCandidate(candidate)
    }

    /** Statistiques brutes pour l'estimation de latence (voir [LatencyEstimator]). */
    suspend fun sample(): StatsSample = suspendCancellableCoroutine { cont ->
        peer.getStats { report -> cont.resume(StatsSample.from(report)) }
    }

    fun close() {
        if (_state.value == SessionState.CLOSED) return
        _state.value = SessionState.CLOSED
        peer.dispose()
    }

    private fun onRemoteDescriptionSet() {
        val queued = synchronized(pendingCandidates) {
            remoteDescriptionSet = true
            pendingCandidates.toList().also { pendingCandidates.clear() }
        }
        queued.forEach { peer.addIceCandidate(it) }
    }

    private companion object {
        const val STREAM_ID = "babyphone"

        fun SessionDescription.toJson() = buildJsonObject { put("sdp", JsonPrimitive(description)) }

        fun sessionDescription(type: SessionDescription.Type, payload: JsonObject) =
            SessionDescription(type, payload.getValue("sdp").jsonPrimitive.content)

        fun IceCandidate.toJson() = buildJsonObject {
            put("sdpMid", JsonPrimitive(sdpMid))
            put("sdpMLineIndex", JsonPrimitive(sdpMLineIndex))
            put("candidate", JsonPrimitive(sdp))
        }

        suspend fun PeerConnection.awaitCreate(offer: Boolean): SessionDescription =
            suspendCancellableCoroutine { cont ->
                val observer = object : SdpObserver {
                    override fun onCreateSuccess(description: SessionDescription) = cont.resume(description)
                    override fun onCreateFailure(error: String?) =
                        cont.resumeWithException(IllegalStateException("Création SDP impossible : $error"))
                    override fun onSetSuccess() = Unit
                    override fun onSetFailure(error: String?) = Unit
                }
                if (offer) createOffer(observer, MediaConstraints()) else createAnswer(observer, MediaConstraints())
            }

        suspend fun PeerConnection.awaitSetLocal(description: SessionDescription) =
            awaitSet { observer -> setLocalDescription(observer, description) }

        suspend fun PeerConnection.awaitSetRemote(description: SessionDescription) =
            awaitSet { observer -> setRemoteDescription(observer, description) }

        suspend fun awaitSet(block: (SdpObserver) -> Unit): Unit = suspendCancellableCoroutine { cont ->
            block(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) = Unit
                override fun onCreateFailure(error: String?) = Unit
                override fun onSetSuccess() = cont.resume(Unit)
                override fun onSetFailure(error: String?) =
                    cont.resumeWithException(IllegalStateException("Description SDP refusée : $error"))
            })
        }
    }
}

/** Extrait de RTCStats utile pour la latence et la qualité. */
data class StatsSample(
    val roundTripSeconds: Double?,
    val jitterBufferDelaySeconds: Double?,
    val jitterBufferEmittedCount: Double?,
    val packetsReceived: Double?,
    val packetsLost: Double?,
) {
    companion object {
        fun from(report: org.webrtc.RTCStatsReport): StatsSample {
            var rtt: Double? = null
            var jbDelay: Double? = null
            var jbCount: Double? = null
            var received: Double? = null
            var lost: Double? = null
            for (stats in report.statsMap.values) {
                val m = stats.members
                when (stats.type) {
                    "candidate-pair" -> if (m["nominated"] == true && m["state"] == "succeeded") {
                        rtt = (m["currentRoundTripTime"] as? Number)?.toDouble() ?: rtt
                    }
                    "inbound-rtp" -> if (m["kind"] == "audio") {
                        jbDelay = (m["jitterBufferDelay"] as? Number)?.toDouble()
                        jbCount = (m["jitterBufferEmittedCount"] as? Number)?.toDouble()
                        received = (m["packetsReceived"] as? Number)?.toDouble()
                        lost = (m["packetsLost"] as? Number)?.toDouble()
                    }
                }
            }
            return StatsSample(rtt, jbDelay, jbCount, received, lost)
        }
    }
}

internal fun JsonObject?.orEmptyObject(): JsonObject = this ?: JsonObject(emptyMap())

/** Accès pratique au payload JSON d'un message. */
fun SignalingMessage.payloadObject(): JsonObject = payload?.jsonObject.orEmptyObject()

package fr.penpenic.babyphone.core.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaRecorder
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Moteur audio WebRTC : une seule instance par service (bébé ou parent).
 *
 * - Aucun serveur STUN/TURN en phase 1 : seules les adresses locales sont proposées,
 *   rien ne sort du Wi-Fi et aucun service tiers (Google STUN par exemple) n'est contacté.
 * - Côté bébé, l'annulation d'écho et la réduction de bruit sont désactivées : elles
 *   atténuent précisément les sons qu'on veut entendre (pleurs, respiration).
 *
 * @param onMicSamples reçoit le son capté par WebRTC (PCM 16 bits mono) pendant une écoute,
 *   pour que la détection de bruit continue sans ouvrir le micro une seconde fois.
 */
class WebRtcAudioEngine(
    context: Context,
    private val onMicSamples: ((ShortArray, Int) -> Unit)? = null,
) {
    private val appContext = context.applicationContext
    private val audioModule: JavaAudioDeviceModule
    private val factory: PeerConnectionFactory

    init {
        ensureInitialized(appContext)
        audioModule = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false)
            .setUseStereoInput(false)
            .setUseStereoOutput(false)
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setSamplesReadyCallback { samples ->
                val callback = onMicSamples ?: return@setSamplesReadyCallback
                if (samples.channelCount != 1) return@setSamplesReadyCallback
                val pcm = ShortArray(samples.data.size / 2)
                ByteBuffer.wrap(samples.data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
                callback(pcm, samples.sampleRate)
            }
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioModule)
            .createPeerConnectionFactory()
    }

    /** Piste micro du téléphone bébé. */
    fun createMicrophoneTrack(): AudioTrack {
        val constraints = MediaConstraints().apply {
            mandatory += MediaConstraints.KeyValuePair("googEchoCancellation", "false")
            mandatory += MediaConstraints.KeyValuePair("googNoiseSuppression", "false")
            mandatory += MediaConstraints.KeyValuePair("googAutoGainControl", "true")
            mandatory += MediaConstraints.KeyValuePair("googHighpassFilter", "true")
        }
        val source = factory.createAudioSource(constraints)
        return factory.createAudioTrack("baby-mic", source)
    }

    internal fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection {
        val config = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
        return requireNotNull(factory.createPeerConnection(config, observer)) {
            "Impossible de créer la connexion WebRTC"
        }
    }

    fun dispose() {
        factory.dispose()
        audioModule.release()
    }

    private companion object {
        @Volatile
        private var initialized = false

        @Synchronized
        fun ensureInitialized(context: Context) {
            if (initialized) return
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions(),
            )
            initialized = true
        }
    }
}

package fr.penpenic.babyphone.feature.baby

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import fr.penpenic.babyphone.core.detection.LoudnessDetector
import fr.penpenic.babyphone.core.media.AudioPeerSession
import fr.penpenic.babyphone.core.media.SessionState
import fr.penpenic.babyphone.core.media.WebRtcAudioEngine
import fr.penpenic.babyphone.core.media.payloadObject
import fr.penpenic.babyphone.core.network.SignalingMessage
import fr.penpenic.babyphone.core.network.lan.LanConnection
import fr.penpenic.babyphone.core.network.lan.LanDiscovery
import fr.penpenic.babyphone.core.network.lan.LanSignalingServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.webrtc.AudioTrack
import java.net.Inet4Address

/**
 * Service au premier plan du téléphone bébé.
 *
 * - Sans parent à l'écoute : capte le micro localement et détecte le bruit. Rien ne sort du téléphone.
 * - Un parent se connecte (Wi-Fi local) : le micro passe par WebRTC, chiffré de bout en bout,
 *   et la détection continue sur les mêmes échantillons. Plusieurs parents peuvent écouter.
 * Le son n'est jamais enregistré sur le stockage.
 */
class BabyMonitorService : LifecycleService() {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val serial = Dispatchers.Default.limitedParallelism(1)

    private val detector = LoudnessDetector(sampleRateHz = LOCAL_SAMPLE_RATE)
    private var engine: WebRtcAudioEngine? = null
    private var microphone: AudioTrack? = null
    private var server: LanSignalingServer? = null
    private var announcement: LanDiscovery.Registration? = null
    private var localCaptureJob: Job? = null
    private var statusJob: Job? = null
    private val connections = mutableMapOf<String, LanConnection>()
    private val sessions = mutableMapOf<String, AudioPeerSession>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var started = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP || !hasMicPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (!started) {
            started = true
            acquireLocks()
            lifecycleScope.launch(serial) { startMonitoring() }
        }
        BabyMonitorState.setRunning(true)
        return START_STICKY
    }

    override fun onDestroy() {
        announcement?.unregister()
        server?.stop()
        statusJob?.cancel()
        localCaptureJob?.cancel()
        sessions.values.forEach { it.close() }
        connections.values.forEach { it.close() }
        microphone?.dispose()
        engine?.dispose()
        releaseLocks()
        BabyMonitorState.setRunning(false)
        super.onDestroy()
    }

    private fun startMonitoring() {
        engine = WebRtcAudioEngine(this) { pcm, rate -> analyse(pcm, rate) }
        startLocalCapture()

        val lan = LanSignalingServer(lifecycleScope, onConnection = { connection ->
            lifecycleScope.launch(serial) { handleParent(connection) }
        })
        val port = lan.start()
        server = lan
        announcement = LanDiscovery(this).announce("Bébé · ${Build.MODEL}", port)
        BabyMonitorState.setAddress(localIpv4()?.let { "$it:$port" })

        statusJob = lifecycleScope.launch(serial) {
            while (currentCoroutineContext().isActive) {
                broadcast(statusMessage())
                delay(STATUS_INTERVAL_MS)
            }
        }
    }

    /** Une connexion parent : négociation WebRTC, puis nettoyage à la déconnexion. */
    private suspend fun handleParent(connection: LanConnection) {
        connections[connection.id] = connection
        connection.send(statusMessage())
        try {
            connection.incoming.collect { message ->
                when (message.type) {
                    SignalingMessage.OFFER -> startSession(connection, message)
                    SignalingMessage.ICE -> sessions[connection.id]?.addRemoteCandidate(message.payloadObject())
                    SignalingMessage.HANGUP -> connection.close()
                }
            }
        } finally {
            withContext(serial) { endSession(connection.id) }
        }
    }

    private suspend fun startSession(connection: LanConnection, offer: SignalingMessage) {
        sessions.remove(connection.id)?.close()
        stopLocalCapture() // un seul usage du micro à la fois : WebRTC prend le relais
        val session = AudioPeerSession(requireNotNull(engine), connection::send)
        sessions[connection.id] = session
        BabyMonitorState.setListeners(sessions.size)
        try {
            session.answerWithMicrophone(offer.payloadObject(), microphoneTrack())
        } catch (e: IllegalStateException) {
            connection.close()
            return
        }
        lifecycleScope.launch(serial) {
            session.state.first { it == SessionState.FAILED || it == SessionState.CLOSED }
            connection.close()
        }
    }

    private fun endSession(id: String) {
        connections.remove(id)
        sessions.remove(id)?.close()
        BabyMonitorState.setListeners(sessions.size)
        if (sessions.isEmpty() && started) startLocalCapture()
    }

    private fun microphoneTrack(): AudioTrack =
        microphone ?: requireNotNull(engine).createMicrophoneTrack().also { microphone = it }

    private fun broadcast(message: SignalingMessage) {
        connections.values.forEach { it.send(message) }
    }

    private fun analyse(pcm: ShortArray, sampleRate: Int) {
        BabyMonitorState.setLevel(LoudnessDetector.levelDbfs(pcm))
        val trigger = detector.process(pcm, sampleRate) ?: return
        val now = System.currentTimeMillis()
        BabyMonitorState.triggered(now)
        lifecycleScope.launch(serial) {
            broadcast(
                SignalingMessage(
                    type = SignalingMessage.ALERT,
                    payload = buildJsonObject {
                        put("kind", JsonPrimitive("noise"))
                        put("levelDbfs", JsonPrimitive(trigger.levelDbfs))
                        put("at", JsonPrimitive(now))
                    },
                ),
            )
        }
    }

    // --- Capture locale (aucun parent à l'écoute) ---

    private fun startLocalCapture() {
        if (localCaptureJob?.isActive == true) return
        localCaptureJob = lifecycleScope.launch(Dispatchers.IO) { captureLocally() }
    }

    private suspend fun stopLocalCapture() {
        localCaptureJob?.cancelAndJoin()
        localCaptureJob = null
    }

    private suspend fun captureLocally() {
        if (!hasMicPermission()) return
        val minBuffer = AudioRecord.getMinBufferSize(LOCAL_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val frame = ShortArray(LOCAL_SAMPLE_RATE / 10) // trames de 100 ms
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, // pas de contrôle de gain automatique
                LOCAL_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, frame.size * 2 * 2),
            )
        } catch (e: SecurityException) {
            return
        }
        try {
            record.startRecording()
            while (currentCoroutineContext().isActive) {
                val read = record.read(frame, 0, frame.size)
                if (read <= 0) continue
                analyse(if (read == frame.size) frame else frame.copyOf(read), LOCAL_SAMPLE_RATE)
            }
        } finally {
            record.stop()
            record.release()
        }
    }

    // --- Système ---

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun statusMessage(): SignalingMessage {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        return SignalingMessage(
            type = SignalingMessage.STATUS,
            payload = buildJsonObject {
                put("battery", JsonPrimitive(level))
                put("charging", JsonPrimitive(plugged))
                put("listeners", JsonPrimitive(sessions.size))
            },
        )
    }

    private fun localIpv4(): String? {
        val cm = getSystemService(ConnectivityManager::class.java)
        val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return props.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }

    @SuppressLint("WakelockTimeout") // tenu tant que la surveillance est active, libéré dans onDestroy
    private fun acquireLocks() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "babyphone:monitor")
            .apply { acquire() }
        wifiLock = getSystemService(WifiManager::class.java).createWifiLock(wifiLockMode(), "babyphone:monitor").apply { acquire() }
    }

    private fun releaseLocks() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.baby_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.baby_notification_title))
            .setContentText(getString(R.string.baby_notification_text))
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        private const val CHANNEL_ID = "baby_monitor"
        private const val NOTIFICATION_ID = 1
        private const val LOCAL_SAMPLE_RATE = 16_000
        private const val STATUS_INTERVAL_MS = 30_000L
        private const val ACTION_STOP = "fr.penpenic.babyphone.action.STOP_MONITOR"

        /** À appeler depuis l'écran au premier plan (exigence Android 14 pour un service micro). */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, BabyMonitorService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, BabyMonitorService::class.java).setAction(ACTION_STOP))
        }
    }
}

/** Wi-Fi à faible latence (Android 10+), sinon haute performance : évite la mise en veille de la radio. */
@Suppress("DEPRECATION")
internal fun wifiLockMode(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF

package fr.penpenic.babyphone.feature.parent

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import fr.penpenic.babyphone.core.media.AudioPeerSession
import fr.penpenic.babyphone.core.media.LatencyEstimator
import fr.penpenic.babyphone.core.media.SessionState
import fr.penpenic.babyphone.core.media.StatsSample
import fr.penpenic.babyphone.core.media.WebRtcAudioEngine
import fr.penpenic.babyphone.core.media.payloadObject
import fr.penpenic.babyphone.core.network.SignalingMessage
import fr.penpenic.babyphone.core.network.lan.LanConnection
import fr.penpenic.babyphone.core.network.lan.LanSignalingClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException

/**
 * Service au premier plan du téléphone parent : écoute le téléphone bébé, écran éteint compris.
 *
 * Se reconnecte tout seul après une coupure (délai croissant jusqu'à 10 s). Si le bébé reste
 * injoignable plus de 30 s, une notification prioritaire prévient les parents.
 */
class ParentListenService : LifecycleService() {

    private var engine: WebRtcAudioEngine? = null
    private var loop: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val host = intent?.getStringExtra(EXTRA_HOST)
        val port = intent?.getIntExtra(EXTRA_PORT, -1) ?: -1
        val name = intent?.getStringExtra(EXTRA_NAME) ?: host
        if (host == null || port !in 1..65535) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        acquireLocks()
        loop?.cancel()
        ParentListenState.update {
            ListenSnapshot(status = ListenStatus.CONNECTING, babyName = name, startedAtMillis = System.currentTimeMillis())
        }
        loop = lifecycleScope.launch(Dispatchers.Default) { listenForever(host, port) }
        // Si le système tue le service, il le relance avec la même adresse : l'écoute ne doit
        // jamais s'arrêter sans que le parent l'ait demandé.
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        loop?.cancel()
        engine?.dispose()
        releaseLocks()
        getSystemService(NotificationManager::class.java).cancel(LOST_NOTIFICATION_ID)
        ParentListenState.reset()
        super.onDestroy()
    }

    private suspend fun listenForever(host: String, port: Int) {
        val audio = engine ?: WebRtcAudioEngine(this).also { engine = it }
        var attempt = 0
        var unreachableSince: Long? = null
        while (currentCoroutineContext().isActive) {
            val connected = try {
                val connection = withContext(Dispatchers.IO) { LanSignalingClient.connect(host, port, lifecycleScope) }
                runSession(audio, connection)
            } catch (e: IOException) {
                false
            }
            if (connected) {
                attempt = 0
                unreachableSince = null
                ParentListenState.update { it.copy(interruptions = it.interruptions + 1) }
            }
            val now = System.currentTimeMillis()
            val since = unreachableSince ?: now.also { unreachableSince = it }
            val unreachable = now - since >= UNREACHABLE_AFTER_MS
            ParentListenState.update {
                it.copy(status = if (unreachable) ListenStatus.UNREACHABLE else ListenStatus.RECONNECTING, latencyMs = null, lossPercent = null)
            }
            if (unreachable) notifyLost()
            delay(BACKOFF_MS[minOf(attempt++, BACKOFF_MS.lastIndex)])
        }
    }

    /**
     * Une connexion : négociation, statistiques, jusqu'à la coupure.
     * @return `true` si l'audio a été établi au moins une fois (pour compter les interruptions).
     */
    private suspend fun runSession(audio: WebRtcAudioEngine, connection: LanConnection): Boolean = coroutineScope {
        val session = AudioPeerSession(audio, connection::send)
        var established = false
        val signaling = launch {
            connection.incoming.collect { message ->
                when (message.type) {
                    SignalingMessage.ANSWER -> session.applyAnswer(message.payloadObject())
                    SignalingMessage.ICE -> session.addRemoteCandidate(message.payloadObject())
                    SignalingMessage.STATUS -> onStatus(message)
                    SignalingMessage.ALERT -> onAlert(message)
                }
            }
        }
        val watcher = launch {
            session.state.collect { state ->
                if (state == SessionState.CONNECTED) {
                    established = true
                    getSystemService(NotificationManager::class.java).cancel(LOST_NOTIFICATION_ID)
                    ParentListenState.update { it.copy(status = ListenStatus.LISTENING) }
                }
            }
        }
        val stats = launch {
            var previous: StatsSample? = null
            while (isActive) {
                delay(STATS_INTERVAL_MS)
                if (session.state.value != SessionState.CONNECTED) continue
                val current = session.sample()
                val quality = LatencyEstimator.estimate(previous, current)
                previous = current
                ParentListenState.update { it.copy(latencyMs = quality.latencyMs, lossPercent = quality.lossPercent) }
            }
        }
        try {
            session.startAsListener()
            // Fin de session : connexion de signalisation fermée, ou WebRTC en échec.
            combine(connection.closed, session.state) { closed, state ->
                closed || state == SessionState.FAILED || state == SessionState.CLOSED
            }.first { it }
        } catch (e: IllegalStateException) {
            // Négociation refusée : on retente comme pour une coupure.
        } finally {
            connection.send(SignalingMessage(type = SignalingMessage.HANGUP))
            stats.cancel()
            watcher.cancel()
            signaling.cancel()
            session.close()
            connection.close()
        }
        established
    }

    private fun onStatus(message: SignalingMessage) {
        val p = message.payloadObject()
        ParentListenState.update {
            it.copy(
                babyBattery = p["battery"]?.jsonPrimitive?.intOrNull,
                babyCharging = p["charging"]?.jsonPrimitive?.booleanOrNull,
            )
        }
    }

    private fun onAlert(message: SignalingMessage) {
        val at = message.payloadObject()["at"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
        ParentListenState.update { it.copy(lastNoiseAtMillis = at) }
    }

    private fun notifyLost() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, getString(R.string.parent_alert_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(getString(R.string.parent_lost_title))
            .setContentText(getString(R.string.parent_lost_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppIntent())
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(LOST_NOTIFICATION_ID, notification)
    }

    private fun openAppIntent(): PendingIntent? = packageManager.getLaunchIntentForPackage(packageName)?.let {
        PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    @SuppressLint("WakelockTimeout") // tenu tant que l'écoute est active, libéré dans onDestroy
    private fun acquireLocks() {
        if (wakeLock?.isHeld != true) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "babyphone:listen")
                .apply { acquire() }
        }
        if (wifiLock?.isHeld != true) {
            wifiLock = getSystemService(WifiManager::class.java).createWifiLock(wifiLockMode(), "babyphone:listen").apply { acquire() }
        }
    }

    private fun releaseLocks() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.parent_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(getString(R.string.parent_notification_title))
            .setContentText(getString(R.string.parent_notification_text))
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        private const val CHANNEL_ID = "parent_listen"
        private const val ALERT_CHANNEL_ID = "parent_alerts"
        private const val NOTIFICATION_ID = 2
        private const val LOST_NOTIFICATION_ID = 3
        private const val STATS_INTERVAL_MS = 2_000L
        private const val UNREACHABLE_AFTER_MS = 30_000L
        private val BACKOFF_MS = longArrayOf(500, 1_000, 2_000, 5_000, 10_000)
        private const val EXTRA_HOST = "host"
        private const val EXTRA_PORT = "port"
        private const val EXTRA_NAME = "name"
        private const val ACTION_STOP = "fr.penpenic.babyphone.action.STOP_LISTEN"

        fun start(context: Context, host: String, port: Int, name: String) {
            val intent = Intent(context, ParentListenService::class.java)
                .putExtra(EXTRA_HOST, host)
                .putExtra(EXTRA_PORT, port)
                .putExtra(EXTRA_NAME, name)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ParentListenService::class.java).setAction(ACTION_STOP))
        }
    }
}

@Suppress("DEPRECATION")
private fun wifiLockMode(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF

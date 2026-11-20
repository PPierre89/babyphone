package fr.penpenic.babyphone.feature.baby

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import fr.penpenic.babyphone.core.detection.LoudnessDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Service au premier plan du téléphone bébé.
 *
 * Phase 0 : capte le micro et calcule le niveau sonore en local, sans rien envoyer.
 * Phase 1 : le flux audio WebRTC partira d'ici vers le téléphone parent.
 * Le son n'est jamais enregistré sur le stockage.
 */
class BabyMonitorService : LifecycleService() {

    private var captureJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!hasMicPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (captureJob == null) captureJob = lifecycleScope.launch(Dispatchers.Default) { capture() }
        BabyMonitorState.setRunning(true)
        return START_STICKY
    }

    override fun onDestroy() {
        captureJob?.cancel()
        BabyMonitorState.setRunning(false)
        super.onDestroy()
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

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

    private suspend fun capture() {
        if (!hasMicPermission()) return
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val frame = ShortArray(SAMPLE_RATE / 10) // trames de 100 ms
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, // pas de contrôle de gain automatique
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, frame.size * 2 * 2),
            )
        } catch (e: SecurityException) {
            return
        }
        val detector = LoudnessDetector(sampleRateHz = SAMPLE_RATE)
        try {
            record.startRecording()
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val read = record.read(frame, 0, frame.size)
                if (read <= 0) continue
                val chunk = if (read == frame.size) frame else frame.copyOf(read)
                BabyMonitorState.setLevel(LoudnessDetector.levelDbfs(chunk))
                detector.process(chunk)?.let { BabyMonitorState.triggered(System.currentTimeMillis()) }
            }
        } finally {
            record.stop()
            record.release()
        }
    }

    companion object {
        private const val CHANNEL_ID = "baby_monitor"
        private const val NOTIFICATION_ID = 1
        private const val SAMPLE_RATE = 16_000
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

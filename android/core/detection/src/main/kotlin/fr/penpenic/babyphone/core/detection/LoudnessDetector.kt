package fr.penpenic.babyphone.core.detection

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Premier étage de détection : déclenche quand le niveau sonore dépasse un seuil
 * pendant une durée minimale. Le second étage (classification des pleurs par YAMNet)
 * ne sera sollicité qu'après ce déclenchement, pour économiser la batterie.
 *
 * Pas de dépendance Android : testable sur la JVM.
 *
 * @param thresholdDbfs seuil en dBFS (0 = niveau maximal, valeurs négatives en dessous).
 * @param minLoudMs durée cumulée de bruit au-dessus du seuil avant de déclencher.
 * @param cooldownMs délai minimal entre deux déclenchements, pour éviter les rafales d'alertes.
 */
class LoudnessDetector(
    private val sampleRateHz: Int = 16_000,
    var thresholdDbfs: Double = -35.0,
    private val minLoudMs: Long = 1_500,
    private val cooldownMs: Long = 30_000,
) {
    private var loudMs = 0L
    private var msSinceTrigger = Long.MAX_VALUE / 2

    /**
     * Analyse une trame audio PCM 16 bits mono.
     * @param frameSampleRateHz fréquence de la trame : le micro peut changer de source en cours de route
     *   (capture locale à 16 kHz, puis capture WebRTC à 48 kHz pendant une écoute).
     * @return un [Trigger] si le seuil a été dépassé assez longtemps, sinon `null`.
     */
    @Synchronized
    fun process(frame: ShortArray, frameSampleRateHz: Int = sampleRateHz): Trigger? {
        if (frame.isEmpty()) return null
        val frameMs = frame.size * 1_000L / frameSampleRateHz
        val level = levelDbfs(frame)
        msSinceTrigger += frameMs

        loudMs = if (level >= thresholdDbfs) loudMs + frameMs else 0L

        if (loudMs >= minLoudMs && msSinceTrigger >= cooldownMs) {
            loudMs = 0L
            msSinceTrigger = 0L
            return Trigger(levelDbfs = level)
        }
        return null
    }

    @Synchronized
    fun reset() {
        loudMs = 0L
        msSinceTrigger = Long.MAX_VALUE / 2
    }

    data class Trigger(val levelDbfs: Double)

    companion object {
        /** Niveau RMS d'une trame en dBFS ; -120 pour le silence numérique. */
        fun levelDbfs(frame: ShortArray): Double {
            if (frame.isEmpty()) return SILENCE_DBFS
            var sum = 0.0
            for (s in frame) {
                val v = s / 32_768.0
                sum += v * v
            }
            val rms = sqrt(sum / frame.size)
            return if (rms <= 0.0) SILENCE_DBFS else maxOf(SILENCE_DBFS, 20 * log10(rms))
        }

        private const val SILENCE_DBFS = -120.0
    }
}

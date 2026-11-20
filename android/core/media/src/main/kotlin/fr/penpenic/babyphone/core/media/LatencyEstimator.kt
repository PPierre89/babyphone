package fr.penpenic.babyphone.core.media

import kotlin.math.roundToInt

/** Qualité de l'écoute, affichée sur le téléphone parent. */
data class LinkQuality(
    /** Latence estimée de bout en bout, en millisecondes ; null tant que les mesures manquent. */
    val latencyMs: Int?,
    /** Paquets perdus sur l'intervalle, en pourcentage. */
    val lossPercent: Double?,
)

/**
 * Estime la latence à partir de deux mesures successives :
 * moitié de l'aller-retour réseau + attente moyenne dans la mémoire tampon de gigue sur l'intervalle
 * + une part fixe pour la capture, l'encodage Opus (trame de 20 ms) et la restitution.
 *
 * C'est une estimation : la mesure de référence pour le jalon G1 reste le test au clap décrit
 * dans docs/phase-1-test.md.
 */
object LatencyEstimator {

    /** Capture (~10 ms), trame Opus (20 ms), tampons de lecture Android (~20 ms). */
    const val FIXED_PIPELINE_MS = 50

    fun estimate(previous: StatsSample?, current: StatsSample): LinkQuality {
        val networkMs = current.roundTripSeconds?.let { it * 1000 / 2 }

        val bufferMs = run {
            val delay = delta(previous?.jitterBufferDelaySeconds, current.jitterBufferDelaySeconds)
            val count = delta(previous?.jitterBufferEmittedCount, current.jitterBufferEmittedCount)
            if (delay == null || count == null || count <= 0) null else delay / count * 1000
        }

        val latency = if (networkMs == null || bufferMs == null) null else (networkMs + bufferMs + FIXED_PIPELINE_MS).roundToInt()

        val loss = run {
            val lost = delta(previous?.packetsLost, current.packetsLost)
            val received = delta(previous?.packetsReceived, current.packetsReceived)
            if (lost == null || received == null || lost + received <= 0) null else (lost.coerceAtLeast(0.0) / (lost + received) * 100)
        }

        return LinkQuality(latency, loss)
    }

    /** Sans mesure précédente, on prend la valeur cumulée depuis le début de la session. */
    private fun delta(previous: Double?, current: Double?): Double? = when {
        current == null -> null
        previous == null || current < previous -> current
        else -> current - previous
    }
}

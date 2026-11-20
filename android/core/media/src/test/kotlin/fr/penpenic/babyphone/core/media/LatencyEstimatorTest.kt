package fr.penpenic.babyphone.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LatencyEstimatorTest {

    private fun sample(rtt: Double? = 0.010, delay: Double? = 6.0, count: Double? = 100.0, received: Double? = 1000.0, lost: Double? = 0.0) =
        StatsSample(rtt, delay, count, received, lost)

    @Test
    fun `latence = demi aller-retour + tampon moyen sur l'intervalle + part fixe`() {
        val before = sample(delay = 6.0, count = 100.0)
        val after = sample(rtt = 0.020, delay = 14.0, count = 200.0) // 8 s de tampon pour 100 trames = 80 ms
        val quality = LatencyEstimator.estimate(before, after)
        assertEquals(10 + 80 + LatencyEstimator.FIXED_PIPELINE_MS, quality.latencyMs)
    }

    @Test
    fun `premiere mesure, moyenne depuis le debut de la session`() {
        val quality = LatencyEstimator.estimate(null, sample(rtt = 0.0, delay = 6.0, count = 100.0))
        assertEquals(60 + LatencyEstimator.FIXED_PIPELINE_MS, quality.latencyMs)
    }

    @Test
    fun `pas d'estimation sans mesure reseau`() {
        assertNull(LatencyEstimator.estimate(null, sample(rtt = null)).latencyMs)
        assertNull(LatencyEstimator.estimate(sample(), sample(count = 100.0)).latencyMs) // aucune trame sur l'intervalle
    }

    @Test
    fun `taux de pertes sur l'intervalle`() {
        val before = sample(received = 1000.0, lost = 10.0)
        val after = sample(received = 1095.0, lost = 15.0)
        assertEquals(5.0, LatencyEstimator.estimate(before, after).lossPercent!!, 0.001)
    }

    @Test
    fun `compteurs remis a zero apres reconnexion`() {
        val before = sample(received = 5000.0, lost = 50.0)
        val after = sample(received = 100.0, lost = 0.0)
        assertEquals(0.0, LatencyEstimator.estimate(before, after).lossPercent!!, 0.001)
    }
}

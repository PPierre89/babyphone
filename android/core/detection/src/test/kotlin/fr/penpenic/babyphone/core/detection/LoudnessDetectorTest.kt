package fr.penpenic.babyphone.core.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LoudnessDetectorTest {

    /** Trame de 100 ms à 16 kHz : sinusoïde d'amplitude donnée (0..1). */
    private fun frame(amplitude: Double) = ShortArray(1_600) { i ->
        (amplitude * 32_767 * sin(2 * PI * 440 * i / 16_000)).toInt().toShort()
    }

    @Test
    fun `silence numérique vaut -120 dBFS`() {
        assertEquals(-120.0, LoudnessDetector.levelDbfs(ShortArray(160)), 0.0)
    }

    @Test
    fun `sinusoïde pleine échelle proche de -3 dBFS`() {
        assertEquals(-3.0, LoudnessDetector.levelDbfs(frame(1.0)), 0.1)
    }

    @Test
    fun `un bruit bref ne déclenche pas`() {
        val detector = LoudnessDetector(minLoudMs = 1_500)
        repeat(10) { assertNull(detector.process(frame(0.5))) } // 1 s de bruit
        assertNull(detector.process(frame(0.0001)))              // retour au calme
    }

    @Test
    fun `un bruit soutenu déclenche une seule fois pendant le délai de garde`() {
        val detector = LoudnessDetector(minLoudMs = 1_500, cooldownMs = 30_000)
        val triggers = (1..100).mapNotNull { detector.process(frame(0.5)) } // 10 s de pleurs
        assertEquals(1, triggers.size)
    }

    @Test
    fun `redéclenche après le délai de garde`() {
        val detector = LoudnessDetector(minLoudMs = 1_000, cooldownMs = 5_000)
        val triggers = (1..120).mapNotNull { detector.process(frame(0.5)) } // 12 s
        assertEquals(3, triggers.size)
    }

    @Test
    fun `le seuil est réglable`() {
        val detector = LoudnessDetector(thresholdDbfs = -10.0, minLoudMs = 100)
        assertNull(detector.process(frame(0.05)))     // environ -29 dBFS
        assertNotNull(detector.process(frame(0.9)))   // environ -4 dBFS
    }
}

package fr.penpenic.babyphone.feature.parent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParentScreenLogicTest {

    @Test
    fun `adresse saisie a la main`() {
        val endpoint = parseAddress("192.168.1.20:41234")!!
        assertEquals("192.168.1.20", endpoint.host)
        assertEquals(41234, endpoint.port)
    }

    @Test
    fun `adresses invalides refusees`() {
        listOf("", "192.168.1.20", "192.168.1.300:80", "192.168.1.20:0", "192.168.1.20:70000", "bebe.local:80", "1.2.3.4:80x").forEach {
            assertNull(it, parseAddress(it))
        }
    }

    @Test
    fun `duree d'ecoute lisible pour le test de 8 h`() {
        assertEquals("0 h 00 min 05 s", formatDuration(5_000))
        assertEquals("8 h 03 min 07 s", formatDuration((8 * 3600 + 3 * 60 + 7) * 1000L))
    }
}

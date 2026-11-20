package fr.penpenic.babyphone.role

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoleTest {
    @Test
    fun `relit les rôles enregistrés`() {
        assertEquals(Role.BABY, Role.fromStored("BABY"))
        assertEquals(Role.PARENT, Role.fromStored("PARENT"))
    }

    @Test
    fun `valeur absente ou inconnue renvoie au choix du rôle`() {
        assertNull(Role.fromStored(null))
        assertNull(Role.fromStored("ADMIN"))
    }
}

package com.adoptu.adapters.authkit

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdoptuRbacTest {

    @Test
    fun `adoptuRoleByName resolves a valid role name`() {
        assertEquals(AdoptuRole.RESCUER, adoptuRoleByName("RESCUER"))
        assertEquals(AdoptuRole.ADMIN, adoptuRoleByName("ADMIN"))
    }

    @Test
    fun `adoptuRoleByName returns null for an unknown name`() {
        assertNull(adoptuRoleByName("NOT_A_REAL_ROLE"))
    }

    @Test
    fun `ADMIN grantsAll, every other role does not`() {
        assert(AdoptuRole.ADMIN.grantsAll())
        (AdoptuRole.entries - AdoptuRole.ADMIN).forEach { role ->
            assert(!role.grantsAll()) { "$role should not grantsAll" }
        }
    }

    @Test
    fun `ADOPTU_RESOURCE_COUNT matches the AdoptuResource catalog size`() {
        assertEquals(AdoptuResource.entries.size, ADOPTU_RESOURCE_COUNT)
    }
}

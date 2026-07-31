package com.adoptu.adapters.authkit

import com.adoptu.adapters.db.Users
import com.adoptu.mocks.TestDatabase
import com.universaliun.auth.backend.domain.model.user.AuthUser
import com.universaliun.auth.backend.domain.model.user.Email
import com.universaliun.auth.common.identity.AuthUserId
import com.universaliun.auth.common.rbac.PermissionSet
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdoptuUserRepositoryAdapterTest {

    private lateinit var adapter: AdoptuUserRepositoryAdapter

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        adapter = AdoptuUserRepositoryAdapter()
    }

    private fun newAuthUser(email: String, passwordHash: String? = "hash", enabled: Boolean = false, emailVerified: Boolean = false) =
        AuthUser(
            id = AuthUserId(java.util.UUID.randomUUID().toString()),
            email = Email(email),
            displayName = "Test User",
            passwordHash = passwordHash,
            roles = emptySet(),
            permissions = PermissionSet.empty(0),
            enabled = enabled,
            emailVerified = emailVerified,
            createdAt = Instant.now(),
            lastModifiedAt = Instant.now(),
        )

    @Test fun `save inserts a new user with a password`() {
        val saved = adapter.save(newAuthUser("insert@test.com"))

        assertNotNull(saved.id.value.toIntOrNull())
        val found = adapter.findByEmail(Email("insert@test.com"))
        assertEquals("Test User", found?.displayName)
        assertEquals("hash", found?.passwordHash)
    }

    @Test fun `save inserts a new passkey-only user with no password row`() {
        val saved = adapter.save(newAuthUser("passkey-only@test.com", passwordHash = null))

        val found = adapter.findById(saved.id)
        assertNull(found?.passwordHash)
    }

    @Test fun `save on an existing user updates displayName and emailVerified without touching passwordHash unless a new one is passed`() {
        val saved = adapter.save(newAuthUser("update@test.com"))

        val updated = adapter.save(saved.copy(displayName = "Renamed", emailVerified = true, passwordHash = null))

        val found = adapter.findById(saved.id)
        assertEquals("Renamed", found?.displayName)
        assertTrue(found!!.emailVerified)
        assertEquals("hash", found.passwordHash) // untouched -- update() call passed passwordHash=null
        assertEquals(updated.id, found.id)
    }

    @Test fun `findByEmail returns null for an unknown email`() {
        assertNull(adapter.findByEmail(Email("nobody@test.com")))
    }

    @Test fun `existsByEmail reflects real state`() {
        assertFalse(adapter.existsByEmail(Email("exists@test.com")))
        adapter.save(newAuthUser("exists@test.com"))
        assertTrue(adapter.existsByEmail(Email("exists@test.com")))
    }

    @Test fun `enabled is derived as emailVerified AND NOT banned AND NOT deactivated`() {
        val saved = adapter.save(newAuthUser("gate@test.com", emailVerified = false))
        assertFalse(adapter.findById(saved.id)!!.enabled)

        adapter.save(saved.copy(emailVerified = true, passwordHash = null))
        assertTrue(adapter.findById(saved.id)!!.enabled)

        transaction { Users.update({ Users.id eq saved.id.value.toInt() }) { it[isBanned] = true } }
        assertFalse(adapter.findById(saved.id)!!.enabled)
    }

    @Test fun `enabled is false once deactivated, even if email is verified and not banned`() {
        val saved = adapter.save(newAuthUser("deactivate-gate@test.com", emailVerified = true))
        adapter.save(saved.copy(emailVerified = true, passwordHash = null))

        adapter.deactivate(saved.id, AuthUserId("1"))

        assertFalse(adapter.findById(saved.id)!!.enabled)
    }

    @Test fun `reactivate clears the deactivation record`() {
        val saved = adapter.save(newAuthUser("reactivate@test.com", emailVerified = true))
        adapter.save(saved.copy(emailVerified = true, passwordHash = null))
        adapter.deactivate(saved.id, AuthUserId("1"))

        adapter.reactivate(saved.id)

        val found = adapter.findById(saved.id)!!
        assertTrue(found.enabled)
        assertNull(found.deactivatedAt)
        assertNull(found.deactivatedBy)
    }

    @Test fun `updateResetToken sets and clears the token`() {
        val saved = adapter.save(newAuthUser("reset@test.com"))
        val expiry = Instant.now().plusSeconds(900)

        adapter.updateResetToken(saved.id, "hashed-token", expiry)
        val withToken = adapter.findByResetTokenHash("hashed-token")
        assertEquals(saved.id, withToken?.id)

        adapter.updateResetToken(saved.id, null, null)
        assertNull(adapter.findByResetTokenHash("hashed-token"))
    }

    @Test fun `findByResetTokenHash returns null for an expired token`() {
        val saved = adapter.save(newAuthUser("expired-reset@test.com"))
        adapter.updateResetToken(saved.id, "expired-hash", Instant.now().minusSeconds(1))

        assertNull(adapter.findByResetTokenHash("expired-hash"))
    }

    @Test fun `search excludes deactivated users unless includeInactive is set`() {
        val active = adapter.save(newAuthUser("search-active@test.com"))
        val deactivated = adapter.save(newAuthUser("search-deactivated@test.com"))
        adapter.deactivate(deactivated.id, AuthUserId("1"))

        val defaultResult = adapter.search(null, page = 1, pageSize = 50, includeInactive = false)
        assertTrue(defaultResult.items.any { it.id == active.id })
        assertFalse(defaultResult.items.any { it.id == deactivated.id })

        val fullResult = adapter.search(null, page = 1, pageSize = 50, includeInactive = true)
        assertTrue(fullResult.items.any { it.id == deactivated.id })
    }

    @Test fun `search matches a case-insensitive substring of displayName or email`() {
        adapter.save(newAuthUser("findme@test.com"))

        val result = adapter.search("FINDME", page = 1, pageSize = 50, includeInactive = true)

        assertEquals(1, result.totalCount)
        assertEquals("findme@test.com", result.items.single().email.value)
    }
}

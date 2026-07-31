package com.adoptu.adapters.authkit

import com.adoptu.mocks.TestDatabase
import com.universaliun.auth.backend.domain.model.auth.RefreshToken
import com.universaliun.auth.backend.domain.model.user.AuthUser
import com.universaliun.auth.backend.domain.model.user.Email
import com.universaliun.auth.common.identity.AuthUserId
import com.universaliun.auth.common.rbac.PermissionSet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdoptuRefreshTokenRepositoryAdapterTest {

    private lateinit var userRepository: AdoptuUserRepositoryAdapter
    private lateinit var adapter: AdoptuRefreshTokenRepositoryAdapter
    private lateinit var userId: AuthUserId

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        userRepository = AdoptuUserRepositoryAdapter()
        adapter = AdoptuRefreshTokenRepositoryAdapter()
        userId = userRepository.save(
            AuthUser(
                id = AuthUserId(UUID.randomUUID().toString()),
                email = Email("refresh-bridge@test.com"),
                displayName = "Refresh Bridge",
                passwordHash = "hash",
                roles = emptySet(),
                permissions = PermissionSet.empty(0),
                enabled = true,
                emailVerified = true,
                createdAt = Instant.now(),
                lastModifiedAt = Instant.now(),
            )
        ).id
    }

    private fun token(id: UUID = UUID.randomUUID(), revoked: Boolean = false) = RefreshToken(
        id = id,
        tokenHash = "hash-$id",
        userId = userId,
        expiresAt = Instant.now().plusSeconds(3600),
        revoked = revoked,
        deviceInfo = "test-device",
    )

    @Test fun `save persists via real Postgres, visible to a second independent adapter instance`() {
        val saved = token()
        adapter.save(saved)

        val found = AdoptuRefreshTokenRepositoryAdapter().findByTokenHash(saved.tokenHash)
        assertNotNull(found)
        assertEquals(saved.id, found.id)
    }

    @Test fun `rotate scenario -- save with revoked=true updates the existing row instead of inserting a duplicate`() {
        val id = UUID.randomUUID()
        adapter.save(token(id = id, revoked = false))

        val stored = adapter.findByTokenHash("hash-$id")!!
        adapter.save(stored.copy(revoked = true))

        val afterRotate = adapter.findByTokenHash("hash-$id")
        assertNotNull(afterRotate)
        assertTrue(afterRotate.revoked)
        assertEquals(id, afterRotate.id)
    }

    @Test fun `findByTokenHash returns null for an unknown hash`() {
        assertNull(adapter.findByTokenHash("no-such-hash"))
    }

    @Test fun `revokeAllForUser revokes every token for that user`() {
        val first = token()
        val second = token()
        adapter.save(first)
        adapter.save(second)

        adapter.revokeAllForUser(userId)

        assertTrue(adapter.findByTokenHash(first.tokenHash)!!.revoked)
        assertTrue(adapter.findByTokenHash(second.tokenHash)!!.revoked)
    }

    @Test fun `revokeAllForUser does not revoke another user's tokens`() {
        val otherUserId = userRepository.save(
            AuthUser(
                id = AuthUserId(UUID.randomUUID().toString()),
                email = Email("other-refresh@test.com"),
                displayName = "Other",
                passwordHash = "hash",
                roles = emptySet(),
                permissions = PermissionSet.empty(0),
                enabled = true,
                emailVerified = true,
                createdAt = Instant.now(),
                lastModifiedAt = Instant.now(),
            )
        ).id
        val ownToken = token()
        val otherToken = token().copy(id = UUID.randomUUID(), tokenHash = "other-user-hash").let {
            RefreshToken(it.id, it.tokenHash, otherUserId, it.expiresAt, it.revoked, it.deviceInfo)
        }
        adapter.save(ownToken)
        adapter.save(otherToken)

        adapter.revokeAllForUser(userId)

        assertTrue(adapter.findByTokenHash(ownToken.tokenHash)!!.revoked)
        assertFalse(adapter.findByTokenHash(otherToken.tokenHash)!!.revoked)
    }

    @Test fun `deleteExpired removes tokens past their expiry`() {
        val expiredId = UUID.randomUUID()
        adapter.save(
            RefreshToken(
                id = expiredId,
                tokenHash = "expired-hash",
                userId = userId,
                expiresAt = Instant.now().minusSeconds(60),
                revoked = false,
                deviceInfo = null,
            )
        )

        adapter.deleteExpired()

        assertNull(adapter.findByTokenHash("expired-hash"))
    }
}

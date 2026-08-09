package com.adoptu.adapters.db.repositories

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.dto.input.PhotographerSettingsRequest
import com.adoptu.dto.input.UserRole
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking

/**
 * Direct repository-level tests for UserRepository methods that exist to satisfy the
 * UserRepositoryPort interface but are not currently reached (or not fully reached) by any HTTP
 * route or service call path -- e.g. the live photographer-settings route goes through
 * PhotographerService -> PhotographerRepositoryImpl instead of this class's
 * updatePhotographerSettings, and getUserIdByToken/getVerificationAttemptsToday/
 * recordVerificationAttempt have no caller at all (EmailVerificationService uses a separate
 * RateLimiter-backed counter). They still need to behave correctly (the interface contract is
 * real), so they're tested directly here rather than through a route.
 */
@OptIn(ExperimentalTime::class)
class UserRepositoryTest {

    private val clock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))
    private lateinit var repository: UserRepository

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        repository = UserRepository(clock)
    }

    private fun createTestUser(id: Int = 1): Int {
        return transaction {
            Users.insert {
                it[Users.id] = id
                it[Users.username] = "user$id@test.com"
                it[Users.displayName] = "User $id"
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
            } get Users.id
        }
    }

    @Test
    fun `updatePhotographerSettings returns null for non-existent user`() = runBlocking {
        val result = repository.updatePhotographerSettings(
            9999,
            PhotographerSettingsRequest(photographerFee = 50.0, photographerCurrency = "USD")
        )
        assertNull(result)
        Unit
    }

    @Test
    fun `updatePhotographerSettings throws for negative fee`() = runBlocking {
        val userId = createTestUser()
        assertThrows<IllegalArgumentException> {
            repository.updatePhotographerSettings(
                userId,
                PhotographerSettingsRequest(photographerFee = -1.0, photographerCurrency = "USD")
            )
        }
        Unit
    }

    @Test
    fun `updatePhotographerSettings creates settings when none exist`() = runBlocking {
        val userId = createTestUser()

        val result = repository.updatePhotographerSettings(
            userId,
            PhotographerSettingsRequest(photographerFee = 75.0, photographerCurrency = "USD", country = "United States", state = "NY")
        )

        assertEquals(userId, result?.userId)
        assertEquals(75.0, result?.photographerFee)
        assertEquals("United States", result?.country)
        Unit
    }

    @Test
    fun `updatePhotographerSettings updates existing settings`() = runBlocking {
        val userId = createTestUser()
        repository.updatePhotographerSettings(
            userId,
            PhotographerSettingsRequest(photographerFee = 50.0, photographerCurrency = "USD", country = "United States", state = "NY")
        )

        val result = repository.updatePhotographerSettings(
            userId,
            PhotographerSettingsRequest(photographerFee = 100.0, photographerCurrency = "EUR", country = "Canada", state = "ON")
        )

        assertEquals(100.0, result?.photographerFee)
        assertEquals("EUR", result?.photographerCurrency)
        assertEquals("Canada", result?.country)
        Unit
    }

    @Test
    fun `getPhotographers filters by country and state`() = runBlocking {
        val userId = createTestUser()
        transaction {
            UserActiveRoles.insert {
                it[UserActiveRoles.userId] = userId
                it[UserActiveRoles.role] = UserRole.PHOTOGRAPHER.name
            }
        }
        repository.updatePhotographerSettings(
            userId,
            PhotographerSettingsRequest(photographerFee = 50.0, photographerCurrency = "USD", country = "United States", state = "NY")
        )

        assertEquals(1, repository.getPhotographers("United States", "NY").size)
        assertTrue(repository.getPhotographers("Canada", null).isEmpty())
        assertTrue(repository.getPhotographers("United States", "CA").isEmpty())
        assertEquals(1, repository.getPhotographers(null, null).size)
        Unit
    }

    @Test
    fun `updateProfile updates displayName, language, and country together`() = runBlocking {
        val userId = createTestUser()

        val result = repository.updateProfile(userId, "New Name", "es", "United States")

        assertEquals("New Name", result?.displayName)
        assertEquals("es", result?.language)
        assertEquals("United States", result?.country)
        Unit
    }

    @Test
    fun `consumePendingRoleActivations returns empty set when nothing was ever added`() = runBlocking {
        val userId = createTestUser()

        val pending = repository.consumePendingRoleActivations(userId)

        assertTrue(pending.isEmpty())
    }

    @Test
    fun `addPendingRoleActivations then consumePendingRoleActivations returns the roles once`() = runBlocking {
        val userId = createTestUser()

        repository.addPendingRoleActivations(userId, setOf(UserRole.RESCUER, UserRole.PHOTOGRAPHER))

        val pending = repository.consumePendingRoleActivations(userId)
        assertEquals(setOf(UserRole.RESCUER, UserRole.PHOTOGRAPHER), pending)

        // Consuming clears them - a second call finds nothing left.
        val secondCall = repository.consumePendingRoleActivations(userId)
        assertTrue(secondCall.isEmpty())
    }

    @Test
    fun `addPendingRoleActivations is idempotent for a role already pending`() = runBlocking {
        val userId = createTestUser()

        repository.addPendingRoleActivations(userId, setOf(UserRole.RESCUER))
        repository.addPendingRoleActivations(userId, setOf(UserRole.RESCUER))

        val pending = repository.consumePendingRoleActivations(userId)
        assertEquals(setOf(UserRole.RESCUER), pending)
    }

    @Test
    fun `addPendingRoleActivations scopes pending roles per user`() = runBlocking {
        val userId1 = createTestUser(1)
        val userId2 = createTestUser(2)

        repository.addPendingRoleActivations(userId1, setOf(UserRole.RESCUER))
        repository.addPendingRoleActivations(userId2, setOf(UserRole.PHOTOGRAPHER))

        assertEquals(setOf(UserRole.RESCUER), repository.consumePendingRoleActivations(userId1))
        assertEquals(setOf(UserRole.PHOTOGRAPHER), repository.consumePendingRoleActivations(userId2))
    }

    // activateUrgentRescuerProfile's transaction body has two paths: the role doesn't exist yet
    // (insert it) and the role is already active (no-op, existingRole != null short-circuits the
    // insert). Only the first path is reached via UserServiceTest/other callers, so the no-op
    // branch is covered directly here.
    @Test
    fun `activateUrgentRescuerProfile inserts the role on first activation`() = runBlocking {
        val userId = createTestUser()

        val result = repository.activateUrgentRescuerProfile(userId)

        assertTrue(result?.activeRoles?.contains(UserRole.URGENT_RESCUER) == true)
    }

    @Test
    fun `activateUrgentRescuerProfile is a no-op when the role is already active`() = runBlocking {
        val userId = createTestUser()
        repository.activateUrgentRescuerProfile(userId)

        val result = repository.activateUrgentRescuerProfile(userId)

        assertTrue(result?.activeRoles?.contains(UserRole.URGENT_RESCUER) == true)
        // Still exactly one row for this user+role - the second call didn't insert a duplicate.
        val rowCount = transaction {
            UserActiveRoles.selectAll()
                .where { (UserActiveRoles.userId eq userId) and (UserActiveRoles.role eq UserRole.URGENT_RESCUER.name) }
                .count()
        }
        assertEquals(1L, rowCount)
    }

    // getUserIdByToken/getVerificationAttemptsToday/recordVerificationAttempt are declared on
    // UserRepositoryPort and implemented here, but nothing in the current codebase calls them --
    // EmailVerificationService and UserService both use the separate RateLimiter-backed
    // resendPolicy / verifyToken() methods instead (see EmailVerificationService.kt). They're
    // still a real part of the interface contract, so they're exercised directly rather than left
    // permanently dark.
    @Test
    fun `getUserIdByToken returns the owning user id for a valid token`() = runBlocking {
        val userId = createTestUser()
        repository.createEmailVerificationToken(userId, "tok-abc", clock.now().toEpochMilliseconds() + 60_000)

        val result = repository.getUserIdByToken("tok-abc")

        assertEquals(userId, result)
    }

    @Test
    fun `getUserIdByToken returns null for an unknown token`() = runBlocking {
        val result = repository.getUserIdByToken("does-not-exist")

        assertNull(result)
    }

    @Test
    fun `getVerificationAttemptsToday returns zero when nothing was recorded`() = runBlocking {
        val userId = createTestUser()

        val count = repository.getVerificationAttemptsToday(userId)

        assertEquals(0, count)
    }

    @Test
    fun `recordVerificationAttempt then getVerificationAttemptsToday reflects the new attempt`() = runBlocking {
        val userId = createTestUser()

        repository.recordVerificationAttempt(userId)
        repository.recordVerificationAttempt(userId)

        assertEquals(2, repository.getVerificationAttemptsToday(userId))
    }

    @Test
    fun `getVerificationAttemptsToday only counts attempts for the given user`() = runBlocking {
        val userId1 = createTestUser(1)
        val userId2 = createTestUser(2)

        repository.recordVerificationAttempt(userId1)

        assertEquals(1, repository.getVerificationAttemptsToday(userId1))
        assertEquals(0, repository.getVerificationAttemptsToday(userId2))
    }
}

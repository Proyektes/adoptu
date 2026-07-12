package com.adoptu.adapters.db.repositories

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.dto.input.PhotographerSettingsRequest
import com.adoptu.dto.input.UserRole
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import org.jetbrains.exposed.v1.jdbc.insert
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
 * Direct repository-level tests for two UserRepository methods that exist to satisfy the
 * UserRepositoryPort interface but are not currently reached by any HTTP route -- the live
 * photographer-settings route goes through PhotographerService -> PhotographerRepositoryImpl
 * instead. They still need to behave correctly (the interface contract is real), so they're
 * tested directly here rather than through a route.
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
}

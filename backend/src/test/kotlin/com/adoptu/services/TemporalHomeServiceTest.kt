package com.adoptu.services
import com.adoptu.adapters.authkit.AdoptuUserRepositoryAdapter

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.TemporalHomeRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.CreateTemporalHomeRequest
import com.adoptu.dto.input.SendTemporalHomeRequestRequest
import com.adoptu.dto.input.TemporalHomeDto
import com.adoptu.dto.input.TemporalHomeSearchParams
import com.adoptu.dto.input.UpdateTemporalHomeRequest
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalTime::class)
class TemporalHomeServiceTest {

    private lateinit var service: TemporalHomeService
    private lateinit var userService: UserService
    private lateinit var mockNotificationAdapter: MockNotificationAdapter
    private lateinit var temporalHomeRepository: TemporalHomeRepositoryImpl
    private val clock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        val userRepository = UserRepository(clock)
        val petRepository = PetRepositoryImpl(clock)
        val photographerRepository = PhotographerRepositoryImpl(petRepository, userRepository, clock)
        userService = UserService(userRepository, photographerRepository, AdoptuUserRepositoryAdapter())
        temporalHomeRepository = TemporalHomeRepositoryImpl(petRepository, userRepository, clock)
        mockNotificationAdapter = MockNotificationAdapter()
        service = TemporalHomeService(
            temporalHomeRepository = temporalHomeRepository,
            notificationAdapter = mockNotificationAdapter,
            userService = userService,
            userRepository = userRepository
        )
    }

    private fun cleanup() {
        transaction {
            com.adoptu.adapters.db.TemporalHomes.deleteWhere { com.adoptu.adapters.db.TemporalHomes.userId greater 0 }
            com.adoptu.adapters.db.BlockedRescuers.deleteWhere { com.adoptu.adapters.db.BlockedRescuers.id greater 0 }
            com.adoptu.adapters.db.TemporalHomeRequests.deleteWhere { com.adoptu.adapters.db.TemporalHomeRequests.id greater 0 }
            com.adoptu.adapters.db.Users.deleteWhere { com.adoptu.adapters.db.Users.id greater 0 }
        }
    }

    @Test
    fun `getTemporalHome returns null for non-existent user`() = runBlocking {
        val result = service.getTemporalHome(999)
        assertNull(result)
    }

    @Test
    fun `getTemporalHome returns temporal home by userId`() = runBlocking {
        val userId = createTestUser("home@test.com", "Test Home")
        createTemporalHome(userId, "My Home", "United States", "California", "Los Angeles")

        val result = service.getTemporalHome(userId)

        assertNotNull(result)
        assertEquals("My Home", result.alias)
        assertEquals("United States", result.country)
        assertEquals("California", result.state)
        assertEquals("Los Angeles", result.city)
    }

    @Test
    fun `createTemporalHome creates successfully`() = runBlocking {
        val userId = createTestUser("create@test.com", "Create User")
        val request = CreateTemporalHomeRequest(
            alias = "New Home",
            country = "Mexico",
            state = null,
            city = "Mexico City",
            zip = "06600",
            neighborhood = "Centro",
            streetAddress = "Calle Principal 123",
            phone = "+52-55-1234-5678"
        )

        val result = service.createTemporalHome(userId, request)

        assertNotNull(result)
        assertEquals(userId, result.userId)
        assertEquals("New Home", result.alias)
        assertEquals("Mexico", result.country)
        assertEquals("Mexico City", result.city)
    }

    @Test
    fun `updateTemporalHome updates successfully`() = runBlocking {
        val userId = createTestUser("update@test.com", "Update User")
        createTemporalHome(userId, "Old Alias", "United States", "California", "LA")
        val request = UpdateTemporalHomeRequest(
            alias = "New Alias",
            city = "San Francisco"
        )

        val result = service.updateTemporalHome(userId, request)

        assertNotNull(result)
        assertEquals("New Alias", result.alias)
        assertEquals("San Francisco", result.city)
        assertEquals("United States", result.country)
    }

    @Test
    fun `updateTemporalHome allows partial updates`() = runBlocking {
        val userId = createTestUser("partial@test.com", "Partial User")
        createTemporalHome(userId, "Original", "United States", "California", "LA")
        val request = UpdateTemporalHomeRequest(phone = "+1-555-9999")

        val result = service.updateTemporalHome(userId, request)

        assertNotNull(result)
        assertEquals("Original", result.alias)
    }

    @Test
    fun `updateTemporalHome returns null for non-existent user`() = runBlocking {
        val request = UpdateTemporalHomeRequest(alias = "New")

        val result = service.updateTemporalHome(999, request)

        assertNull(result)
    }

    @Test
    fun `searchTemporalHomes returns empty list when no results`() = runBlocking {
        val result = service.searchTemporalHomes(TemporalHomeSearchParams(country = "United States"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchTemporalHomes filters by country`() = runBlocking {
        val user1 = createTestUser("home1@test.com", "Home 1")
        val user2 = createTestUser("home2@test.com", "Home 2")
        createTemporalHome(user1, "US Home", "United States", "California", "LA")
        createTemporalHome(user2, "MX Home", "Mexico", null, "Mexico City")

        val result = service.searchTemporalHomes(TemporalHomeSearchParams(country = "United States"))

        assertEquals(1, result.size)
        assertEquals("US Home", result.first().alias)
    }

    @Test
    fun `searchTemporalHomes filters by country and state`() = runBlocking {
        val user1 = createTestUser("ca@test.com", "CA User")
        val user2 = createTestUser("ny@test.com", "NY User")
        createTemporalHome(user1, "CA Home", "United States", "California", "LA")
        createTemporalHome(user2, "NY Home", "United States", "New York", "NY")

        val result = service.searchTemporalHomes(
            TemporalHomeSearchParams(country = "United States", state = "California")
        )

        assertEquals(1, result.size)
        assertEquals("CA Home", result.first().alias)
    }

    @Test
    fun `searchTemporalHomes filters by all params`() = runBlocking {
        val user1 = createTestUser("la@test.com", "LA User")
        val user2 = createTestUser("sf@test.com", "SF User")
        createTemporalHome(user1, "LA Home", "United States", "California", "Los Angeles")
        createTemporalHome(user2, "SF Home", "United States", "California", "San Francisco")

        val result = service.searchTemporalHomes(
            TemporalHomeSearchParams(
                country = "United States",
                state = "California",
                city = "Los Angeles"
            )
        )

        assertEquals(1, result.size)
        assertEquals("LA Home", result.first().alias)
    }

    @Test
    fun `sendRequest fails when temporal home not found`() = runBlocking {
        val rescuerId = createTestUser("rescuer@test.com", "Rescuer", "RESCUER")
        val request = SendTemporalHomeRequestRequest(
            temporalHomeId = 999,
            message = "Need help"
        )

        val result = service.sendRequest(rescuerId, request)

        assertTrue(result.isFailure)
        assertEquals("Temporal home not found", result.exceptionOrNull()?.message)
    }

    @Test
    fun `sendRequest fails when user not found`() = runBlocking {
        val homeUserId = createTestUser("home@test.com", "Home User")
        createTemporalHome(homeUserId, "My Home", "United States", "California", "LA")

        val result = service.sendRequest(999, SendTemporalHomeRequestRequest(
            temporalHomeId = homeUserId,
            message = "Test"
        ))

        assertTrue(result.isFailure)
        assertEquals("User not found", result.exceptionOrNull()?.message)
    }

    @Test
    fun `sendRequest fails when user is not rescuer or admin`() = runBlocking {
        val homeUserId = createTestUser("home@test.com", "Home User")
        createTemporalHome(homeUserId, "My Home", "United States", "California", "LA")
        val adopterId = createTestUser("adopter@test.com", "Adopter", "ADOPTER")

        val result = service.sendRequest(adopterId, SendTemporalHomeRequestRequest(
            temporalHomeId = homeUserId,
            message = "Test"
        ))

        assertTrue(result.isFailure)
        assertEquals("Only rescuers can send temporal home requests", result.exceptionOrNull()?.message)
    }

    @Test
    fun `sendRequest succeeds for rescuer`() = runBlocking {
        val homeUserId = createTestUser("home@test.com", "Home User")
        createTemporalHome(homeUserId, "My Home", "United States", "California", "LA")
        val rescuerId = createTestUser("rescuer@test.com", "Rescuer", "RESCUER")

        val result = service.sendRequest(rescuerId, SendTemporalHomeRequestRequest(
            temporalHomeId = homeUserId,
            petId = null,
            message = "Need help with a pet"
        ))

        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
        Unit
    }

    @Test
    fun `sendRequest succeeds for admin`() = runBlocking {
        val homeUserId = createTestUser("home@test.com", "Home User")
        createTemporalHome(homeUserId, "My Home", "United States", "California", "LA")
        val adminId = createTestUser("admin@test.com", "Admin", "ADMIN")

        val result = service.sendRequest(adminId, SendTemporalHomeRequestRequest(
            temporalHomeId = homeUserId,
            message = "Admin help"
        ))

        assertTrue(result.isSuccess)
    }

    @Test
    fun `sendRequest fails when blocked`() = runBlocking {
        val homeUserId = createTestUser("home@test.com", "Home User")
        createTemporalHome(homeUserId, "My Home", "United States", "California", "LA")
        val rescuerId = createTestUser("rescuer@test.com", "Rescuer", "RESCUER")
        service.blockRescuer(homeUserId, rescuerId)

        val result = service.sendRequest(rescuerId, SendTemporalHomeRequestRequest(
            temporalHomeId = homeUserId,
            message = "Test"
        ))

        assertTrue(result.isFailure)
        assertEquals("You have been blocked by this temporal home", result.exceptionOrNull()?.message)
    }

    @Test
    fun `isBlocked returns true when blocked`() = runBlocking {
        val homeUserId = createTestUser("home@test.com", "Home User")
        createTemporalHome(homeUserId, "My Home", "United States", "California", "LA")
        val rescuerId = createTestUser("rescuer@test.com", "Rescuer", "RESCUER")
        service.blockRescuer(homeUserId, rescuerId)

        val result = service.isBlocked(homeUserId, rescuerId)

        assertTrue(result)
    }

    @Test
    fun `isBlocked returns false when not blocked`() = runBlocking {
        val homeUserId = createTestUser("home@test.com", "Home User")
        createTemporalHome(homeUserId, "My Home", "United States", "California", "LA")
        val rescuerId = createTestUser("rescuer@test.com", "Rescuer", "RESCUER")

        val result = service.isBlocked(homeUserId, rescuerId)

        assertFalse(result)
    }

    @Test
    fun `blockRescuer blocks rescuer successfully`() = runBlocking {
        val homeUserId = createTestUser("home@test.com", "Home User")
        createTemporalHome(homeUserId, "My Home", "United States", "California", "LA")
        val rescuerId = createTestUser("rescuer@test.com", "Rescuer", "RESCUER")

        val result = service.blockRescuer(homeUserId, rescuerId)

        assertTrue(result)
        assertTrue(service.isBlocked(homeUserId, rescuerId))
    }

    @Test
    fun `blockRescuer returns false for already blocked`() = runBlocking {
        val homeUserId = createTestUser("home@test.com", "Home User")
        createTemporalHome(homeUserId, "My Home", "United States", "California", "LA")
        val rescuerId = createTestUser("rescuer@test.com", "Rescuer", "RESCUER")
        service.blockRescuer(homeUserId, rescuerId)

        val result = service.blockRescuer(homeUserId, rescuerId)

        assertFalse(result)
    }

    @Test
    fun `blockRescuerByToken blocks the rescuer named by a valid token`() = runBlocking {
        val homeUserId = createTestUser("home@test.com", "Home User")
        createTemporalHome(homeUserId, "My Home", "United States", "California", "LA")
        val rescuerId = createTestUser("rescuer@test.com", "Rescuer", "RESCUER")
        val token = temporalHomeRepository.createSpamReportToken(homeUserId, rescuerId)

        val result = service.blockRescuerByToken(token)

        assertTrue(result)
        assertTrue(service.isBlocked(homeUserId, rescuerId))
    }

    @Test
    fun `blockRescuerByToken is single-use`() = runBlocking {
        val homeUserId = createTestUser("home@test.com", "Home User")
        createTemporalHome(homeUserId, "My Home", "United States", "California", "LA")
        val rescuerId = createTestUser("rescuer@test.com", "Rescuer", "RESCUER")
        val token = temporalHomeRepository.createSpamReportToken(homeUserId, rescuerId)

        assertTrue(service.blockRescuerByToken(token))
        assertFalse(service.blockRescuerByToken(token))
    }

    @Test
    fun `blockRescuerByToken returns false for an unknown token`() = runBlocking {
        val result = service.blockRescuerByToken("not-a-real-token")
        assertFalse(result)
    }

    @Test
    fun `getMyRequests returns empty list when no requests`() = runBlocking {
        val userId = createTestUser("test@test.com", "Test User")

        val result = service.getMyRequests(userId)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getMyRequests returns requests for user`() = runBlocking {
        val homeUserId = createTestUser("home@test.com", "Home User")
        createTemporalHome(homeUserId, "My Home", "United States", "California", "LA")
        val rescuerId = createTestUser("rescuer@test.com", "Rescuer", "RESCUER")
        service.sendRequest(rescuerId, SendTemporalHomeRequestRequest(
            temporalHomeId = homeUserId,
            message = "Need help"
        ))

        val result = service.getMyRequests(homeUserId)

        assertEquals(1, result.size)
        assertEquals(rescuerId, result.first().rescuerId)
        assertEquals("Need help", result.first().message)
    }

    private suspend fun createTemporalHome(
        userId: Int,
        alias: String,
        country: String,
        state: String?,
        city: String,
        zip: String? = null,
        neighborhood: String? = null
    ): TemporalHomeDto {
        val request = CreateTemporalHomeRequest(
            alias = alias,
            country = country,
            state = state,
            city = city,
            zip = zip,
            neighborhood = neighborhood
        )
        return service.createTemporalHome(userId, request)
    }

    private fun createTestUser(
        username: String,
        displayName: String,
        role: String = "ADOPTER"
    ): Int {
        val userId = transaction {
            Users.insert {
                it[Users.username] = username
                it[Users.displayName] = displayName
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
            } get Users.id
        }

        transaction {
            UserActiveRoles.insert {
                it[UserActiveRoles.userId] = userId
                it[UserActiveRoles.role] = role
            }
        }

        return userId
    }
}

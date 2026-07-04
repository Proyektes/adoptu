package com.adoptu.services

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.UserSterilizationLocations
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.adapters.db.repositories.UserSterilizationLocationRepository
import com.adoptu.dto.input.CreateUserSterilizationLocationRequest
import com.adoptu.dto.input.UpdateUserSterilizationLocationRequest
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalTime::class)
class UserSterilizationLocationServiceTest {

    private lateinit var userSterilizationLocationService: UserSterilizationLocationService
    private val clock: TestClock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        val repository = UserSterilizationLocationRepository(clock)
        val profileEmailVerificationService = ProfileEmailVerificationService(UserRepository(clock), MockNotificationAdapter(), clock)
        userSterilizationLocationService = UserSterilizationLocationService(repository, profileEmailVerificationService)
    }

    private val accountEmail = "account@test.com"
    private val displayName = "Test User"

    @Test
    fun `getByUserId returns null when no location exists`() = runBlocking {
        val userId = createTestUser("alice")

        val result = userSterilizationLocationService.getByUserId(userId)

        assertNull(result)
        Unit
    }

    @Test
    fun `getByUserId returns location for existing user`() = runBlocking {
        val userId = createTestUser("bob")
        createTestUserLocation(userId, name = "Bob's Clinic", country = "United States", state = "NY", city = "New York")

        val result = userSterilizationLocationService.getByUserId(userId)

        assertNotNull(result)
        assertEquals(userId, result.userId)
        assertEquals("Bob's Clinic", result.name)
        assertEquals("United States", result.country)
        assertEquals("NY", result.state)
        assertEquals("New York", result.city)
        Unit
    }

    @Test
    fun `create creates new location with all fields`() = runBlocking {
        val userId = createTestUser("carol")
        val request = CreateUserSterilizationLocationRequest(
            name = "Carol's Vet Clinic",
            country = "United States",
            state = "CA",
            city = "Los Angeles",
            neighborhood = "Downtown",
            address = "123 Vet Street",
            zip = "90001",
            phone = "+1-555-1234",
            email = "vet@clinic.com",
            website = "https://vet.com",
            description = "A great veterinary clinic"
        )

        val result = userSterilizationLocationService.create(userId, accountEmail, displayName, request)

        assertEquals(userId, result.userId)
        assertEquals("Carol's Vet Clinic", result.name)
        assertEquals("United States", result.country)
        assertEquals("CA", result.state)
        assertEquals("Los Angeles", result.city)
        assertEquals("Downtown", result.neighborhood)
        assertEquals("123 Vet Street", result.address)
        assertEquals("90001", result.zip)
        assertEquals("+1-555-1234", result.phone)
        assertEquals("vet@clinic.com", result.email)
        assertEquals("https://vet.com", result.website)
        assertEquals("A great veterinary clinic", result.description)

        val stored = userSterilizationLocationService.getByUserId(userId)
        assertNotNull(stored)
        assertEquals("Carol's Vet Clinic", stored.name)
        Unit
    }

    @Test
    fun `create creates location with minimal fields`() = runBlocking {
        val userId = createTestUser("dave")
        val request = CreateUserSterilizationLocationRequest(
            name = "Minimal Clinic",
            country = "United States",
            city = "NYC",
            address = "456 Oak Ave"
        )

        val result = userSterilizationLocationService.create(userId, accountEmail, displayName, request)

        assertEquals(userId, result.userId)
        assertEquals("Minimal Clinic", result.name)
        assertEquals("United States", result.country)
        assertNull(result.state)
        assertEquals("NYC", result.city)
        Unit
    }

    @Test
    fun `create throws exception when name is blank`() = runBlocking {
        val userId = createTestUser("erin")
        val request = CreateUserSterilizationLocationRequest(
            name = "",
            country = "United States",
            city = "NYC",
            address = "456 Oak Ave"
        )

        val exception = assertThrows<IllegalArgumentException> {
            userSterilizationLocationService.create(userId, accountEmail, displayName, request)
        }
        assertEquals("Name is required", exception.message)
        Unit
    }

    @Test
    fun `create throws exception when country is blank`() = runBlocking {
        val userId = createTestUser("frank")
        val request = CreateUserSterilizationLocationRequest(
            name = "Test",
            country = "",
            city = "NYC",
            address = "456 Oak Ave"
        )

        val exception = assertThrows<IllegalArgumentException> {
            userSterilizationLocationService.create(userId, accountEmail, displayName, request)
        }
        assertEquals("Country is required", exception.message)
        Unit
    }

    @Test
    fun `create throws exception when city is blank`() = runBlocking {
        val userId = createTestUser("grace")
        val request = CreateUserSterilizationLocationRequest(
            name = "Test",
            country = "United States",
            city = "",
            address = "456 Oak Ave"
        )

        val exception = assertThrows<IllegalArgumentException> {
            userSterilizationLocationService.create(userId, accountEmail, displayName, request)
        }
        assertEquals("City is required", exception.message)
        Unit
    }

    @Test
    fun `create throws exception when address is blank`() = runBlocking {
        val userId = createTestUser("heidi")
        val request = CreateUserSterilizationLocationRequest(
            name = "Test",
            country = "United States",
            city = "NYC",
            address = ""
        )

        val exception = assertThrows<IllegalArgumentException> {
            userSterilizationLocationService.create(userId, accountEmail, displayName, request)
        }
        assertEquals("Address is required", exception.message)
        Unit
    }

    @Test
    fun `create called twice for same user updates rather than duplicates`() = runBlocking {
        val userId = createTestUser("ivan")
        val firstRequest = CreateUserSterilizationLocationRequest(
            name = "First Clinic",
            country = "United States",
            state = "NY",
            city = "New York",
            address = "123 Main St"
        )
        val secondRequest = CreateUserSterilizationLocationRequest(
            name = "Second Clinic",
            country = "Canada",
            state = "ON",
            city = "Toronto",
            address = "456 Queen St"
        )

        val firstResult = userSterilizationLocationService.create(userId, accountEmail, displayName, firstRequest)
        assertEquals("First Clinic", firstResult.name)

        val secondResult = userSterilizationLocationService.create(userId, accountEmail, displayName, secondRequest)

        assertEquals(userId, secondResult.userId)
        assertEquals("Second Clinic", secondResult.name)
        assertEquals("Canada", secondResult.country)
        assertEquals("Toronto", secondResult.city)

        val stored = userSterilizationLocationService.getByUserId(userId)
        assertNotNull(stored)
        assertEquals("Second Clinic", stored.name)
        assertEquals("Canada", stored.country)

        val count = transaction {
            UserSterilizationLocations.selectAll().count()
        }
        assertEquals(1L, count)
        Unit
    }

    @Test
    fun `update updates location successfully`() = runBlocking {
        val userId = createTestUser("judy")
        createTestUserLocation(userId, name = "Old Name", country = "United States", state = "NY", city = "New York")

        val result = userSterilizationLocationService.update(userId, accountEmail, displayName, UpdateUserSterilizationLocationRequest(
            name = "Updated Name",
            city = "Brooklyn"
        ))

        assertTrue(result is ServiceResult.Success)
        val updated = result.data
        assertEquals("Updated Name", updated.name)
        assertEquals("Brooklyn", updated.city)
        assertEquals("NY", updated.state)
        Unit
    }

    @Test
    fun `update returns not found for user without location`() = runBlocking {
        val userId = createTestUser("kevin")

        val result = userSterilizationLocationService.update(userId, accountEmail, displayName, UpdateUserSterilizationLocationRequest(name = "New Name"))

        assertTrue(result is ServiceResult.NotFound)
        Unit
    }

    @Test
    fun `update only modifies specified fields`() = runBlocking {
        val userId = createTestUser("laura")
        createTestUserLocation(
            userId, name = "Original", country = "United States", state = "NY", city = "New York",
            phone = "555-0000", email = "old@test.com"
        )

        val result = userSterilizationLocationService.update(userId, accountEmail, displayName, UpdateUserSterilizationLocationRequest(name = "Updated"))

        assertTrue(result is ServiceResult.Success)
        val updated = result.data
        assertEquals("Updated", updated.name)
        assertEquals("United States", updated.country)
        assertEquals("NY", updated.state)
        assertEquals("New York", updated.city)
        assertEquals("555-0000", updated.phone)
        assertEquals("old@test.com", updated.email)
        Unit
    }

    @Test
    fun `delete removes location successfully`() = runBlocking {
        val userId = createTestUser("mallory")
        createTestUserLocation(userId, name = "To Delete", country = "United States", state = "NY", city = "New York")

        val result = userSterilizationLocationService.delete(userId)

        assertTrue(result is ServiceResult.Success)
        assertNull(userSterilizationLocationService.getByUserId(userId))
        Unit
    }

    @Test
    fun `delete returns not found for user without location`() = runBlocking {
        val userId = createTestUser("nina")

        val result = userSterilizationLocationService.delete(userId)

        assertTrue(result is ServiceResult.NotFound)
        Unit
    }

    @Test
    fun `search throws exception when country is blank`() = runBlocking {
        assertThrows<IllegalArgumentException> {
            userSterilizationLocationService.search("")
        }
        Unit
    }

    @Test
    fun `search throws exception when country is whitespace only`() = runBlocking {
        assertThrows<IllegalArgumentException> {
            userSterilizationLocationService.search("   ")
        }
        Unit
    }

    @Test
    fun `search filters by country only`() = runBlocking {
        val u1 = createTestUser("o1")
        val u2 = createTestUser("o2")
        val u3 = createTestUser("o3")
        createTestUserLocation(u1, name = "Clinic 1", country = "United States", state = "NY", city = "New York")
        createTestUserLocation(u2, name = "Clinic 2", country = "United States", state = "CA", city = "Los Angeles")
        createTestUserLocation(u3, name = "Clinic 3", country = "Canada", state = "ON", city = "Toronto")

        val result = userSterilizationLocationService.search("United States")

        assertEquals(2, result.size)
        assertTrue(result.all { it.country == "United States" })
        Unit
    }

    @Test
    fun `search filters by country and state`() = runBlocking {
        val u1 = createTestUser("p1")
        val u2 = createTestUser("p2")
        val u3 = createTestUser("p3")
        createTestUserLocation(u1, name = "Clinic 1", country = "United States", state = "NY", city = "New York")
        createTestUserLocation(u2, name = "Clinic 2", country = "United States", state = "CA", city = "Los Angeles")
        createTestUserLocation(u3, name = "Clinic 3", country = "United States", state = "NY", city = "Buffalo")

        val result = userSterilizationLocationService.search("United States", state = "NY")

        assertEquals(2, result.size)
        assertTrue(result.all { it.state == "NY" })
        Unit
    }

    @Test
    fun `search filters by country state and city`() = runBlocking {
        val u1 = createTestUser("q1")
        val u2 = createTestUser("q2")
        createTestUserLocation(u1, name = "Clinic 1", country = "United States", state = "NY", city = "New York")
        createTestUserLocation(u2, name = "Clinic 2", country = "United States", state = "NY", city = "Buffalo")

        val result = userSterilizationLocationService.search("United States", state = "NY", city = "New York")

        assertEquals(1, result.size)
        assertEquals("Clinic 1", result.first().name)
        Unit
    }

    @Test
    fun `search filters by country state city and neighborhood`() = runBlocking {
        val u1 = createTestUser("r1")
        val u2 = createTestUser("r2")
        createTestUserLocation(u1, name = "Clinic 1", country = "United States", state = "NY", city = "New York", neighborhood = "Brooklyn")
        createTestUserLocation(u2, name = "Clinic 2", country = "United States", state = "NY", city = "New York", neighborhood = "Queens")

        val result = userSterilizationLocationService.search("United States", state = "NY", city = "New York", neighborhood = "Brooklyn")

        assertEquals(1, result.size)
        assertEquals("Clinic 1", result.first().name)
        Unit
    }

    @Test
    fun `search filters by all parameters including zip`() = runBlocking {
        val u1 = createTestUser("s1")
        val u2 = createTestUser("s2")
        createTestUserLocation(u1, name = "Clinic 1", country = "United States", state = "NY", city = "New York", neighborhood = "Brooklyn", zip = "11201")
        createTestUserLocation(u2, name = "Clinic 2", country = "United States", state = "NY", city = "New York", neighborhood = "Brooklyn", zip = "11211")

        val result = userSterilizationLocationService.search("United States", state = "NY", city = "New York", neighborhood = "Brooklyn", zip = "11201")

        assertEquals(1, result.size)
        assertEquals("Clinic 1", result.first().name)
        Unit
    }

    @Test
    fun `search ignores blank optional filters`() = runBlocking {
        val u1 = createTestUser("t1")
        createTestUserLocation(u1, name = "Clinic 1", country = "United States", state = "NY", city = "New York")

        val result = userSterilizationLocationService.search("United States", state = "", city = "  ", neighborhood = null, zip = "")

        assertEquals(1, result.size)
        Unit
    }

    @Test
    fun `search returns empty list for no match`() = runBlocking {
        val u1 = createTestUser("u1")
        createTestUserLocation(u1, name = "Clinic 1", country = "United States", state = "NY", city = "New York")

        val result = userSterilizationLocationService.search("France")

        assertTrue(result.isEmpty())
        Unit
    }

    private fun createTestUser(username: String, displayName: String = username): Int {
        return transaction {
            Users.insert {
                it[Users.username] = username
                it[Users.displayName] = displayName
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
            } get Users.id
        }
    }

    private fun createTestUserLocation(
        userId: Int,
        name: String,
        country: String,
        state: String?,
        city: String,
        neighborhood: String? = null,
        address: String = "123 Test St",
        zip: String? = null,
        phone: String? = null,
        email: String? = null,
        website: String? = null,
        description: String? = null
    ) {
        transaction {
            UserSterilizationLocations.insert {
                it[UserSterilizationLocations.userId] = userId
                it[UserSterilizationLocations.name] = name
                it[UserSterilizationLocations.country] = com.adoptu.common.Country.fromDisplayName(country)!!
                it[UserSterilizationLocations.state] = state
                it[UserSterilizationLocations.city] = city
                it[UserSterilizationLocations.neighborhood] = neighborhood
                it[UserSterilizationLocations.address] = address
                it[UserSterilizationLocations.zip] = zip
                it[UserSterilizationLocations.phone] = phone
                it[UserSterilizationLocations.email] = email
                it[UserSterilizationLocations.website] = website
                it[UserSterilizationLocations.description] = description
                it[UserSterilizationLocations.createdAt] = clock.now().toEpochMilliseconds()
                it[UserSterilizationLocations.updatedAt] = clock.now().toEpochMilliseconds()
            }
            UserActiveRoles.insert {
                it[UserActiveRoles.userId] = userId
                it[UserActiveRoles.role] = "STERILIZATION_SERVICE"
            }
        }
    }
}

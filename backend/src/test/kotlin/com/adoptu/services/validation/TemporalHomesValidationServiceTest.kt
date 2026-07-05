package com.adoptu.services.validation

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.TemporalHomeRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.CreateTemporalHomeRequest
import com.adoptu.dto.input.UserDto
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import com.adoptu.services.ServiceResult
import com.adoptu.services.TemporalHomeService
import com.adoptu.services.UserService
import com.adoptu.services.auth.SessionUser
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class TemporalHomesValidationServiceTest {

    private lateinit var service: TemporalHomesValidationService
    private lateinit var userService: UserService
    private lateinit var temporalHomeService: TemporalHomeService
    private val clock: TestClock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        val userRepository = UserRepository(clock)
        userService = UserService(userRepository)
        val petRepository = PetRepositoryImpl(clock)
        val temporalHomeRepository = TemporalHomeRepositoryImpl(petRepository, userRepository, clock)
        val notificationAdapter = MockNotificationAdapter()
        temporalHomeService = TemporalHomeService(
            temporalHomeRepository = temporalHomeRepository,
            notificationAdapter = notificationAdapter,
            userService = userService,
            userRepository = userRepository
        )

        stopKoin() // defensive: clear any Koin app leaked from a concurrently-run test in this JVM
        startKoin {
            modules(module {
                single { userService }
                single { temporalHomeService }
            })
        }

        service = TemporalHomesValidationService()
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    private fun createTestUser(
        username: String = "user@test.com",
        displayName: String = "Test User",
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

    private fun createTemporalHomeRequest(
        alias: String = "My Home",
        country: String = "United States",
        city: String = "NYC"
    ) = CreateTemporalHomeRequest(
        alias = alias,
        country = country,
        city = city
    )

    // validateSession

    @Test
    fun `validateSession returns Forbidden when session is null`() = runBlocking {
        val result = service.validateSession(null)

        assertIs<ServiceResult.Forbidden>(result)
        Unit
    }

    @Test
    fun `validateSession returns Success when session is present`() = runBlocking {
        val session = SessionUser(userId = 1, email = "a@test.com", displayName = "A")

        val result = service.validateSession(session)

        assertIs<ServiceResult.Success<SessionUser>>(result)
        assertEquals(session, result.data)
        Unit
    }

    // validateUserById

    @Test
    fun `validateUserById returns Success when user exists`() = runBlocking {
        val userId = createTestUser()

        val result = service.validateUserById(userId)

        assertIs<ServiceResult.Success<UserDto>>(result)
        assertEquals(userId, result.data.id)
        Unit
    }

    @Test
    fun `validateUserById returns NotFound when user does not exist`() = runBlocking {
        val result = service.validateUserById(999)

        assertIs<ServiceResult.NotFound>(result)
        Unit
    }

    // validateUser

    @Test
    fun `validateUser returns Success when user is not null`() = runBlocking {
        val userId = createTestUser()
        val user = userService.getById(userId)!!

        val result = service.validateUser(user)

        assertIs<ServiceResult.Success<UserDto>>(result)
        assertEquals(user, result.data)
        Unit
    }

    @Test
    fun `validateUser returns NotFound when user is null`() = runBlocking {
        val result = service.validateUser(null)

        assertIs<ServiceResult.NotFound>(result)
        Unit
    }

    // validateId

    @Test
    fun `validateId returns Success for valid numeric id`() = runBlocking {
        val result = service.validateId("11")

        assertIs<ServiceResult.Success<Int>>(result)
        assertEquals(11, result.data)
        Unit
    }

    @Test
    fun `validateId returns Error for null id`() = runBlocking {
        val result = service.validateId(null)

        assertIs<ServiceResult.Error<Int>>(result)
        assertEquals(ValidationConstants.INVALID_ID, result.message)
        Unit
    }

    @Test
    fun `validateId returns Error for non-numeric id`() = runBlocking {
        val result = service.validateId("bad")

        assertIs<ServiceResult.Error<Int>>(result)
        assertEquals(ValidationConstants.INVALID_ID, result.message)
        Unit
    }

    // validateRequired

    @Test
    fun `validateRequired returns Success for non-blank value`() = runBlocking {
        val result = service.validateRequired("Some Value", "Alias")

        assertIs<ServiceResult.Success<String>>(result)
        assertEquals("Some Value", result.data)
        Unit
    }

    @Test
    fun `validateRequired returns Error for null value`() = runBlocking {
        val result = service.validateRequired(null, "Alias")

        assertIs<ServiceResult.Error<String>>(result)
        assertEquals("Alias is required", result.message)
        Unit
    }

    @Test
    fun `validateRequired returns Error for blank value`() = runBlocking {
        val result = service.validateRequired("   ", "Alias")

        assertIs<ServiceResult.Error<String>>(result)
        assertEquals("Alias is required", result.message)
        Unit
    }

    // validateRole

    @Test
    fun `validateRole returns Success when user has required role`() = runBlocking {
        val userId = createTestUser(role = "TEMPORAL_HOME")
        val user = userService.getById(userId)!!

        val result = service.validateRole(user, "TEMPORAL_HOME")

        assertIs<ServiceResult.Success<Unit>>(result)
        Unit
    }

    @Test
    fun `validateRole returns Success when user is admin`() = runBlocking {
        val userId = createTestUser(role = "ADMIN")
        val user = userService.getById(userId)!!

        val result = service.validateRole(user, "TEMPORAL_HOME")

        assertIs<ServiceResult.Success<Unit>>(result)
        Unit
    }

    @Test
    fun `validateRole returns Forbidden when user lacks required role`() = runBlocking {
        val userId = createTestUser(role = "ADOPTER")
        val user = userService.getById(userId)!!

        val result = service.validateRole(user, "TEMPORAL_HOME")

        assertIs<ServiceResult.Forbidden>(result)
        Unit
    }

    // validateCreateTemporalHomeRequest

    @Test
    fun `validateCreateTemporalHomeRequest returns Error when profile already exists`() = runBlocking {
        val userId = createTestUser(role = "TEMPORAL_HOME")
        temporalHomeService.createTemporalHome(userId, createTemporalHomeRequest())

        val result = service.validateCreateTemporalHomeRequest(userId, createTemporalHomeRequest())

        assertIs<ServiceResult.Error<CreateTemporalHomeRequest>>(result)
        assertEquals(ValidationConstants.TEMPORAL_HOME_PROFILE_ALREADY_EXISTS, result.message)
        Unit
    }

    @Test
    fun `validateCreateTemporalHomeRequest returns Error when alias is blank`() = runBlocking {
        val userId = createTestUser(role = "TEMPORAL_HOME")

        val result = service.validateCreateTemporalHomeRequest(userId, createTemporalHomeRequest(alias = ""))

        assertIs<ServiceResult.Error<CreateTemporalHomeRequest>>(result)
        assertEquals("Alias is required", result.message)
        Unit
    }

    @Test
    fun `validateCreateTemporalHomeRequest returns Error when country is blank`() = runBlocking {
        val userId = createTestUser(role = "TEMPORAL_HOME")

        val result = service.validateCreateTemporalHomeRequest(userId, createTemporalHomeRequest(country = ""))

        assertIs<ServiceResult.Error<CreateTemporalHomeRequest>>(result)
        assertEquals("Country is required", result.message)
        Unit
    }

    @Test
    fun `validateCreateTemporalHomeRequest returns Error when city is blank`() = runBlocking {
        val userId = createTestUser(role = "TEMPORAL_HOME")

        val result = service.validateCreateTemporalHomeRequest(userId, createTemporalHomeRequest(city = ""))

        assertIs<ServiceResult.Error<CreateTemporalHomeRequest>>(result)
        assertEquals("City is required", result.message)
        Unit
    }

    @Test
    fun `validateCreateTemporalHomeRequest returns Success when all fields valid and no existing profile`() = runBlocking {
        val userId = createTestUser(role = "TEMPORAL_HOME")
        val request = createTemporalHomeRequest()

        val result = service.validateCreateTemporalHomeRequest(userId, request)

        assertIs<ServiceResult.Success<CreateTemporalHomeRequest>>(result)
        assertEquals(request, result.data)
        Unit
    }

    // validateTemporalHomeProfile

    @Test
    fun `validateTemporalHomeProfile returns Success when profile exists`() = runBlocking {
        val userId = createTestUser(role = "TEMPORAL_HOME")
        temporalHomeService.createTemporalHome(userId, createTemporalHomeRequest())

        val result = service.validateTemporalHomeProfile(userId)

        assertIs<ServiceResult.Success<Unit>>(result)
        Unit
    }

    @Test
    fun `validateTemporalHomeProfile returns Error when profile does not exist`() = runBlocking {
        val userId = createTestUser(role = "TEMPORAL_HOME")

        val result = service.validateTemporalHomeProfile(userId)

        assertIs<ServiceResult.Error<Unit>>(result)
        assertEquals(ValidationConstants.TEMPORAL_HOME_PROFILE_NOT_FOUND, result.message)
        Unit
    }

    // validateRescuerRole

    @Test
    fun `validateRescuerRole returns NotFound when user does not exist`() = runBlocking {
        val result = service.validateRescuerRole(999)

        assertIs<ServiceResult.NotFound>(result)
        Unit
    }

    @Test
    fun `validateRescuerRole returns Success when user is a rescuer`() = runBlocking {
        val userId = createTestUser(role = "RESCUER")

        val result = service.validateRescuerRole(userId)

        assertIs<ServiceResult.Success<UserDto>>(result)
        assertEquals(userId, result.data.id)
        Unit
    }

    @Test
    fun `validateRescuerRole returns Success when user is admin`() = runBlocking {
        val userId = createTestUser(role = "ADMIN")

        val result = service.validateRescuerRole(userId)

        assertIs<ServiceResult.Success<UserDto>>(result)
        assertEquals(userId, result.data.id)
        Unit
    }

    @Test
    fun `validateRescuerRole returns Error when user is neither rescuer nor admin`() = runBlocking {
        val userId = createTestUser(role = "ADOPTER")

        val result = service.validateRescuerRole(userId)

        assertIs<ServiceResult.Error<UserDto>>(result)
        assertEquals(ValidationConstants.ONLY_RESCUERS_CAN_SEND_REQUESTS, result.message)
        Unit
    }

    // validateBlockRescuerRequest

    @Test
    fun `validateBlockRescuerRequest returns NotFound when user does not exist`() = runBlocking {
        val result = service.validateBlockRescuerRequest(999)

        assertIs<ServiceResult.NotFound>(result)
        Unit
    }

    @Test
    fun `validateBlockRescuerRequest returns Success when user is a temporal home`() = runBlocking {
        val userId = createTestUser(role = "TEMPORAL_HOME")

        val result = service.validateBlockRescuerRequest(userId)

        assertIs<ServiceResult.Success<UserDto>>(result)
        assertEquals(userId, result.data.id)
        Unit
    }

    @Test
    fun `validateBlockRescuerRequest returns Success when user is admin`() = runBlocking {
        val userId = createTestUser(role = "ADMIN")

        val result = service.validateBlockRescuerRequest(userId)

        assertIs<ServiceResult.Success<UserDto>>(result)
        assertEquals(userId, result.data.id)
        Unit
    }

    @Test
    fun `validateBlockRescuerRequest returns Forbidden when user is neither temporal home nor admin`() = runBlocking {
        val userId = createTestUser(role = "ADOPTER")

        val result = service.validateBlockRescuerRequest(userId)

        assertIs<ServiceResult.Forbidden>(result)
        Unit
    }

    // validateTemporalHomeId

    @Test
    fun `validateTemporalHomeId returns Success for valid numeric id`() = runBlocking {
        val result = service.validateTemporalHomeId("7")

        assertIs<ServiceResult.Success<Int>>(result)
        assertEquals(7, result.data)
        Unit
    }

    @Test
    fun `validateTemporalHomeId returns Error for null id`() = runBlocking {
        val result = service.validateTemporalHomeId(null)

        assertIs<ServiceResult.Error<Int>>(result)
        assertEquals(ValidationConstants.INVALID_TEMPORAL_HOME_ID, result.message)
        Unit
    }

    @Test
    fun `validateTemporalHomeId returns Error for non-numeric id`() = runBlocking {
        val result = service.validateTemporalHomeId("nope")

        assertIs<ServiceResult.Error<Int>>(result)
        assertEquals(ValidationConstants.INVALID_TEMPORAL_HOME_ID, result.message)
        Unit
    }
}

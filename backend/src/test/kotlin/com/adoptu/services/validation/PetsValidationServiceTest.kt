package com.adoptu.services.validation

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.UserDto
import com.adoptu.dto.input.UserRole
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import com.adoptu.services.ServiceResult
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
class PetsValidationServiceTest {

    private lateinit var service: PetsValidationService
    private lateinit var userService: UserService
    private val clock: TestClock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        val userRepository = UserRepository(clock)
        val photographerRepository = PhotographerRepositoryImpl(PetRepositoryImpl(clock), userRepository, clock)
        userService = UserService(userRepository, photographerRepository)

        stopKoin() // defensive: clear any Koin app leaked from a concurrently-run test in this JVM
        startKoin {
            modules(module {
                single { userService }
            })
        }

        service = PetsValidationService()
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

    private fun userDto(roles: Set<UserRole>): UserDto = UserDto(
        id = 1,
        username = "user@test.com",
        displayName = "Test User",
        activeRoles = roles
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
        val user = userDto(emptySet())

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
        val result = service.validateId("5")

        assertIs<ServiceResult.Success<Int>>(result)
        assertEquals(5, result.data)
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
        val result = service.validateId("xyz")

        assertIs<ServiceResult.Error<Int>>(result)
        assertEquals(ValidationConstants.INVALID_ID, result.message)
        Unit
    }

    // validateRequired

    @Test
    fun `validateRequired returns Success for non-blank value`() = runBlocking {
        val result = service.validateRequired("Rex", "Name")

        assertIs<ServiceResult.Success<String>>(result)
        assertEquals("Rex", result.data)
        Unit
    }

    @Test
    fun `validateRequired returns Error for null value`() = runBlocking {
        val result = service.validateRequired(null, "Name")

        assertIs<ServiceResult.Error<String>>(result)
        assertEquals("Name is required", result.message)
        Unit
    }

    @Test
    fun `validateRequired returns Error for blank value`() = runBlocking {
        val result = service.validateRequired("  ", "Breed")

        assertIs<ServiceResult.Error<String>>(result)
        assertEquals("Breed is required", result.message)
        Unit
    }

    // validateRole

    @Test
    fun `validateRole returns Success when user has required role`() = runBlocking {
        val user = userDto(setOf(UserRole.RESCUER))

        val result = service.validateRole(user, "RESCUER")

        assertIs<ServiceResult.Success<Unit>>(result)
        Unit
    }

    @Test
    fun `validateRole returns Success when user is admin`() = runBlocking {
        val user = userDto(setOf(UserRole.ADMIN))

        val result = service.validateRole(user, "RESCUER")

        assertIs<ServiceResult.Success<Unit>>(result)
        Unit
    }

    @Test
    fun `validateRole returns Forbidden when user lacks required role`() = runBlocking {
        val user = userDto(setOf(UserRole.ADOPTER))

        val result = service.validateRole(user, "RESCUER")

        assertIs<ServiceResult.Forbidden>(result)
        Unit
    }

    // validateRoles

    @Test
    fun `validateRoles returns Success when user has one of the required roles`() = runBlocking {
        val user = userDto(setOf(UserRole.PHOTOGRAPHER))

        val result = service.validateRoles(user, listOf("RESCUER", "PHOTOGRAPHER"))

        assertIs<ServiceResult.Success<Unit>>(result)
        Unit
    }

    @Test
    fun `validateRoles returns Success when user is admin`() = runBlocking {
        val user = userDto(setOf(UserRole.ADMIN))

        val result = service.validateRoles(user, listOf("RESCUER", "PHOTOGRAPHER"))

        assertIs<ServiceResult.Success<Unit>>(result)
        Unit
    }

    @Test
    fun `validateRoles returns Forbidden when user has none of the required roles`() = runBlocking {
        val user = userDto(setOf(UserRole.ADOPTER))

        val result = service.validateRoles(user, listOf("RESCUER", "PHOTOGRAPHER"))

        assertIs<ServiceResult.Forbidden>(result)
        Unit
    }

    // validateStatus

    @Test
    fun `validateStatus returns Success for valid status`() = runBlocking {
        val result = service.validateStatus("AVAILABLE", listOf("AVAILABLE", "ADOPTED"))

        assertIs<ServiceResult.Success<String>>(result)
        assertEquals("AVAILABLE", result.data)
        Unit
    }

    @Test
    fun `validateStatus returns Error for null status`() = runBlocking {
        val result = service.validateStatus(null, listOf("AVAILABLE", "ADOPTED"))

        assertIs<ServiceResult.Error<String>>(result)
        assertEquals("Invalid status", result.message)
        Unit
    }

    @Test
    fun `validateStatus returns Error for status not in valid list`() = runBlocking {
        val result = service.validateStatus("UNKNOWN", listOf("AVAILABLE", "ADOPTED"))

        assertIs<ServiceResult.Error<String>>(result)
        assertEquals("Invalid status", result.message)
        Unit
    }
}

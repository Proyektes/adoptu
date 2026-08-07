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
class UsersValidationServiceTest {

    private lateinit var service: UsersValidationService
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

        service = UsersValidationService()
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

    private fun userDto(roles: Set<UserRole>, id: Int = 1): UserDto = UserDto(
        id = id,
        username = "user@test.com",
        displayName = "Test User",
        activeRoles = roles
    )

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
        val result = service.validateId("3")

        assertIs<ServiceResult.Success<Int>>(result)
        assertEquals(3, result.data)
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
        val result = service.validateId("nan")

        assertIs<ServiceResult.Error<Int>>(result)
        assertEquals(ValidationConstants.INVALID_ID, result.message)
        Unit
    }

    // validateRequired

    @Test
    fun `validateRequired returns Success for non-blank value`() = runBlocking {
        val result = service.validateRequired("Some Name", "Name")

        assertIs<ServiceResult.Success<String>>(result)
        assertEquals("Some Name", result.data)
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
        val result = service.validateRequired("   ", "Name")

        assertIs<ServiceResult.Error<String>>(result)
        assertEquals("Name is required", result.message)
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

    // validateNotSelf

    @Test
    fun `validateNotSelf returns Success when ids differ`() = runBlocking {
        val result = service.validateNotSelf(1, 2)

        assertIs<ServiceResult.Success<Unit>>(result)
        Unit
    }

    @Test
    fun `validateNotSelf returns Error when ids are equal`() = runBlocking {
        val result = service.validateNotSelf(1, 1)

        assertIs<ServiceResult.Error<Unit>>(result)
        assertEquals("Cannot perform action on yourself", result.message)
        Unit
    }

    // validateNotAdmin

    @Test
    fun `validateNotAdmin returns NotFound when user is null`() = runBlocking {
        val result = service.validateNotAdmin(null)

        assertIs<ServiceResult.NotFound>(result)
        Unit
    }

    @Test
    fun `validateNotAdmin returns Success when user is not admin`() = runBlocking {
        val user = userDto(setOf(UserRole.ADOPTER))

        val result = service.validateNotAdmin(user)

        assertIs<ServiceResult.Success<Unit>>(result)
        Unit
    }

    @Test
    fun `validateNotAdmin returns Error when user is admin`() = runBlocking {
        val user = userDto(setOf(UserRole.ADMIN))

        val result = service.validateNotAdmin(user)

        assertIs<ServiceResult.Error<Unit>>(result)
        assertEquals("Cannot perform action on an admin", result.message)
        Unit
    }
}

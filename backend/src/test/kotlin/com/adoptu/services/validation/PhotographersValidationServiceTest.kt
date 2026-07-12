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
class PhotographersValidationServiceTest {

    private lateinit var service: PhotographersValidationService
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

        service = PhotographersValidationService()
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    private fun createTestUser(
        username: String = "photog@test.com",
        displayName: String = "Photog User",
        role: String = "PHOTOGRAPHER"
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
        username = "photog@test.com",
        displayName = "Photog User",
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
        val result = service.validateId("9")

        assertIs<ServiceResult.Success<Int>>(result)
        assertEquals(9, result.data)
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
        val result = service.validateId("nope")

        assertIs<ServiceResult.Error<Int>>(result)
        assertEquals(ValidationConstants.INVALID_ID, result.message)
        Unit
    }

    // validateRole

    @Test
    fun `validateRole returns Success when user has required role`() = runBlocking {
        val user = userDto(setOf(UserRole.PHOTOGRAPHER))

        val result = service.validateRole(user, "PHOTOGRAPHER")

        assertIs<ServiceResult.Success<Unit>>(result)
        Unit
    }

    @Test
    fun `validateRole returns Success when user is admin`() = runBlocking {
        val user = userDto(setOf(UserRole.ADMIN))

        val result = service.validateRole(user, "PHOTOGRAPHER")

        assertIs<ServiceResult.Success<Unit>>(result)
        Unit
    }

    @Test
    fun `validateRole returns Forbidden when user lacks required role`() = runBlocking {
        val user = userDto(setOf(UserRole.ADOPTER))

        val result = service.validateRole(user, "PHOTOGRAPHER")

        assertIs<ServiceResult.Forbidden>(result)
        Unit
    }

    // validatePhotographerFee

    @Test
    fun `validatePhotographerFee returns Success for positive fee`() = runBlocking {
        val result = service.validatePhotographerFee(25.5)

        assertIs<ServiceResult.Success<Double>>(result)
        assertEquals(25.5, result.data)
        Unit
    }

    @Test
    fun `validatePhotographerFee returns Success for zero fee`() = runBlocking {
        val result = service.validatePhotographerFee(0.0)

        assertIs<ServiceResult.Success<Double>>(result)
        assertEquals(0.0, result.data)
        Unit
    }

    @Test
    fun `validatePhotographerFee returns Error for negative fee`() = runBlocking {
        val result = service.validatePhotographerFee(-1.0)

        assertIs<ServiceResult.Error<Double>>(result)
        assertEquals("Photographer fee must be zero or positive", result.message)
        Unit
    }

    @Test
    fun `validatePhotographerFee returns Error for null fee`() = runBlocking {
        val result = service.validatePhotographerFee(null)

        assertIs<ServiceResult.Error<Double>>(result)
        assertEquals("Photographer fee must be zero or positive", result.message)
        Unit
    }
}

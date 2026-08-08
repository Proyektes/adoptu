package com.adoptu.services.validation
import com.adoptu.adapters.authkit.AdoptuUserRepositoryAdapter

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.UserDto
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import com.adoptu.services.ServiceResult
import com.adoptu.services.UserService
import com.adoptu.services.crypto.CryptoService
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class AuthValidationServiceTest {

    private lateinit var service: AuthValidationService
    private lateinit var userService: UserService
    private val clock: TestClock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        val userRepository = UserRepository(clock)
        val photographerRepository = PhotographerRepositoryImpl(PetRepositoryImpl(clock), userRepository, clock)
        userService = UserService(userRepository, photographerRepository, AdoptuUserRepositoryAdapter())

        stopKoin() // defensive: clear any Koin app leaked from a concurrently-run test in this JVM
        startKoin {
            modules(module {
                single { userService }
            })
        }

        service = AuthValidationService()
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

    private fun encrypted(plaintext: String): String {
        val publicKey = CryptoService.getPublicKey()
        return CryptoService.encrypt(plaintext, publicKey)!!
    }

    // validateAndDecryptEmail

    @Test
    fun `validateAndDecryptEmail returns Success for valid encrypted email`() = runBlocking {
        val data = encrypted("user@test.com")

        val result = service.validateAndDecryptEmail(data)

        assertIs<ServiceResult.Success<String>>(result)
        assertEquals("user@test.com", result.data)
        Unit
    }

    @Test
    fun `validateAndDecryptEmail returns Error when decryption fails`() = runBlocking {
        val result = service.validateAndDecryptEmail("not-a-valid-ciphertext!!")

        assertIs<ServiceResult.Error<String>>(result)
        assertEquals("Failed to decrypt email", result.message)
        Unit
    }

    @Test
    fun `validateAndDecryptEmail returns Error for invalid email format`() = runBlocking {
        val data = encrypted("not-an-email")

        val result = service.validateAndDecryptEmail(data)

        assertIs<ServiceResult.Error<String>>(result)
        assertEquals("Invalid email format", result.message)
        Unit
    }

    // validateEmailAndUser

    @Test
    fun `validateEmailAndUser returns Error for invalid email format`() = runBlocking {
        val result = service.validateEmailAndUser("not-an-email")

        assertIs<ServiceResult.Error<UserDto>>(result)
        assertEquals("Invalid email format", result.message)
        Unit
    }

    @Test
    fun `validateEmailAndUser returns Error when user not found`() = runBlocking {
        val result = service.validateEmailAndUser("missing@test.com")

        assertIs<ServiceResult.Error<UserDto>>(result)
        assertEquals("Invalid credentials", result.message)
        Unit
    }

    @Test
    fun `validateEmailAndUser returns Success when user found`() = runBlocking {
        createTestUser(username = "found@test.com")

        val result = service.validateEmailAndUser("found@test.com")

        assertIs<ServiceResult.Success<UserDto>>(result)
        assertEquals("found@test.com", result.data.username)
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

    // validateNotBanned

    @Test
    fun `validateNotBanned returns Success when user is not banned`() = runBlocking {
        val userId = createTestUser()

        val result = service.validateNotBanned(userId)

        assertIs<ServiceResult.Success<UserDto>>(result)
        assertEquals(userId, result.data.id)
        Unit
    }

    @Test
    fun `validateNotBanned returns NotFound when user does not exist`() = runBlocking {
        val result = service.validateNotBanned(999)

        assertIs<ServiceResult.NotFound>(result)
        Unit
    }

    @Test
    fun `validateNotBanned returns Error with reason when user is banned with a reason`() = runBlocking {
        val userId = createTestUser()
        userService.banUser(userId, "Spam activity")

        val result = service.validateNotBanned(userId)

        assertIs<ServiceResult.Error<UserDto>>(result)
        assertEquals("Your account has been suspended. Reason: Spam activity", result.message)
        Unit
    }

    @Test
    fun `validateNotBanned returns Error with default reason when user is banned without a reason`() = runBlocking {
        val userId = createTestUser()
        userService.banUser(userId)

        val result = service.validateNotBanned(userId)

        assertIs<ServiceResult.Error<UserDto>>(result)
        assertEquals("Your account has been suspended. Reason: Contact administrator", result.message)
        Unit
    }

    // validateVerified

    @Test
    fun `validateVerified returns Error with email when user is not verified`() = runBlocking {
        val userId = createTestUser()

        val result = service.validateVerified(userId, "user@test.com")

        assertIs<ServiceResult.Error<Unit>>(result)
        assertEquals("user@test.com", result.message)
        Unit
    }

    @Test
    fun `validateVerified returns Success when user is verified`() = runBlocking {
        val userId = createTestUser()
        transaction {
            Users.update({ Users.id eq userId }) {
                it[Users.isEmailVerified] = true
            }
        }

        val result = service.validateVerified(userId, "user@test.com")

        assertIs<ServiceResult.Success<Unit>>(result)
        Unit
    }

    // getUserByEmail

    @Test
    fun `getUserByEmail returns user when found`() = runBlocking {
        createTestUser(username = "lookup@test.com")

        val result = service.getUserByEmail("lookup@test.com")

        assertEquals("lookup@test.com", result?.username)
        Unit
    }

    @Test
    fun `getUserByEmail returns null when not found`() = runBlocking {
        val result = service.getUserByEmail("missing@test.com")

        assertNull(result)
        Unit
    }
}

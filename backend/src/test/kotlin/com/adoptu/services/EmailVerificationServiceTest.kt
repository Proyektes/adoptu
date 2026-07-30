package com.adoptu.services

import com.adoptu.adapters.db.EmailVerificationTokens
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import com.universaliun.ratelimit.common.InMemoryRateLimitStateAdapter
import com.universaliun.ratelimit.common.RateLimiter
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalTime::class)
class EmailVerificationServiceTest {

    private val clock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))
    private lateinit var emailVerificationService: EmailVerificationService
    private lateinit var userRepository: UserRepository
    private lateinit var mockNotificationAdapter: MockNotificationAdapter

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        userRepository = UserRepository(clock)
        mockNotificationAdapter = MockNotificationAdapter()
        emailVerificationService = EmailVerificationService(userRepository, mockNotificationAdapter, clock, "http://localhost:80", RateLimiter(InMemoryRateLimitStateAdapter()))
    }

    @Test
    fun `generateAndSendVerificationEmail sends email successfully`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        val result = emailVerificationService.generateAndSendVerificationEmail(
            userId = userId,
            email = "test@example.com",
            displayName = "Test User",
            language = "en"
        )

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == true)
        
        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertEquals(1, sentEmails.size)
        assertTrue(sentEmails.first().subject.contains("Verify your email"))
    }

    @Test
    fun `generateAndSendVerificationEmail respects rate limit`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        repeat(3) {
            emailVerificationService.generateAndSendVerificationEmail(
                userId = userId,
                email = "test@example.com",
                displayName = "Test User",
                language = "en"
            )
        }

        val result = emailVerificationService.generateAndSendVerificationEmail(
            userId = userId,
            email = "test@example.com",
            displayName = "Test User",
            language = "en"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RateLimitExceededException)
    }

    @Test
    fun `generateAndSendVerificationEmail works for Spanish language`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        val result = emailVerificationService.generateAndSendVerificationEmail(
            userId = userId,
            email = "test@example.com",
            displayName = "Test User",
            language = "es"
        )

        assertTrue(result.isSuccess)
        
        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertTrue(sentEmails.first().subject.contains("Verifica tu correo"))
    }

    @Test
    fun `generateAndSendVerificationEmail works for French language`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        val result = emailVerificationService.generateAndSendVerificationEmail(
            userId = userId,
            email = "test@example.com",
            displayName = "Test User",
            language = "fr"
        )

        assertTrue(result.isSuccess)
        
        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertTrue(sentEmails.first().subject.contains("Vérifiez votre email"))
    }

    @Test
    fun `generateAndSendVerificationEmail works for German language`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        val result = emailVerificationService.generateAndSendVerificationEmail(
            userId = userId,
            email = "test@example.com",
            displayName = "Test User",
            language = "de"
        )

        assertTrue(result.isSuccess)
        
        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertTrue(sentEmails.first().subject.contains("Bestätigen Sie Ihre E-Mail"))
    }

    @Test
    fun `generateAndSendVerificationEmail works for Italian language`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        val result = emailVerificationService.generateAndSendVerificationEmail(
            userId = userId,
            email = "test@example.com",
            displayName = "Test User",
            language = "it"
        )

        assertTrue(result.isSuccess)
        
        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertTrue(sentEmails.first().subject.contains("Verifica la tua email"))
    }

    @Test
    fun `generateAndSendVerificationEmail works for Portuguese language`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        val result = emailVerificationService.generateAndSendVerificationEmail(
            userId = userId,
            email = "test@example.com",
            displayName = "Test User",
            language = "pt"
        )

        assertTrue(result.isSuccess)
        
        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertTrue(sentEmails.first().subject.contains("Verifique seu email"))
    }

    @Test
    fun `verifyToken returns true and marks email as verified`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val token = createVerificationToken(userId)

        val result = emailVerificationService.verifyToken(token)

        assertTrue(result)
        
        val isVerified = emailVerificationService.isUserVerified(userId)
        assertTrue(isVerified)
    }

    @Test
    fun `verifyToken returns false for invalid token`() = runBlocking {
        val result = emailVerificationService.verifyToken("invalid-token")

        assertFalse(result)
    }

    @Test
    fun `verifyToken returns false when token not found`() = runBlocking {
        val result = emailVerificationService.verifyToken("nonexistenttoken123")

        assertFalse(result)
    }

    @Test
    fun `verifyTokenAndGetLanguage returns true and language when token valid`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User", language = "es")
        val token = createVerificationToken(userId)

        val (success, language) = emailVerificationService.verifyTokenAndGetLanguage(token)

        assertTrue(success)
        assertEquals("es", language)
    }

    @Test
    fun `verifyTokenAndGetLanguage returns false and default language for invalid token`() = runBlocking {
        val (success, language) = emailVerificationService.verifyTokenAndGetLanguage("invalid-token")

        assertFalse(success)
        assertEquals("en", language)
    }

    @Test
    fun `isUserVerified returns true when email is verified`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User", isEmailVerified = true)

        val result = emailVerificationService.isUserVerified(userId)

        assertTrue(result)
    }

    @Test
    fun `isUserVerified returns false when email is not verified`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User", isEmailVerified = false)

        val result = emailVerificationService.isUserVerified(userId)

        assertFalse(result)
    }

    @Test
    fun `resendVerificationEmail deletes old tokens and sends new email`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        createVerificationToken(userId)

        emailVerificationService.resendVerificationEmail(
            userId = userId,
            email = "test@example.com",
            displayName = "Test User",
            language = "en"
        )

        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertEquals(1, sentEmails.size)
    }

    @Test
    fun `canSendVerificationEmail returns true when under limit`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        val result = emailVerificationService.canSendVerificationEmail(userId)

        assertTrue(result)
    }

    @Test
    fun `canSendVerificationEmail returns false when at limit`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        repeat(3) {
            kotlinx.coroutines.runBlocking {
                emailVerificationService.generateAndSendVerificationEmail(
                    userId = userId,
                    email = "test@example.com",
                    displayName = "Test User"
                )
            }
        }

        val result = emailVerificationService.canSendVerificationEmail(userId)

        assertFalse(result)
    }

    @Test
    fun `verifyToken cleans up tokens after verification`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val token = createVerificationToken(userId)

        emailVerificationService.verifyToken(token)

        val isVerified = emailVerificationService.isUserVerified(userId)
        assertTrue(isVerified)
    }

    private fun createTestUser(
        username: String,
        displayName: String,
        language: String = "en",
        isEmailVerified: Boolean = false
    ): Int {
        return transaction {
            Users.insert {
                it[Users.username] = username
                it[Users.displayName] = displayName
                it[Users.language] = language
                it[Users.isEmailVerified] = isEmailVerified
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
            } get Users.id
        }
    }

    private fun createVerificationToken(userId: Int): String {
        val token = "test-token-123"
        transaction {
            EmailVerificationTokens.insert {
                it[EmailVerificationTokens.userId] = userId
                it[EmailVerificationTokens.token] = token
                it[EmailVerificationTokens.expiresAt] = clock.now().toEpochMilliseconds() + 86400000
                it[EmailVerificationTokens.createdAt] = clock.now().toEpochMilliseconds()
            }
        }
        return token
    }
}

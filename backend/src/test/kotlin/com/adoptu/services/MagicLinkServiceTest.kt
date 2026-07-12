package com.adoptu.services

import com.adoptu.adapters.db.EmailVerificationTokens
import com.adoptu.adapters.db.MagicLinkTokens
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class MagicLinkServiceTest {

    private val clock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))
    private lateinit var magicLinkService: MagicLinkService
    private lateinit var userRepository: UserRepository
    private lateinit var mockNotificationAdapter: MockNotificationAdapter
    private lateinit var emailVerificationService: EmailVerificationService

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        userRepository = UserRepository(clock)
        mockNotificationAdapter = MockNotificationAdapter()
        emailVerificationService = EmailVerificationService(userRepository, mockNotificationAdapter, clock, "http://localhost:80")
        magicLinkService = MagicLinkService(userRepository, mockNotificationAdapter, clock, "http://localhost:80", emailVerificationService)
    }

    @Test
    fun `requestMagicLink sends email successfully`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        val result = magicLinkService.requestMagicLink("test@example.com", "en")
        assertTrue(result.isSuccess)

        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertEquals(1, sentEmails.size)
        assertTrue(sentEmails.first().subject.contains("Login link"))
        assertTrue(sentEmails.first().body.contains("magic-link-login"))
    }

    @Test
    fun `requestMagicLink returns success for non-existent email`() = kotlinx.coroutines.runBlocking {
        val result = magicLinkService.requestMagicLink("nonexistent@example.com", "en")
        assertTrue(result.isSuccess)
        assertEquals(0, mockNotificationAdapter.getSentEmails().size)
    }

    @Test
    fun `requestMagicLink respects rate limit`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        repeat(5) {
            magicLinkService.requestMagicLink("test@example.com", "en")
        }

        val result = magicLinkService.requestMagicLink("test@example.com", "en")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Maximum") == true)
    }

    @Test
    fun `requestMagicLink cleans up expired tokens`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        val expiredToken = "expired-token-test"
        transaction {
            MagicLinkTokens.insert {
                it[MagicLinkTokens.userId] = userId
                it[MagicLinkTokens.token] = expiredToken
                it[MagicLinkTokens.expiresAt] = clock.now().toEpochMilliseconds() - 1000
                it[MagicLinkTokens.createdAt] = clock.now().toEpochMilliseconds() - 400000
                it[MagicLinkTokens.usedAt] = null
            }
        }

        magicLinkService.requestMagicLink("test@example.com", "en")

        val tokensCount = transaction {
            MagicLinkTokens.selectAll()
                .where { MagicLinkTokens.userId eq userId }
                .count()
        }
        assertEquals(1, tokensCount)
        assertNull(transaction {
            MagicLinkTokens.selectAll()
                .where { MagicLinkTokens.token eq expiredToken }
                .firstOrNull()
        })
    }

    @Test
    fun `requestMagicLink works for Spanish language`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        val result = magicLinkService.requestMagicLink("test@example.com", "es")
        assertTrue(result.isSuccess)

        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertTrue(sentEmails.first().subject.contains("Enlace de inicio"))
    }

    @Test
    fun `requestMagicLink works for French language`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        val result = magicLinkService.requestMagicLink("test@example.com", "fr")
        assertTrue(result.isSuccess)

        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertTrue(sentEmails.first().subject.contains("Lien de connexion"))
    }

    @Test
    fun `requestMagicLink works for Portuguese language`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        val result = magicLinkService.requestMagicLink("test@example.com", "pt")
        assertTrue(result.isSuccess)

        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertTrue(sentEmails.first().subject.contains("Link de login"))
    }

    @Test
    fun `requestMagicLink works for Chinese language`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        val result = magicLinkService.requestMagicLink("test@example.com", "zh")
        assertTrue(result.isSuccess)

        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertTrue(sentEmails.first().subject.contains("登录链接"))
    }

    @Test
    fun `requestMagicLink fails when a verification email was already sent recently`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("unverified@example.com", "Unverified User", verified = false)

        transaction {
            EmailVerificationTokens.insert {
                it[EmailVerificationTokens.userId] = userId
                it[EmailVerificationTokens.token] = "existing-verification-token"
                it[EmailVerificationTokens.expiresAt] = clock.now().toEpochMilliseconds() + 3_600_000
                it[EmailVerificationTokens.createdAt] = clock.now().toEpochMilliseconds()
            }
        }

        val result = magicLinkService.requestMagicLink("unverified@example.com", "en")

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message
        assertNotNull(message)
        assertTrue(message.contains("already sent") || message.contains("wait for the link"))
    }

    @Test
    fun `requestMagicLink fails when verification email daily limit already reached`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("unverified@example.com", "Unverified User", verified = false)

        repeat(3) {
            emailVerificationService.generateAndSendVerificationEmail(userId, "unverified@example.com", "Unverified User", "en")
        }

        val result = magicLinkService.requestMagicLink("unverified@example.com", "en")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Maximum verification emails") == true)
    }

    @Test
    fun `requestMagicLink fails when resending the verification email cannot be delivered`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("unverified@example.com", "Unverified User", verified = false)
        mockNotificationAdapter.setFailMode(true)

        val result = magicLinkService.requestMagicLink("unverified@example.com", "en")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to send verification email") == true)
    }

    @Test
    fun `requestMagicLink succeeds but reports failure when the magic link email cannot be delivered`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        mockNotificationAdapter.setFailMode(true)

        val result = magicLinkService.requestMagicLink("test@example.com", "en")

        assertTrue(result.isSuccess)
        assertFalse(result.getOrDefault(true))
        assertEquals(0, mockNotificationAdapter.getSentEmails().size)
    }

    @Test
    fun `releaseMagicLinkToken is a no-op`() {
        magicLinkService.releaseMagicLinkToken("any-token")
    }

    @Test
    fun `verifyAndConsumeMagicLink returns user info for valid token`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val token = createMagicLinkToken(userId)

        val result = magicLinkService.verifyAndConsumeMagicLink(token)

        assertNotNull(result)
        assertEquals(userId, result.userId)
        assertEquals("test@example.com", result.username)
        assertEquals("Test User", result.displayName)
    }

    @Test
    fun `verifyAndConsumeMagicLink marks token as used`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val token = createMagicLinkToken(userId)

        magicLinkService.verifyAndConsumeMagicLink(token)

        val usedAt = transaction {
            MagicLinkTokens.selectAll()
                .where { MagicLinkTokens.token eq token }
                .firstOrNull()
                ?.get(MagicLinkTokens.usedAt)
        }
        assertNotNull(usedAt)
        Unit
    }

    @Test
    fun `verifyAndConsumeMagicLink returns null for invalid token`() = kotlinx.coroutines.runBlocking {
        val result = magicLinkService.verifyAndConsumeMagicLink("invalid-token-123")
        assertNull(result)
    }

    @Test
    fun `verifyAndConsumeMagicLink returns null for expired token`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val token = createExpiredMagicLinkToken(userId)

        val result = magicLinkService.verifyAndConsumeMagicLink(token)
        assertNull(result)
    }

    @Test
    fun `verifyAndConsumeMagicLink deletes expired token`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val token = createExpiredMagicLinkToken(userId)

        magicLinkService.verifyAndConsumeMagicLink(token)

        val tokenExists = transaction {
            MagicLinkTokens.selectAll()
                .where { MagicLinkTokens.token eq token }
                .firstOrNull()
        }
        assertNull(tokenExists)
    }

    @Test
    fun `verifyAndConsumeMagicLink returns null for already used token`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val token = createMagicLinkToken(userId, usedAt = clock.now().toEpochMilliseconds())

        val result = magicLinkService.verifyAndConsumeMagicLink(token)
        assertNull(result)
    }

    @Test
    fun `verifyAndConsumeMagicLink returns null for non-existent user`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val token = createMagicLinkToken(userId)

        transaction {
            MagicLinkTokens.deleteWhere { MagicLinkTokens.userId eq userId }
            Users.deleteWhere { Users.id eq userId }
        }

        val result = magicLinkService.verifyAndConsumeMagicLink(token)
        assertNull(result)
    }

    @Test
    fun `token is 43 characters (32 bytes base64url encoded)`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        magicLinkService.requestMagicLink("test@example.com", "en")

        val token = transaction {
            MagicLinkTokens.selectAll()
                .where { MagicLinkTokens.userId eq userId }
                .firstOrNull()
                ?.get(MagicLinkTokens.token)
        }

        assertNotNull(token)
        assertEquals(43, token.length)
    }

    @Test
    fun `requestMagicLink sends verification email for unverified user`() = kotlinx.coroutines.runBlocking {
        // Create an unverified user
        val userId = transaction {
            Users.insert {
                it[Users.username] = "unverified@example.com"
                it[Users.displayName] = "Unverified User"
                it[Users.language] = "en"
                it[Users.isEmailVerified] = false
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
            } get Users.id
        }

        val result = magicLinkService.requestMagicLink("unverified@example.com", "en")
        // Should fail with a message that email is not verified
        assertTrue(result.isFailure)
        val errorMessage = result.exceptionOrNull()?.message
        assertNotNull(errorMessage)
        assertTrue(errorMessage.contains("not verified") || errorMessage.contains("verification email"))

        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertEquals(1, sentEmails.size)
        assertTrue(sentEmails.first().subject.contains("Verify your email"))
    }

    @Test
    fun `requestMagicLink sends magic link for verified user`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("verified@example.com", "Verified User")

        val result = magicLinkService.requestMagicLink("verified@example.com", "en")
        assertTrue(result.isSuccess)

        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertEquals(1, sentEmails.size)
        assertTrue(sentEmails.first().subject.contains("Login link"))
    }

    private fun createTestUser(
        username: String,
        displayName: String,
        language: String = "en",
        verified: Boolean = true
    ): Int {
        return transaction {
            Users.insert {
                it[Users.username] = username
                it[Users.displayName] = displayName
                it[Users.language] = language
                it[Users.isEmailVerified] = verified
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
            } get Users.id
        }
    }

    private fun createMagicLinkToken(userId: Int, usedAt: Long? = null): String {
        val token = "valid-magic-link-token-${System.nanoTime()}"
        transaction {
            MagicLinkTokens.insert {
                it[MagicLinkTokens.userId] = userId
                it[MagicLinkTokens.token] = token
                it[MagicLinkTokens.expiresAt] = clock.now().toEpochMilliseconds() + 300000
                it[MagicLinkTokens.createdAt] = clock.now().toEpochMilliseconds()
                it[MagicLinkTokens.usedAt] = usedAt
            }
        }
        return token
    }

    private fun createExpiredMagicLinkToken(userId: Int): String {
        val token = "expired-magic-link-token-${System.nanoTime()}"
        transaction {
            MagicLinkTokens.insert {
                it[MagicLinkTokens.userId] = userId
                it[MagicLinkTokens.token] = token
                it[MagicLinkTokens.expiresAt] = clock.now().toEpochMilliseconds() - 1000
                it[MagicLinkTokens.createdAt] = clock.now().toEpochMilliseconds() - 400000
                it[MagicLinkTokens.usedAt] = null
            }
        }
        return token
    }
}

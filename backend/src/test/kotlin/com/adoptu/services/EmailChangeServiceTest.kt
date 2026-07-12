package com.adoptu.services

import com.adoptu.adapters.db.EmailChangeTokens
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalTime::class)
class EmailChangeServiceTest {

    private val clock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))
    private lateinit var emailChangeService: EmailChangeService
    private lateinit var userRepository: UserRepository
    private lateinit var mockNotificationAdapter: MockNotificationAdapter

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        userRepository = UserRepository(clock)
        mockNotificationAdapter = MockNotificationAdapter()
        emailChangeService = EmailChangeService(userRepository, mockNotificationAdapter, clock, "http://localhost:80")
    }

    @Test
    fun `requestEmailChange sends verification emails to both old and new email`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("old@example.com", "Test User")

        val result = emailChangeService.requestEmailChange(userId, "new@example.com", "en")
        assertTrue(result.isSuccess)

        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertEquals(2, sentEmails.size)
        
        val newEmailMsg = sentEmails.find { it.to == "new@example.com" }
        assertNotNull(newEmailMsg)
        assertTrue(newEmailMsg.subject.contains("Verify your new email"))
        
        val oldEmailMsg = sentEmails.find { it.to == "old@example.com" }
        assertNotNull(oldEmailMsg)
        assertTrue(oldEmailMsg.subject.contains("Email change requested"))
    }

    @Test
    fun `requestEmailChange fails when new email already in use`() = kotlinx.coroutines.runBlocking {
        val userId1 = createTestUser("user1@example.com", "User One")
        createTestUser("user2@example.com", "User Two")

        val result = emailChangeService.requestEmailChange(userId1, "user2@example.com", "en")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("already in use") == true)
    }

    @Test
    fun `requestEmailChange fails when new email is same as current`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        val result = emailChangeService.requestEmailChange(userId, "test@example.com", "en")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("same as current") == true)
    }

    @Test
    fun `requestEmailChange fails when new email is same as current with different case`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("Test@Example.com", "Test User")

        val result = emailChangeService.requestEmailChange(userId, "test@example.com", "en")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("same as current") == true)
    }

    @Test
    fun `requestEmailChange fails for non-existent user`() = kotlinx.coroutines.runBlocking {
        val result = emailChangeService.requestEmailChange(99999, "new@example.com", "en")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("not found") == true)
    }

    @Test
    fun `requestEmailChange deletes old tokens`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        emailChangeService.requestEmailChange(userId, "new1@example.com", "en")
        clock.advanceMillis(1000)
        emailChangeService.requestEmailChange(userId, "new2@example.com", "en")

        val tokensCount = transaction {
            EmailChangeTokens.selectAll()
                .where { EmailChangeTokens.userId eq userId }
                .count()
        }
        assertEquals(1, tokensCount)

        val currentToken = transaction {
            EmailChangeTokens.selectAll()
                .where { EmailChangeTokens.userId eq userId }
                .firstOrNull()
                ?.get(EmailChangeTokens.newEmail)
        }
        assertEquals("new2@example.com", currentToken)
    }

    @Test
    fun `requestEmailChange works for Spanish language`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("old@example.com", "Test User")

        val result = emailChangeService.requestEmailChange(userId, "new@example.com", "es")
        assertTrue(result.isSuccess)

        val sentEmails = mockNotificationAdapter.getSentEmails()
        val newEmailMsg = sentEmails.find { it.to == "new@example.com" }
        assertTrue(newEmailMsg?.subject?.contains("Verificar nuevo correo") == true)
    }

    @Test
    fun `requestEmailChange works for French language`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("old@example.com", "Test User")

        val result = emailChangeService.requestEmailChange(userId, "new@example.com", "fr")
        assertTrue(result.isSuccess)

        val sentEmails = mockNotificationAdapter.getSentEmails()
        val newEmailMsg = sentEmails.find { it.to == "new@example.com" }
        assertTrue(newEmailMsg?.subject?.contains("Vérifier la nouvelle adresse") == true)
    }

    @Test
    fun `requestEmailChange works for Portuguese language`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("old@example.com", "Test User")

        val result = emailChangeService.requestEmailChange(userId, "new@example.com", "pt")
        assertTrue(result.isSuccess)

        val sentEmails = mockNotificationAdapter.getSentEmails()
        val newEmailMsg = sentEmails.find { it.to == "new@example.com" }
        assertTrue(newEmailMsg?.subject?.contains("Verificar novo email") == true)
    }

    @Test
    fun `requestEmailChange works for Chinese language`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("old@example.com", "Test User")

        val result = emailChangeService.requestEmailChange(userId, "new@example.com", "zh")
        assertTrue(result.isSuccess)

        val sentEmails = mockNotificationAdapter.getSentEmails()
        val newEmailMsg = sentEmails.find { it.to == "new@example.com" }
        assertTrue(newEmailMsg?.subject?.contains("验证新电子邮件") == true)
    }

    @Test
    fun `requestEmailChange returns false result when new-email delivery fails`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("old@example.com", "Test User")
        mockNotificationAdapter.setFailMode(true)

        val result = emailChangeService.requestEmailChange(userId, "new@example.com", "en")
        assertTrue(result.isSuccess)
        assertFalse(result.getOrDefault(true))
    }

    @Test
    fun `verifyEmailChange updates user email`() = runBlocking {
        val userId = createTestUser("old@example.com", "Test User")
        val token = createEmailChangeToken(userId, "new@example.com")

        val result = emailChangeService.verifyEmailChange(token)
        assertTrue(result)

        val updatedUser = userRepository.getById(userId)
        assertEquals("new@example.com", updatedUser?.username)
    }

    @Test
    fun `verifyEmailChange deletes token after use`() = runBlocking {
        val userId = createTestUser("old@example.com", "Test User")
        val token = createEmailChangeToken(userId, "new@example.com")

        emailChangeService.verifyEmailChange(token)

        val tokensRemaining = transaction {
            EmailChangeTokens.selectAll()
                .where { EmailChangeTokens.userId eq userId }
                .count()
        }
        assertEquals(0, tokensRemaining)
    }

    @Test
    fun `verifyEmailChange returns false for invalid token`() = runBlocking {
        val result = emailChangeService.verifyEmailChange("invalid-token")
        assertFalse(result)
    }

    @Test
    fun `verifyEmailChange returns false for expired token`() = runBlocking {
        val userId = createTestUser("old@example.com", "Test User")
        val token = createExpiredEmailChangeToken(userId, "new@example.com")

        val result = emailChangeService.verifyEmailChange(token)
        assertFalse(result)
    }

    @Test
    fun `verifyEmailChange returns false when token not found`() = runBlocking {
        val result = emailChangeService.verifyEmailChange("nonexistent-token-123")
        assertFalse(result)
    }

    @Test
    fun `verifyEmailChange does not update email when token invalid`() = runBlocking {
        val userId = createTestUser("old@example.com", "Test User")

        emailChangeService.verifyEmailChange("invalid-token")

        val updatedUser = userRepository.getById(userId)
        assertEquals("old@example.com", updatedUser?.username)
    }

    @Test
    fun `token contains correct new email`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("old@example.com", "Test User")

        emailChangeService.requestEmailChange(userId, "specific@example.com", "en")

        val storedEmail = transaction {
            EmailChangeTokens.selectAll()
                .where { EmailChangeTokens.userId eq userId }
                .firstOrNull()
                ?.get(EmailChangeTokens.newEmail)
        }
        assertEquals("specific@example.com", storedEmail)
    }

    @Test
    fun `token expires in 1 hour`() = kotlinx.coroutines.runBlocking {
        val userId = createTestUser("old@example.com", "Test User")

        emailChangeService.requestEmailChange(userId, "new@example.com", "en")

        val expiresAt = transaction {
            EmailChangeTokens.selectAll()
                .where { EmailChangeTokens.userId eq userId }
                .firstOrNull()
                ?.get(EmailChangeTokens.expiresAt)
        }
        
        val expectedExpiry = clock.now().toEpochMilliseconds() + 3600000
        assertEquals(expectedExpiry, expiresAt)
    }

    private fun createTestUser(
        username: String,
        displayName: String,
        language: String = "en"
    ): Int {
        return transaction {
            Users.insert {
                it[Users.username] = username
                it[Users.displayName] = displayName
                it[Users.language] = language
                it[Users.isEmailVerified] = true
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
            } get Users.id
        }
    }

    private fun createEmailChangeToken(userId: Int, newEmail: String): String {
        val token = "email-change-token-${System.nanoTime()}"
        transaction {
            EmailChangeTokens.insert {
                it[EmailChangeTokens.userId] = userId
                it[EmailChangeTokens.newEmail] = newEmail
                it[EmailChangeTokens.token] = token
                it[EmailChangeTokens.expiresAt] = clock.now().toEpochMilliseconds() + 3600000
                it[EmailChangeTokens.createdAt] = clock.now().toEpochMilliseconds()
            }
        }
        return token
    }

    private fun createExpiredEmailChangeToken(userId: Int, newEmail: String): String {
        val token = "expired-email-change-token-${System.nanoTime()}"
        transaction {
            EmailChangeTokens.insert {
                it[EmailChangeTokens.userId] = userId
                it[EmailChangeTokens.newEmail] = newEmail
                it[EmailChangeTokens.token] = token
                it[EmailChangeTokens.expiresAt] = clock.now().toEpochMilliseconds() - 1000
                it[EmailChangeTokens.createdAt] = clock.now().toEpochMilliseconds() - 4000000
            }
        }
        return token
    }
}

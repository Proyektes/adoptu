package com.adoptu.services

import com.adoptu.adapters.db.PasswordResetTokens
import com.adoptu.adapters.db.UserPasswords
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import com.adoptu.services.crypto.CryptoService
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class PasswordServiceTest {

    private val clock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))
    private lateinit var passwordService: PasswordService
    private lateinit var userRepository: UserRepository
    private lateinit var mockNotificationAdapter: MockNotificationAdapter

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        userRepository = UserRepository(clock)
        mockNotificationAdapter = MockNotificationAdapter()
        passwordService = PasswordService(userRepository, mockNotificationAdapter, clock, "http://localhost:80")
        CryptoService.initialize()
    }

    @Test
    fun `hasPassword returns false when no password set`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        assertFalse(passwordService.hasPassword(userId))
    }

    @Test
    fun `setPassword stores hashed password`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val encryptedPassword = encryptPassword("SecurePassword123!")

        val result = passwordService.setPassword(userId, encryptedPassword)
        assertTrue(result)
        assertTrue(passwordService.hasPassword(userId))
    }

    @Test
    fun `invalidatePassword removes an existing password`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val encryptedPassword = encryptPassword("SecurePassword123!")
        passwordService.setPassword(userId, encryptedPassword)
        assertTrue(passwordService.hasPassword(userId))

        passwordService.invalidatePassword(userId)

        assertFalse(passwordService.hasPassword(userId))
    }

    @Test
    fun `invalidatePassword is a no-op when no password is set`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")

        passwordService.invalidatePassword(userId)

        assertFalse(passwordService.hasPassword(userId))
    }

    @Test
    fun `setPassword rejects invalid password - too short`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val encryptedPassword = encryptPassword("Short1!")

        val result = passwordService.setPassword(userId, encryptedPassword)
        assertFalse(result)
        assertFalse(passwordService.hasPassword(userId))
    }

    @Test
    fun `setPassword rejects invalid password - too long`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val encryptedPassword = encryptPassword("a".repeat(129))

        val result = passwordService.setPassword(userId, encryptedPassword)
        assertFalse(result)
        assertFalse(passwordService.hasPassword(userId))
    }

    @Test
    fun `setPassword rejects tampered encrypted data`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val encryptedPassword = "tampered-encrypted-data"

        val result = passwordService.setPassword(userId, encryptedPassword)
        assertFalse(result)
        assertFalse(passwordService.hasPassword(userId))
    }

    @Test
    fun `verifyPassword returns true for correct password`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val password = "SecurePassword123!"
        val encryptedPassword = encryptPassword(password)

        passwordService.setPassword(userId, encryptedPassword)
        val result = passwordService.verifyPassword(userId, encryptedPassword)
        assertTrue(result)
    }

    @Test
    fun `verifyPassword returns false for wrong password`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val encryptedCorrect = encryptPassword("CorrectPassword123!")
        val encryptedWrong = encryptPassword("WrongPassword123!")

        passwordService.setPassword(userId, encryptedCorrect)
        val result = passwordService.verifyPassword(userId, encryptedWrong)
        assertFalse(result)
    }

    @Test
    fun `verifyPassword returns false when no password set`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val encryptedPassword = encryptPassword("SomePassword123!")

        val result = passwordService.verifyPassword(userId, encryptedPassword)
        assertFalse(result)
    }

    @Test
    fun `changePassword updates password successfully`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val oldPassword = encryptPassword("OldPassword123!")
        val newPassword = encryptPassword("NewPassword123!")

        passwordService.setPassword(userId, oldPassword)
        val result = passwordService.changePassword(userId, oldPassword, newPassword)
        assertTrue(result)
        assertTrue(passwordService.verifyPassword(userId, newPassword))
        assertFalse(passwordService.verifyPassword(userId, oldPassword))
    }

    @Test
    fun `changePassword fails with wrong current password`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val oldPassword = encryptPassword("OldPassword123!")
        val newPassword = encryptPassword("NewPassword123!")
        val wrongPassword = encryptPassword("WrongPassword123!")

        passwordService.setPassword(userId, oldPassword)
        val result = passwordService.changePassword(userId, wrongPassword, newPassword)
        assertFalse(result)
        assertTrue(passwordService.verifyPassword(userId, oldPassword))
        assertFalse(passwordService.verifyPassword(userId, newPassword))
    }

    @Test
    fun `changePassword fails when new password too short`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val oldPassword = encryptPassword("OldPassword123!")
        val newPassword = encryptPassword("Short1!")

        passwordService.setPassword(userId, oldPassword)
        val result = passwordService.changePassword(userId, oldPassword, newPassword)
        assertFalse(result)
        assertTrue(passwordService.verifyPassword(userId, oldPassword))
    }

    @Test
    fun `requestPasswordReset sends email successfully`() = runBlocking {
        createTestUser("test@example.com", "Test User")

        val result = passwordService.requestPasswordReset("test@example.com", "en")
        assertTrue(result.isSuccess)

        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertEquals(1, sentEmails.size)
        assertTrue(sentEmails.first().subject.contains("Reset your password"))
    }

    @Test
    fun `requestPasswordReset returns success for non-existent email`() = runBlocking {
        val result = passwordService.requestPasswordReset("nonexistent@example.com", "en")
        assertTrue(result.isSuccess)
        assertEquals(0, mockNotificationAdapter.getSentEmails().size)
    }

    @Test
    fun `requestPasswordReset respects rate limit`() = runBlocking {
        createTestUser("test@example.com", "Test User")

        repeat(3) {
            passwordService.requestPasswordReset("test@example.com", "en")
        }

        val result = passwordService.requestPasswordReset("test@example.com", "en")
        assertTrue(result.isFailure)
        assertEquals(result.exceptionOrNull()?.message?.contains("Maximum"), true)
    }

    @Test
    fun `requestPasswordReset works for Spanish language`() = runBlocking {
        createTestUser("test@example.com", "Test User", "es")

        val result = passwordService.requestPasswordReset("test@example.com", "es")
        assertTrue(result.isSuccess)

        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertTrue(sentEmails.first().subject.contains("Restablecer"))
    }

    @Test
    fun `resetPassword sets new password`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val token = createPasswordResetToken(userId)
        val newPassword = encryptPassword("NewResetPassword123!")

        val result = passwordService.resetPassword(token, newPassword)
        assertTrue(result)
        assertTrue(passwordService.hasPassword(userId))
        assertTrue(passwordService.verifyPassword(userId, newPassword))
    }

    @Test
    fun `resetPassword fails with invalid token`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val newPassword = encryptPassword("NewResetPassword123!")

        val result = passwordService.resetPassword("invalid-token", newPassword)
        assertFalse(result)
        assertFalse(passwordService.hasPassword(userId))
    }

    @Test
    fun `resetPassword fails with expired token`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val token = createExpiredPasswordResetToken(userId)
        val newPassword = encryptPassword("NewResetPassword123!")

        val result = passwordService.resetPassword(token, newPassword)
        assertFalse(result)
    }

    @Test
    fun `resetPassword deletes token after use`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val token = createPasswordResetToken(userId)
        val newPassword = encryptPassword("NewResetPassword123!")

        passwordService.resetPassword(token, newPassword)

        val tokensRemaining = transaction {
            PasswordResetTokens.selectAll()
                .where { PasswordResetTokens.userId eq userId }
                .count()
        }
        assertEquals(0, tokensRemaining)
    }

    @Test
    fun `password hashing uses Argon2id`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        val encryptedPassword = encryptPassword("TestPassword123!")

        passwordService.setPassword(userId, encryptedPassword)

        val storedHash = transaction {
            UserPasswords.selectAll()
                .where { UserPasswords.userId eq userId }
                .firstOrNull()
                ?.get(UserPasswords.passwordHash)
        }

        assertNotNull(storedHash)
        assertTrue(storedHash.startsWith($$"$argon2"))
    }

    private fun encryptPassword(password: String): String {
        val publicKey = CryptoService.getPublicKey()
        return CryptoService.encrypt(password, publicKey) ?: throw IllegalStateException("Encryption failed")
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

    private fun createPasswordResetToken(userId: Int): String {
        val token = "reset-token-123"
        transaction {
            PasswordResetTokens.insert {
                it[PasswordResetTokens.userId] = userId
                it[PasswordResetTokens.token] = token
                it[PasswordResetTokens.expiresAt] = clock.now().toEpochMilliseconds() + 900000
                it[PasswordResetTokens.createdAt] = clock.now().toEpochMilliseconds()
            }
        }
        return token
    }

    private fun createExpiredPasswordResetToken(userId: Int): String {
        val token = "expired-token-123"
        transaction {
            PasswordResetTokens.insert {
                it[PasswordResetTokens.userId] = userId
                it[PasswordResetTokens.token] = token
                it[PasswordResetTokens.expiresAt] = clock.now().toEpochMilliseconds() - 1000
                it[PasswordResetTokens.createdAt] = clock.now().toEpochMilliseconds() - 1000000
            }
        }
        return token
    }
}

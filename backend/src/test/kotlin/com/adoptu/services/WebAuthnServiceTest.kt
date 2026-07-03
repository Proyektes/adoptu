package com.adoptu.services

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.WebAuthnCredentials
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.UserRole
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import com.adoptu.services.auth.WebAuthnService
import com.adoptu.services.crypto.CryptoService
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.SecureRandom
import kotlin.test.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import com.webauthn4j.data.PublicKeyCredentialCreationOptions
import com.webauthn4j.data.PublicKeyCredentialRequestOptions

@OptIn(ExperimentalTime::class)
class WebAuthnServiceTest {

    private val clock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))
    private lateinit var userRepository: UserRepository
    private lateinit var passwordService: PasswordService
    private lateinit var emailVerificationService: EmailVerificationService
    private lateinit var magicLinkService: MagicLinkService
    private lateinit var webAuthnService: WebAuthnService
    private lateinit var mockNotificationAdapter: MockNotificationAdapter
    private val adminEmail = "admin@test.com"
    private val rpId = "localhost"
    private val rpName = "Adopt-U Test"
    private val origins = listOf("http://localhost:80")

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        
        userRepository = UserRepository(clock)
        mockNotificationAdapter = MockNotificationAdapter()
        passwordService = PasswordService(userRepository, mockNotificationAdapter, clock, "http://localhost:80")
        emailVerificationService = EmailVerificationService(userRepository, mockNotificationAdapter, clock, "http://localhost:80")
        magicLinkService = MagicLinkService(userRepository, mockNotificationAdapter, clock, "http://localhost:80", emailVerificationService)
        webAuthnService = WebAuthnService(
            clock,
            emailVerificationService,
            userService(),
            passwordService,
            magicLinkService,
            adminEmail,
            rpId,
            rpName,
            origins
        )
        CryptoService.initialize()
    }

    private fun userService(): UserService = UserService(userRepository)

    @Test
    fun `hasPasskey returns false when user has no credentials`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        assertFalse(webAuthnService.hasPasskey(userId))
    }

    @Test
    fun `hasPasskey returns true when user has credentials`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        createTestCredential(userId)
        assertTrue(webAuthnService.hasPasskey(userId))
    }

    @Test
    fun `hasPasskey returns false for non-existent user`() = runBlocking {
        assertFalse(webAuthnService.hasPasskey(99999))
    }

    @Test
    fun `registerWithPassword creates new user with password`() = runBlocking {
        val email = "newuser@example.com"
        val displayName = "New User"
        val roles = setOf(UserRole.ADOPTER)
        val encryptedPassword = encryptPassword("SecurePassword123!")

        val result = webAuthnService.registerWithPassword(email, displayName, roles, encryptedPassword)

        assertNotNull(result)
        assertTrue(result.userId > 0)
        
        val user = transaction {
            Users.selectAll().where { Users.username eq email }.firstOrNull()
        }
        assertNotNull(user)
        assertEquals(displayName, user[Users.displayName])
        
        assertTrue(passwordService.hasPassword(result.userId))
    }

    @Test
    fun `registerWithPassword assigns roles to new user`() = runBlocking {
        val email = "roles@example.com"
        val displayName = "Role User"
        val roles = setOf(UserRole.ADOPTER, UserRole.RESCUER)
        val encryptedPassword = encryptPassword("SecurePassword123!")

        val result = webAuthnService.registerWithPassword(email, displayName, roles, encryptedPassword)

        assertNotNull(result)
        
        val userRoles = transaction {
            UserActiveRoles.selectAll()
                .where { UserActiveRoles.userId eq result.userId }
                .map { it[UserActiveRoles.role] }
                .toSet()
        }
        
        assertTrue(userRoles.contains("ADOPTER"))
        assertTrue(userRoles.contains("RESCUER"))
    }

    @Test
    fun `registerWithPassword sends verification email`() = runBlocking {
        val email = "email@example.com"
        val displayName = "Email User"
        val roles = setOf(UserRole.ADOPTER)
        val encryptedPassword = encryptPassword("SecurePassword123!")

        webAuthnService.registerWithPassword(email, displayName, roles, encryptedPassword)

        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertEquals(1, sentEmails.size)
        assertTrue(sentEmails.first().to.contains(email))
    }

    @Test
    fun `registerWithPassword fails with invalid password`() = runBlocking {
        val email = "invalid@example.com"
        val displayName = "Invalid User"
        val roles = setOf(UserRole.ADOPTER)
        val encryptedPassword = encryptPassword("weak") 

        val result = webAuthnService.registerWithPassword(email, displayName, roles, encryptedPassword)
        assertNull(result)
    }

    @Test
    fun `registerWithPassword returns null for tampered encrypted data`() = runBlocking {
        val email = "tampered@example.com"
        val displayName = "Tampered User"
        val roles = setOf(UserRole.ADOPTER)

        val result = webAuthnService.registerWithPassword(email, displayName, roles, "tampered-data")
        assertNull(result)
    }

    @Test
    fun `registerWithPassword grants ADMIN role for admin email`() = runBlocking {
        val email = "admin@test.com"
        val displayName = "Admin User"
        val roles = setOf(UserRole.ADOPTER)
        val encryptedPassword = encryptPassword("AdminPassword123!")

        val result = webAuthnService.registerWithPassword(email, displayName, roles, encryptedPassword)

        assertNotNull(result)
        
        val userRoles = transaction {
            UserActiveRoles.selectAll()
                .where { UserActiveRoles.userId eq result.userId }
                .map { it[UserActiveRoles.role] }
                .toSet()
        }
        
        assertTrue(userRoles.contains("ADMIN"))
    }

    @Test
    fun `registerWithPassword returns emailSent true when email sent`() = runBlocking {
        val email = "emailsent@example.com"
        val displayName = "Email Sent User"
        val roles = setOf(UserRole.ADOPTER)
        val encryptedPassword = encryptPassword("SecurePassword123!")

        val result = webAuthnService.registerWithPassword(email, displayName, roles, encryptedPassword)

        assertNotNull(result)
        assertTrue(result.emailSent)
    }

    @Test
    fun `generateRegistrationOptionsForUser creates valid options`() {
        val userId = createTestUser("user@example.com", "Test User")

        val options = webAuthnService.generateRegistrationOptionsForUser(userId, "user@example.com", "Test User")

        assertNotNull(options)
        assertEquals(rpId, options.publicKey.rp.id)
        assertEquals(rpName, options.publicKey.rp.name)
        assertEquals("user@example.com", options.publicKey.user.name)
        assertEquals("Test User", options.publicKey.user.displayName)
        assertNotNull(options.publicKey.challenge)
        assertTrue(options.publicKey.pubKeyCredParams.isNotEmpty())
    }

    @Test
    fun `registerAdditionalPasskey returns false when no challenge stored`() = runBlocking {
        val userId = createTestUser("nopasskey@example.com", "No Passkey User")

        val result = webAuthnService.registerAdditionalPasskey(userId, "invalid-response")
        assertFalse(result)
    }

    @Test
    fun `registerAdditionalPasskey returns false for invalid registration response`() = runBlocking {
        val userId = createTestUser("invalid@example.com", "Invalid User")
        webAuthnService.generateRegistrationOptionsForUser(userId, "invalid@example.com", "Invalid User")

        val result = webAuthnService.registerAdditionalPasskey(userId, "completely-invalid-json")
        assertFalse(result)
    }

    @Test
    fun `registerAdditionalPasskey creates credential for valid response`() {
        val userId = createTestUser("additional@example.com", "Additional User")
        val email = "additional@example.com"
        val displayName = "Additional User"
        
        webAuthnService.generateRegistrationOptionsForUser(userId, email, displayName)
        
        val initialCredentialCount = transaction {
            WebAuthnCredentials.selectAll()
                .where { WebAuthnCredentials.userId eq userId }
                .count()
        }
        
        assertEquals(0, initialCredentialCount)
    }

    private fun encryptPassword(password: String): String {
        val publicKey = CryptoService.getPublicKey()
        return CryptoService.encrypt(password, publicKey) 
            ?: throw IllegalStateException("Encryption failed")
    }

    private fun createTestUser(username: String, displayName: String): Int {
        return transaction {
            Users.insert {
                it[Users.username] = username
                it[Users.displayName] = displayName
                it[Users.language] = "en"
                it[Users.isEmailVerified] = true
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
            } get Users.id
        }
    }

    private fun createTestCredential(userId: Int): Int {
        val credentialId = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val aaguid = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val publicKey = ByteArray(65).also { SecureRandom().nextBytes(it) }
        
        return transaction {
            WebAuthnCredentials.insert {
                it[WebAuthnCredentials.userId] = userId
                it[WebAuthnCredentials.credentialId] = java.util.Base64.getEncoder().encodeToString(credentialId)
                it[WebAuthnCredentials.attestedCredentialDataBase64] = java.util.Base64.getEncoder().encodeToString(aaguid + publicKey)
                it[WebAuthnCredentials.signCount] = 0
                it[WebAuthnCredentials.transports] = null
                it[WebAuthnCredentials.createdAt] = clock.now().toEpochMilliseconds()
            } get WebAuthnCredentials.id
        }
    }
}

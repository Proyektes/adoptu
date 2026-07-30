package com.adoptu.services.auth

import com.adoptu.adapters.db.WebAuthnCredentials
import com.adoptu.dto.input.UserDto
import com.adoptu.mocks.TestDatabase
import com.adoptu.services.PasswordService
import com.adoptu.services.UserService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class WebAuthnServiceTest {

    private lateinit var userService: UserService
    private lateinit var passwordService: PasswordService
    private lateinit var webAuthnService: WebAuthnService

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()

        userService = mockk(relaxed = true)
        passwordService = mockk(relaxed = true)
        webAuthnService = WebAuthnService(
            clock = com.adoptu.mocks.TestClock(kotlin.time.Instant.parse("2024-01-15T10:00:00Z")),
            emailVerificationService = mockk(relaxed = true),
            userService = userService,
            passwordService = passwordService,
            magicLinkService = mockk(relaxed = true),
            adminEmail = "admin@adopt-u.com",
            rpId = "localhost",
            rpName = "Adopt-U Pet Adoption",
            origins = listOf("http://localhost:80")
        )
    }

    @Nested
    inner class GenerateRegistrationOptions {
        @Test
        fun `generates valid registration options with correct structure`() {
            val result = webAuthnService.generateRegistrationOptions("test@example.com", "Test User")

            assertEquals("localhost", result.publicKey.rp.id)
            assertEquals("Adopt-U Pet Adoption", result.publicKey.rp.name)
            assertEquals("test@example.com", result.publicKey.user.name)
            assertEquals("Test User", result.publicKey.user.displayName)
            assertTrue(result.publicKey.challenge.isNotEmpty())
            assertEquals(2, result.publicKey.pubKeyCredParams.size)
        }

        @Test
        fun `includes ES256 and RS256 algorithms`() {
            val result = webAuthnService.generateRegistrationOptions("test@example.com", "Test User")

            val es256 = result.publicKey.pubKeyCredParams.find { it.alg == -7 }
            val rs256 = result.publicKey.pubKeyCredParams.find { it.alg == -257 }

            assertTrue(es256?.type == "public-key")
            assertTrue(rs256?.type == "public-key")
        }
    }

    @Nested
    inner class GenerateAssertionOptions {
        @Test
        fun `generates valid assertion options`() {
            val result = webAuthnService.generateAssertionOptions()

            assertEquals("localhost", result.rpId)
            assertEquals("required", result.userVerification)
            assertTrue(result.challenge.isNotEmpty())
        }
    }

    @Nested
    inner class ForcePasswordReset {
        @BeforeEach
        fun setupDb() {
            TestDatabase.initH2()
            TestDatabase.clearAllData()
        }

        @Test
        fun `deletes passkeys, invalidates the password, and re-sends the reset email`() = runBlocking {
            val user = UserDto(id = 42, username = "target@example.com", email = "target@example.com", displayName = "Target User", language = "es")
            coEvery { userService.getById(42) } returns user
            coEvery { passwordService.requestPasswordReset("target@example.com", "es") } returns Result.success(true)

            transaction {
                com.adoptu.adapters.db.Users.insert {
                    it[com.adoptu.adapters.db.Users.id] = 42
                    it[com.adoptu.adapters.db.Users.username] = "target@example.com"
                    it[com.adoptu.adapters.db.Users.displayName] = "Target User"
                    it[com.adoptu.adapters.db.Users.createdAt] = 0L
                }
                WebAuthnCredentials.insert {
                    it[WebAuthnCredentials.userId] = 42
                    it[WebAuthnCredentials.credentialId] = "cred-1"
                    it[WebAuthnCredentials.attestedCredentialDataBase64] = "dGVzdA=="
                    it[WebAuthnCredentials.signCount] = 0
                    it[WebAuthnCredentials.transports] = null
                    it[WebAuthnCredentials.createdAt] = 0L
                }
            }

            val result = webAuthnService.forcePasswordReset(42)

            assertTrue(result)
            coVerify { passwordService.invalidatePassword(42) }
            coVerify { passwordService.requestPasswordReset("target@example.com", "es") }
            transaction {
                assertTrue(WebAuthnCredentials.selectAll().where { WebAuthnCredentials.userId eq 42 }.empty())
            }
        }

        @Test
        fun `returns false when the user does not exist`() = runBlocking {
            coEvery { userService.getById(99) } returns null

            val result = webAuthnService.forcePasswordReset(99)

            assertFalse(result)
            coVerify(exactly = 0) { passwordService.invalidatePassword(any()) }
        }
    }
}

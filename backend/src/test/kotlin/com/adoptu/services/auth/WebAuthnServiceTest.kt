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
            userService = userService,
            passwordService = passwordService,
        )
    }

    @Nested
    inner class VerifyTokenAndGetLanguage {
        @Test
        fun `delegates to userService and returns its result verbatim`() = runBlocking {
            coEvery { userService.verifyTokenAndGetLanguage("legacy-token") } returns (true to "es")

            val result = webAuthnService.verifyTokenAndGetLanguage("legacy-token")

            assertEquals(true to "es", result)
            coVerify { userService.verifyTokenAndGetLanguage("legacy-token") }
        }

        @Test
        fun `returns false with default language for an invalid token`() = runBlocking {
            coEvery { userService.verifyTokenAndGetLanguage("bad-token") } returns (false to "en")

            val result = webAuthnService.verifyTokenAndGetLanguage("bad-token")

            assertFalse(result.first)
            assertEquals("en", result.second)
        }
    }

    @Nested
    inner class ForcePasswordReset {
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

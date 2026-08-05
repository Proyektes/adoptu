package com.adoptu.routes

import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.config.AppConfig
import com.adoptu.dto.output.VerificationResponse
import com.adoptu.mocks.MockImageStorage
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestDatabase
import com.adoptu.services.EmailVerificationService
import com.adoptu.testsupport.TestHttp
import com.adoptu.testsupport.TestServer
import com.adoptu.web.JsonSupport
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.dsl.module
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class EmailVerificationRoutesE2ETest {

    private val clock = Clock.System
    private lateinit var mockNotificationAdapter: MockNotificationAdapter

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        mockNotificationAdapter = MockNotificationAdapter()
    }

    private fun testModules(): List<Module> {
        val config = AppConfig.fromMap(mapOf("env" to "test", "admin.email" to "admin@test.com"))

        return listOf(module {
            single { config }
            single<Clock> { Clock.System }
            single<com.adoptu.ports.UserRepositoryPort> { UserRepository(get()) }
            single { com.adoptu.services.UserService(get(), get()) }
            single { com.universaliun.ratelimit.common.RateLimiter(com.universaliun.ratelimit.common.InMemoryRateLimitStateAdapter()) }
            single { EmailVerificationService(get(), get(), get(), "http://localhost:80", get()) }
            single { com.adoptu.services.PasswordService(get(), mockNotificationAdapter, get(), "http://localhost:80", get()) }
            single { com.adoptu.services.MagicLinkService(get(), mockNotificationAdapter, get(), "http://localhost:80", get(), get()) }
            single {
                com.adoptu.services.auth.WebAuthnService(
                    get(), get(), get(), get(), get(),
                    config.propertyOrNull("admin.email")?.getString() ?: "admin@adopt-u.com",
                    config.propertyOrNull("webauthn.rpId")?.getString() ?: "localhost",
                    config.propertyOrNull("webauthn.rpName")?.getString() ?: "Adopt-U Pet Adoption",
                    listOf(config.propertyOrNull("webauthn.origin")?.getString() ?: "http://localhost:80")
                )
            }
            single { MockImageStorage() }
            single { mockNotificationAdapter }
            single<com.adoptu.ports.NotificationPort> { mockNotificationAdapter }
            single<com.adoptu.ports.PetRepositoryPort> { com.adoptu.adapters.db.repositories.PetRepositoryImpl(get()) }
            single<com.adoptu.ports.SavedSearchRepositoryPort> { com.adoptu.adapters.db.repositories.SavedSearchRepositoryImpl(get()) }
            single<com.adoptu.ports.PhotographerRepositoryPort> { com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl(get(), get(), get()) }
            single { com.adoptu.services.PhotographerService(get(), get(), get(), get()) }
            single { com.adoptu.services.PetService(get(), get(), get(), get(), get()) }
            single { com.adoptu.services.validation.AuthValidationService() }
            single { com.adoptu.adapters.authkit.AdoptuUserRepositoryAdapter() }
            single { com.adoptu.adapters.authkit.AdoptuPasskeyCredentialRepositoryAdapter() }
        })
    }

    private fun startTestServer() = TestServer.start(modules = testModules(), initDatabase = false)

    @Test
    fun `GET verify-email returns success for valid token`() {
        val userId = createVerifiedUser()
        val token = createValidToken(userId)

        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/auth/verify-email?token=$token")

            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), VerificationResponse::class.java)
            assertTrue(body.success)
            assertEquals("Email verified successfully. You can now login.", body.message)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET verify-email returns error for invalid token`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/auth/verify-email?token=invalid-token")

            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), VerificationResponse::class.java)
            assertFalse(body.success)
            assertEquals("Invalid or expired token", body.message)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET verify-email returns error when token is missing`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/auth/verify-email")

            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), VerificationResponse::class.java)
            assertFalse(body.success)
            assertEquals("Token is required", body.message)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST resend-verification returns Unauthorized when not authenticated`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.post("${handle.baseUrl}/api/auth/resend-verification")

            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    private fun createVerifiedUser(): Int {
        return transaction {
            Users.insert {
                it[Users.username] = "test@test.com"
                it[Users.displayName] = "Test User"
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
                it[Users.isEmailVerified] = true
            } get Users.id
        }
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    // AuthRoutes.kt's /api/auth/verify-email now reads the shared AuthKit resetTokenHash slot on
    // Users (via AdoptuUserRepositoryAdapter.findByResetTokenHash) instead of the retired
    // EmailVerificationTokens table -- seed that slot directly, hashed the same way the route does.
    private fun createValidToken(userId: Int): String {
        val token = "valid-test-token-${clock.now().toEpochMilliseconds()}"
        transaction {
            Users.update({ Users.id eq userId }) {
                it[resetTokenHash] = sha256Hex(token)
                it[resetTokenExpiresAt] = clock.now().toEpochMilliseconds() + 86400000
            }
        }
        return token
    }
}

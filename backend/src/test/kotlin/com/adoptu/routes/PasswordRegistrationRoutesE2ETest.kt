package com.adoptu.routes

import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.WebAuthnCredentials
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.config.AppConfig
import com.adoptu.mocks.MockImageStorage
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestDatabase
import com.adoptu.ports.NotificationPort
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.ports.PhotographerRepositoryPort
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.services.EmailVerificationService
import com.adoptu.services.MagicLinkService
import com.adoptu.services.PasswordService
import com.adoptu.services.PetService
import com.adoptu.services.PhotographerService
import com.adoptu.services.UserService
import com.adoptu.services.auth.WebAuthnService
import com.adoptu.services.crypto.CryptoService
import com.adoptu.testsupport.TestHttp
import com.adoptu.testsupport.TestServer
import com.adoptu.web.JsonSupport
import com.fasterxml.jackson.databind.node.ObjectNode
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import java.security.SecureRandom
import java.util.Base64

@OptIn(ExperimentalTime::class)
class PasswordRegistrationRoutesE2ETest {

    private val clock = Clock.System
    private lateinit var mockNotificationAdapter: MockNotificationAdapter
    private val testConfig = AppConfig.fromMap(mapOf("admin.email" to "admin@test.com"))

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        mockNotificationAdapter = MockNotificationAdapter()
        CryptoService.initialize()
    }

    private fun testModules() = module {
        single { testConfig }
        single<Clock> { Clock.System }
        single<UserRepositoryPort> { UserRepository(get()) }
        single { UserService(get()) }
        single { EmailVerificationService(get(), get(), get(), "http://localhost:80") }
        single { PasswordService(get(), mockNotificationAdapter, get(), "http://localhost:80") }
        single { MagicLinkService(get(), mockNotificationAdapter, get(), "http://localhost:80", get()) }
        single {
            WebAuthnService(
                get(), get(), get(), get(), get(),
                testConfig.propertyOrNull("admin.email")?.getString() ?: "admin@adopt-u.com",
                testConfig.propertyOrNull("webauthn.rpId")?.getString() ?: "localhost",
                testConfig.propertyOrNull("webauthn.rpName")?.getString() ?: "Adopt-U Pet Adoption",
                listOf(testConfig.propertyOrNull("webauthn.origin")?.getString() ?: "http://localhost:80")
            )
        }
        single { MockImageStorage() }
        single { mockNotificationAdapter }
        single<NotificationPort> { mockNotificationAdapter }
        single<PetRepositoryPort> { PetRepositoryImpl(get()) }
        single<PhotographerRepositoryPort> { PhotographerRepositoryImpl(get(), get(), get()) }
        single { PhotographerService(get(), get(), get(), get()) }
        single { PetService(get(), get(), get(), get()) }
    }

    private fun startServer() = TestServer.start(modules = listOf(testModules()), initDatabase = false)

    private fun encryptPassword(password: String): String {
        val publicKey = CryptoService.getPublicKey()
        return CryptoService.encrypt(password, publicKey)
            ?: throw IllegalStateException("Encryption failed")
    }

    @Test
    fun `POST register-password creates user with password`() {
        val handle = startServer()
        try {
            val encryptedPassword = encryptPassword("SecurePass123!")

            val body = JsonSupport.objectMapper.writeValueAsString(
                mapOf(
                    "email" to "newuser@example.com",
                    "displayName" to "New User",
                    "roles" to "ADOPTER",
                    "encryptedPassword" to encryptedPassword
                )
            )
            val response = TestHttp.postJson("${handle.baseUrl}/api/auth/register-password", body)

            assertEquals(200, response.statusCode())
            val json = JsonSupport.objectMapper.readTree(response.body()) as ObjectNode
            assertTrue(json.get("success").asBoolean())
            assertTrue(json.get("emailVerificationSent").asBoolean())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST register-password with invalid email returns error`() {
        val handle = startServer()
        try {
            val encryptedPassword = encryptPassword("SecurePass123!")

            val body = JsonSupport.objectMapper.writeValueAsString(
                mapOf(
                    "email" to "invalid-email",
                    "displayName" to "Test User",
                    "roles" to "ADOPTER",
                    "encryptedPassword" to encryptedPassword
                )
            )
            val response = TestHttp.postJson("${handle.baseUrl}/api/auth/register-password", body)

            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST register-password with weak password returns error`() {
        val handle = startServer()
        try {
            val encryptedPassword = encryptPassword("weak")

            val body = JsonSupport.objectMapper.writeValueAsString(
                mapOf(
                    "email" to "weak@example.com",
                    "displayName" to "Weak User",
                    "roles" to "ADOPTER",
                    "encryptedPassword" to encryptedPassword
                )
            )
            val response = TestHttp.postJson("${handle.baseUrl}/api/auth/register-password", body)

            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST register-password assigns roles correctly`() {
        val handle = startServer()
        try {
            val encryptedPassword = encryptPassword("SecurePass123!")

            val body = JsonSupport.objectMapper.writeValueAsString(
                mapOf(
                    "email" to "roles@example.com",
                    "displayName" to "Roles User",
                    "roles" to "ADOPTER,RESCUER",
                    "encryptedPassword" to encryptedPassword
                )
            )
            val response = TestHttp.postJson("${handle.baseUrl}/api/auth/register-password", body)

            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET has-passkey returns false when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/auth/has-passkey")

            assertEquals(200, response.statusCode())
            val json = JsonSupport.objectMapper.readTree(response.body()) as ObjectNode
            assertFalse(json.get("success").asBoolean())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET has-passkey returns false for user without passkey`() {
        val userId = createTestUser("nopasskey@example.com", "No Passkey User")

        val handle = startServer()
        try {
            // Without session, returns failure
            val response = TestHttp.get("${handle.baseUrl}/api/auth/has-passkey")

            val json = JsonSupport.objectMapper.readTree(response.body()) as ObjectNode
            assertFalse(json.get("success").asBoolean())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST registration-options-for-user requires authentication`() {
        val handle = startServer()
        try {
            val body = JsonSupport.objectMapper.writeValueAsString(
                mapOf(
                    "email" to "user@example.com",
                    "displayName" to "Test User"
                )
            )
            val response = TestHttp.postJson("${handle.baseUrl}/api/auth/registration-options-for-user", body)

            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST register-passkey requires authentication`() {
        val handle = startServer()
        try {
            val body = JsonSupport.objectMapper.writeValueAsString(
                mapOf(
                    "registrationResponse" to "{}",
                    "passkeyName" to "Test Key"
                )
            )
            val response = TestHttp.postJson("${handle.baseUrl}/api/auth/register-passkey", body)

            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
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
                it[WebAuthnCredentials.credentialId] = Base64.getEncoder().encodeToString(credentialId)
                it[WebAuthnCredentials.attestedCredentialDataBase64] = Base64.getEncoder().encodeToString(aaguid + publicKey)
                it[WebAuthnCredentials.signCount] = 0
                it[WebAuthnCredentials.transports] = null
                it[WebAuthnCredentials.createdAt] = clock.now().toEpochMilliseconds()
            } get WebAuthnCredentials.id
        }
    }
}

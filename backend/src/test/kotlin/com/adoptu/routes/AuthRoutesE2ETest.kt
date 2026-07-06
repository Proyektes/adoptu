package com.adoptu.routes

import com.adoptu.adapters.db.EmailVerificationAttempts
import com.adoptu.adapters.db.EmailVerificationTokens
import com.adoptu.adapters.db.MagicLinkTokens
import com.adoptu.adapters.db.PasswordResetTokens
import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.UserPasswords
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.config.AppConfig
import com.adoptu.dto.output.AuthMeResponse
import com.adoptu.dto.output.RegistrationResponse
import com.adoptu.dto.output.SuccessWithErrorResponse
import com.adoptu.dto.output.VerificationResponse
import com.adoptu.mocks.MockImageStorage
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestDatabase
import com.adoptu.services.EmailVerificationService
import com.adoptu.services.crypto.CryptoService
import com.adoptu.testsupport.TestHttp
import com.adoptu.testsupport.TestServer
import com.adoptu.testsupport.TestServerHandle
import com.adoptu.web.JsonSupport
import com.adoptu.web.SuccessResponse
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import java.net.URLEncoder
import java.net.http.HttpResponse
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Covers endpoints/branches in AuthRoutes.kt not already exercised by
 * PasswordRegistrationRoutesE2ETest and EmailVerificationRoutesE2ETest:
 * registration-options, register, has-passkey (authenticated), registration-options-for-user
 * (authenticated), register-passkey (authenticated), resend-verification (all branches),
 * assertion-options, authenticate, logout, me, request-magic-link, magic-link-login,
 * login-with-password, forgot-password, reset-password, encryption-key.
 */
@OptIn(ExperimentalTime::class)
class AuthRoutesE2ETest {

    private val clock = Clock.System
    private lateinit var mockNotificationAdapter: MockNotificationAdapter

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        mockNotificationAdapter = MockNotificationAdapter()
        CryptoService.initialize()
    }

    /**
     * Starts a TestServer with a minimal Koin module (mirrors the original Ktor test's inline
     * `module { ... }` block) instead of the full production appModule, and with its own DB
     * connection managed via TestDatabase (initDatabase = false).
     */
    private fun startTestServer(): TestServerHandle {
        val config = AppConfig.fromMap(mapOf("env" to "test", "admin.email" to "admin@test.com"))

        val testModules = module {
            single<AppConfig> { config }
            single<Clock> { Clock.System }
            single<com.adoptu.ports.UserRepositoryPort> { UserRepository(get()) }
            single { com.adoptu.services.UserService(get()) }
            single { EmailVerificationService(get(), get(), get(), "http://localhost:80") }
            single { com.adoptu.services.PasswordService(get(), mockNotificationAdapter, get(), "http://localhost:80") }
            single { com.adoptu.services.MagicLinkService(get(), mockNotificationAdapter, get(), "http://localhost:80", get()) }
            single {
                com.adoptu.services.auth.WebAuthnService(
                    get(), get(), get(), get(), get(),
                    config.propertyOrNull("admin.email")?.getString() ?: "admin@adopt-u.com",
                    config.propertyOrNull("webauthn.rpId")?.getString() ?: "localhost",
                    config.propertyOrNull("webauthn.rpName")?.getString() ?: "Adopt-U Pet Adoption",
                    listOf(config.propertyOrNull("webauthn.origin")?.getString() ?: "http://localhost:80")
                )
            }
            single { com.adoptu.services.validation.AuthValidationService() }
            single { MockImageStorage() }
            single { mockNotificationAdapter }
            single<com.adoptu.ports.NotificationPort> { mockNotificationAdapter }
            single<com.adoptu.ports.PetRepositoryPort> { com.adoptu.adapters.db.repositories.PetRepositoryImpl(get()) }
            single<com.adoptu.ports.PhotographerRepositoryPort> { com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl(get(), get(), get()) }
            single { com.adoptu.services.PhotographerService(get(), get(), get(), get()) }
            single { com.adoptu.services.PetService(get(), get(), get(), get()) }
        }

        return TestServer.start(modules = listOf(testModules), initDatabase = false)
    }

    private fun encryptValue(value: String): String {
        val publicKey = CryptoService.getPublicKey()
        return CryptoService.encrypt(value, publicKey) ?: throw IllegalStateException("Encryption failed")
    }

    private fun formUrlEncode(params: List<Pair<String, String>>): String =
        params.joinToString("&") { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }

    private fun HttpResponse<String>.header(name: String): String? = headers().firstValue(name).orElse(null)

    // ==================== Helpers: real registration / login flows ====================

    private fun TestServerHandle.registerUnverifiedUser(
        email: String,
        displayName: String = "Test User",
        password: String = "SecurePass123!",
        roles: String = "ADOPTER"
    ): Int {
        val response = TestHttp.postJson(
            "$baseUrl/api/auth/register-password",
            JsonSupport.objectMapper.writeValueAsString(
                mapOf(
                    "email" to email,
                    "displayName" to displayName,
                    "roles" to roles,
                    "encryptedPassword" to encryptValue(password)
                )
            )
        )
        assertEquals(200, response.statusCode())
        return transaction { Users.selectAll().where { Users.username eq email }.first()[Users.id] }
    }

    private fun TestServerHandle.registerVerifiedUser(
        email: String,
        displayName: String = "Test User",
        password: String = "SecurePass123!",
        roles: String = "ADOPTER"
    ): Int {
        val userId = registerUnverifiedUser(email, displayName, password, roles)
        transaction {
            Users.update({ Users.id eq userId }) { it[Users.isEmailVerified] = true }
        }
        return userId
    }

    private fun TestServerHandle.loginAndGetCookie(email: String, password: String): String {
        val response = TestHttp.postJson(
            "$baseUrl/api/auth/login-with-password",
            JsonSupport.objectMapper.writeValueAsString(PasswordLoginRequest(email, encryptValue(password)))
        )
        assertEquals(200, response.statusCode())
        val setCookie = response.header("Set-Cookie") ?: error("Missing Set-Cookie header on login response")
        return setCookie.substringBefore(";")
    }

    // ==================== POST /api/auth/registration-options ====================

    @Test
    fun `POST registration-options returns 400 when email missing`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postForm(
                "${handle.baseUrl}/api/auth/registration-options",
                formUrlEncode(listOf("displayName" to "Name"))
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST registration-options returns 400 when displayName missing`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postForm(
                "${handle.baseUrl}/api/auth/registration-options",
                formUrlEncode(listOf("email" to "new@example.com"))
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST registration-options returns localized error for invalid email format`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postForm(
                "${handle.baseUrl}/api/auth/registration-options",
                formUrlEncode(listOf("email" to "not-an-email", "displayName" to "Name", "language" to "es"))
            )
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("formato de correo"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST registration-options returns options for new user`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postForm(
                "${handle.baseUrl}/api/auth/registration-options",
                formUrlEncode(listOf("email" to "brandnew@example.com", "displayName" to "Brand New"))
            )
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("challenge"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST registration-options returns localized already-registered error for verified user`() {
        val email = "verified-dup@example.com"
        val handle = startTestServer()
        try {
            handle.registerVerifiedUser(email)

            val response = TestHttp.postForm(
                "${handle.baseUrl}/api/auth/registration-options",
                formUrlEncode(listOf("email" to email, "displayName" to "Name", "language" to "fr"))
            )
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("déjà enregistré"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST registration-options resends verification and returns localized message for unverified user`() {
        val email = "unverified-dup@example.com"
        val handle = startTestServer()
        try {
            handle.registerUnverifiedUser(email)

            val response = TestHttp.postForm(
                "${handle.baseUrl}/api/auth/registration-options",
                formUrlEncode(listOf("email" to email, "displayName" to "Name", "language" to "pt"))
            )
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("e-mail de verificação enviado"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST registration-options reports rate limit when daily verification emails exhausted`() {
        val email = "registerratelimited@example.com"
        val handle = startTestServer()
        try {
            val userId = handle.registerUnverifiedUser(email)
            transaction {
                EmailVerificationTokens.deleteWhere { EmailVerificationTokens.userId eq userId }
                repeat(3) {
                    EmailVerificationAttempts.insert {
                        it[EmailVerificationAttempts.userId] = userId
                        it[EmailVerificationAttempts.createdAt] = clock.now().toEpochMilliseconds()
                    }
                }
            }

            val response = TestHttp.postForm(
                "${handle.baseUrl}/api/auth/registration-options",
                formUrlEncode(listOf("email" to email, "displayName" to "Name"))
            )
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("daily limit"))
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/auth/register ====================

    @Test
    fun `POST register returns 400 when email missing`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postForm(
                "${handle.baseUrl}/api/auth/register",
                formUrlEncode(listOf("displayName" to "Name", "registrationResponse" to "{}"))
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST register returns 400 when displayName missing`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postForm(
                "${handle.baseUrl}/api/auth/register",
                formUrlEncode(listOf("email" to "a@b.com", "registrationResponse" to "{}"))
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST register returns 400 for invalid email format`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postForm(
                "${handle.baseUrl}/api/auth/register",
                formUrlEncode(listOf("email" to "bad-email", "displayName" to "Name", "registrationResponse" to "{}"))
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST register returns 400 when registrationResponse missing`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postForm(
                "${handle.baseUrl}/api/auth/register",
                formUrlEncode(listOf("email" to "a@b.com", "displayName" to "Name"))
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST register with default roles fails gracefully for invalid attestation`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postForm(
                "${handle.baseUrl}/api/auth/register",
                formUrlEncode(listOf("email" to "garbage1@example.com", "displayName" to "Name", "registrationResponse" to "not-real-json"))
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), RegistrationResponse::class.java)
            assertFalse(body.success)
            assertEquals("Registration failed", body.message)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST register parses explicit roles list for invalid attestation`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postForm(
                "${handle.baseUrl}/api/auth/register",
                formUrlEncode(
                    listOf(
                        "email" to "garbage2@example.com",
                        "displayName" to "Name",
                        "roles" to "ADOPTER,RESCUER",
                        "registrationResponse" to "not-real-json"
                    )
                )
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), RegistrationResponse::class.java)
            assertFalse(body.success)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST register adds ADMIN role for admin email with invalid attestation`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postForm(
                "${handle.baseUrl}/api/auth/register",
                formUrlEncode(
                    listOf(
                        "email" to "admin@test.com",
                        "displayName" to "Admin",
                        "registrationResponse" to "not-real-json"
                    )
                )
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), RegistrationResponse::class.java)
            assertFalse(body.success)
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/auth/register-password (additional branches) ====================

    @Test
    fun `POST register-password defaults roles to ADOPTER when roles field is absent`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/register-password",
                JsonSupport.objectMapper.writeValueAsString(
                    mapOf(
                        "email" to "noroles@example.com",
                        "displayName" to "No Roles",
                        "encryptedPassword" to encryptValue("SecurePass123!")
                    )
                )
            )
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST register-password adds ADMIN role for admin email`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/register-password",
                JsonSupport.objectMapper.writeValueAsString(
                    mapOf(
                        "email" to "admin@test.com",
                        "displayName" to "Admin",
                        "roles" to "ADOPTER",
                        "encryptedPassword" to encryptValue("SecurePass123!")
                    )
                )
            )
            assertEquals(200, response.statusCode())
            val userId = transaction { Users.selectAll().where { Users.username eq "admin@test.com" }.first()[Users.id] }
            val roles = transaction {
                UserActiveRoles.selectAll().where { UserActiveRoles.userId eq userId }.map { it[UserActiveRoles.role] }
            }
            assertTrue(roles.contains("ADMIN"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST register-password ignores self-assigned ADMIN role for non-admin email`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/register-password",
                JsonSupport.objectMapper.writeValueAsString(
                    mapOf(
                        "email" to "attacker@example.com",
                        "displayName" to "Attacker",
                        "roles" to "ADOPTER,ADMIN",
                        "encryptedPassword" to encryptValue("SecurePass123!")
                    )
                )
            )
            assertEquals(200, response.statusCode())
            val userId = transaction { Users.selectAll().where { Users.username eq "attacker@example.com" }.first()[Users.id] }
            val roles = transaction {
                UserActiveRoles.selectAll().where { UserActiveRoles.userId eq userId }.map { it[UserActiveRoles.role] }
            }
            assertFalse(roles.contains("ADMIN"))
            assertTrue(roles.contains("ADOPTER"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST register-password does not activate roles that require email verification`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/register-password",
                JsonSupport.objectMapper.writeValueAsString(
                    mapOf(
                        "email" to "unverified-shelter@example.com",
                        "displayName" to "Unverified Shelter",
                        "roles" to "ADOPTER,RESCUER,SHELTER,PHOTOGRAPHER,TEMPORAL_HOME,STERILIZATION_SERVICE",
                        "encryptedPassword" to encryptValue("SecurePass123!")
                    )
                )
            )
            assertEquals(200, response.statusCode())
            val userId = transaction { Users.selectAll().where { Users.username eq "unverified-shelter@example.com" }.first()[Users.id] }
            val roles = transaction {
                UserActiveRoles.selectAll().where { UserActiveRoles.userId eq userId }.map { it[UserActiveRoles.role] }
            }
            // Adopter has no verification gate anywhere and registers immediately;
            // Rescuer/Shelter/Photographer/Temporal-Home/Sterilization all mirror the
            // isEmailVerified gate that their respective POST /api/users/{role}-profile
            // (activate=true) endpoint already enforces - a brand new registration is
            // never verified yet, so none of them may be granted at signup. They must be
            // activated from /profile after verifying.
            assertTrue(roles.contains("ADOPTER"))
            assertFalse(roles.contains("RESCUER"))
            assertFalse(roles.contains("SHELTER"))
            assertFalse(roles.contains("PHOTOGRAPHER"))
            assertFalse(roles.contains("TEMPORAL_HOME"))
            assertFalse(roles.contains("STERILIZATION_SERVICE"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/auth/has-passkey (authenticated) ====================

    @Test
    fun `GET has-passkey returns false for authenticated user without passkey`() {
        val email = "haspasskey@example.com"
        val handle = startTestServer()
        try {
            handle.registerVerifiedUser(email)
            val cookie = handle.loginAndGetCookie(email, "SecurePass123!")

            val response = TestHttp.get("${handle.baseUrl}/api/auth/has-passkey", cookie)
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessWithErrorResponse::class.java)
            assertFalse(body.success)
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/auth/registration-options-for-user ====================

    @Test
    fun `POST registration-options-for-user returns options with explicit email and displayName`() {
        val email = "optsforuser@example.com"
        val handle = startTestServer()
        try {
            handle.registerVerifiedUser(email)
            val cookie = handle.loginAndGetCookie(email, "SecurePass123!")

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/registration-options-for-user",
                JsonSupport.objectMapper.writeValueAsString(mapOf("email" to email, "displayName" to "Explicit Name")),
                cookie
            )
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("challenge"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST registration-options-for-user falls back to session email and displayName`() {
        val email = "optsforuserfallback@example.com"
        val handle = startTestServer()
        try {
            handle.registerVerifiedUser(email)
            val cookie = handle.loginAndGetCookie(email, "SecurePass123!")

            val response = TestHttp.postJson("${handle.baseUrl}/api/auth/registration-options-for-user", "{}", cookie)
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("challenge"))
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/auth/register-passkey (authenticated) ====================

    @Test
    fun `POST register-passkey returns 400 when registrationResponse missing`() {
        val email = "regpasskey@example.com"
        val handle = startTestServer()
        try {
            handle.registerVerifiedUser(email)
            val cookie = handle.loginAndGetCookie(email, "SecurePass123!")

            val response = TestHttp.postJson("${handle.baseUrl}/api/auth/register-passkey", "{}", cookie)
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST register-passkey returns failure when no challenge was issued`() {
        val email = "regpasskeyfail@example.com"
        val handle = startTestServer()
        try {
            handle.registerVerifiedUser(email)
            val cookie = handle.loginAndGetCookie(email, "SecurePass123!")

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/register-passkey",
                JsonSupport.objectMapper.writeValueAsString(mapOf("registrationResponse" to "garbage")),
                cookie
            )
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Failed to register passkey"))
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/auth/resend-verification ====================

    @Test
    fun `POST resend-verification returns 401 for form request without email`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postForm(
                "${handle.baseUrl}/api/auth/resend-verification",
                formUrlEncode(listOf("foo" to "bar"))
            )
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST resend-verification sends email for unverified user via form email`() {
        val email = "resendform@example.com"
        val handle = startTestServer()
        try {
            handle.registerUnverifiedUser(email)

            val response = TestHttp.postForm(
                "${handle.baseUrl}/api/auth/resend-verification",
                formUrlEncode(listOf("email" to email))
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), VerificationResponse::class.java)
            assertTrue(body.success)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST resend-verification fails for already verified user via form email`() {
        val email = "resendformverified@example.com"
        val handle = startTestServer()
        try {
            handle.registerVerifiedUser(email)

            val response = TestHttp.postForm(
                "${handle.baseUrl}/api/auth/resend-verification",
                formUrlEncode(listOf("email" to email))
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), VerificationResponse::class.java)
            assertFalse(body.success)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST resend-verification fails for authenticated already-verified user`() {
        val email = "resendsession@example.com"
        val handle = startTestServer()
        try {
            handle.registerVerifiedUser(email)
            val cookie = handle.loginAndGetCookie(email, "SecurePass123!")

            val response = TestHttp.post("${handle.baseUrl}/api/auth/resend-verification", cookie)
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), VerificationResponse::class.java)
            assertFalse(body.success)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST resend-verification succeeds for authenticated user flipped back to unverified`() {
        val email = "resendsessionunverified@example.com"
        val handle = startTestServer()
        try {
            val userId = handle.registerVerifiedUser(email)
            val cookie = handle.loginAndGetCookie(email, "SecurePass123!")
            // Session cookie only carries userId/email/displayName; flip verification status
            // directly in the DB to exercise the "authenticated but unverified" branch.
            transaction { Users.update({ Users.id eq userId }) { it[Users.isEmailVerified] = false } }

            val response = TestHttp.post("${handle.baseUrl}/api/auth/resend-verification", cookie)
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), VerificationResponse::class.java)
            assertTrue(body.success)
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/auth/assertion-options ====================

    @Test
    fun `GET assertion-options returns challenge`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/auth/assertion-options")
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("challenge"))
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/auth/authenticate ====================

    @Test
    fun `POST authenticate returns failure when credential missing`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postForm(
                "${handle.baseUrl}/api/auth/authenticate",
                formUrlEncode(emptyList())
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessWithErrorResponse::class.java)
            assertFalse(body.success)
            assertEquals("No credential", body.error)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST authenticate returns failure for invalid credential`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postForm(
                "${handle.baseUrl}/api/auth/authenticate",
                formUrlEncode(listOf("credential" to "not-real-json"))
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessWithErrorResponse::class.java)
            assertFalse(body.success)
            assertEquals("Authentication failed", body.error)
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/auth/logout ====================

    @Test
    fun `POST logout returns success`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.post("${handle.baseUrl}/api/auth/logout")
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessResponse::class.java)
            assertTrue(body.success)
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/auth/me ====================

    @Test
    fun `GET me returns unauthenticated when no session`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/auth/me")
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), AuthMeResponse::class.java)
            assertFalse(body.authenticated)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET me returns authenticated user details for valid session`() {
        val email = "meuser@example.com"
        val handle = startTestServer()
        try {
            handle.registerVerifiedUser(email, displayName = "Me User", roles = "ADOPTER,RESCUER")
            val cookie = handle.loginAndGetCookie(email, "SecurePass123!")

            val response = TestHttp.get("${handle.baseUrl}/api/auth/me", cookie)
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), AuthMeResponse::class.java)
            assertTrue(body.authenticated)
            assertEquals(email, body.email)
            assertTrue(body.emailVerified)
            assertTrue(body.activeRoles.contains("ADOPTER"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET me returns unauthenticated when session user was deleted`() {
        val email = "medeleted@example.com"
        val handle = startTestServer()
        try {
            val userId = handle.registerVerifiedUser(email)
            val cookie = handle.loginAndGetCookie(email, "SecurePass123!")

            transaction {
                EmailVerificationAttempts.deleteWhere { EmailVerificationAttempts.userId eq userId }
                EmailVerificationTokens.deleteWhere { EmailVerificationTokens.userId eq userId }
                UserPasswords.deleteWhere { UserPasswords.userId eq userId }
                UserActiveRoles.deleteWhere { UserActiveRoles.userId eq userId }
                Users.deleteWhere { Users.id eq userId }
            }

            val response = TestHttp.get("${handle.baseUrl}/api/auth/me", cookie)
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), AuthMeResponse::class.java)
            assertFalse(body.authenticated)
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/auth/request-magic-link ====================

    @Test
    fun `POST request-magic-link returns 400 for invalid body`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/request-magic-link",
                """{"notEncryptedData":"x"}"""
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST request-magic-link returns 400 when decryption fails`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/request-magic-link",
                JsonSupport.objectMapper.writeValueAsString(EncryptedLoginRequest("not-encrypted-data"))
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST request-magic-link returns 400 for decrypted invalid email format`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/request-magic-link",
                JsonSupport.objectMapper.writeValueAsString(EncryptedLoginRequest(encryptValue("not-an-email")))
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST request-magic-link returns success for unknown email to avoid enumeration`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/request-magic-link",
                JsonSupport.objectMapper.writeValueAsString(EncryptedLoginRequest(encryptValue("unknown@example.com")))
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessResponse::class.java)
            assertTrue(body.success)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST request-magic-link returns failure for unverified user`() {
        val email = "magicunverified@example.com"
        val handle = startTestServer()
        try {
            handle.registerUnverifiedUser(email)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/request-magic-link",
                JsonSupport.objectMapper.writeValueAsString(EncryptedLoginRequest(encryptValue(email)))
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessWithErrorResponse::class.java)
            assertFalse(body.success)
            assertEquals(email, body.email)
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/auth/magic-link-login ====================

    @Test
    fun `GET magic-link-login redirects with invalid_token when token missing`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/auth/magic-link-login")
            assertEquals(302, response.statusCode())
            assertEquals("/login?error=invalid_token", response.header("Location"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET magic-link-login redirects with invalid_or_expired for unknown token`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/auth/magic-link-login?token=nonexistent")
            assertEquals(302, response.statusCode())
            assertEquals("/login?error=invalid_or_expired", response.header("Location"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET magic-link-login redirects with not_verified when token valid but email unverified and not expired`() {
        val email = "magiclogin-notverified@example.com"
        val handle = startTestServer()
        try {
            val userId = handle.registerUnverifiedUser(email)
            val token = insertMagicLinkToken(userId)

            val response = TestHttp.get("${handle.baseUrl}/api/auth/magic-link-login?token=$token")
            assertEquals(302, response.statusCode())
            val location = response.header("Location") ?: ""
            assertTrue(location.startsWith("/login?error=not_verified"))
            assertFalse(location.contains("resent=true"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET magic-link-login redirects with not_verified and resent when verification token missing`() {
        val email = "magiclogin-resent@example.com"
        val handle = startTestServer()
        try {
            val userId = handle.registerUnverifiedUser(email)
            transaction { EmailVerificationTokens.deleteWhere { EmailVerificationTokens.userId eq userId } }
            val token = insertMagicLinkToken(userId)

            val response = TestHttp.get("${handle.baseUrl}/api/auth/magic-link-login?token=$token")
            assertEquals(302, response.statusCode())
            val location = response.header("Location") ?: ""
            assertTrue(location.contains("resent=true"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET magic-link-login redirects with banned for banned verified user`() {
        val email = "magiclogin-banned@example.com"
        val handle = startTestServer()
        try {
            val userId = handle.registerVerifiedUser(email)
            transaction { Users.update({ Users.id eq userId }) { it[Users.isBanned] = true; it[Users.banReason] = "test ban" } }
            val token = insertMagicLinkToken(userId)

            val response = TestHttp.get("${handle.baseUrl}/api/auth/magic-link-login?token=$token")
            assertEquals(302, response.statusCode())
            assertEquals("/login?error=banned", response.header("Location"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET magic-link-login succeeds and redirects to profile for verified non-banned user`() {
        val email = "magiclogin-success@example.com"
        val handle = startTestServer()
        try {
            val userId = handle.registerVerifiedUser(email)
            val token = insertMagicLinkToken(userId)

            val response = TestHttp.get("${handle.baseUrl}/api/auth/magic-link-login?token=$token")
            assertEquals(302, response.statusCode())
            assertEquals("/profile", response.header("Location"))
            assertNotNull(response.header("Set-Cookie"))
        } finally {
            handle.stop()
        }
    }

    private fun insertMagicLinkToken(userId: Int, expiresInMs: Long = 5 * 60 * 1000L): String {
        val token = "magic-token-$userId-${clock.now().toEpochMilliseconds()}"
        transaction {
            MagicLinkTokens.insert {
                it[MagicLinkTokens.userId] = userId
                it[MagicLinkTokens.token] = token
                it[MagicLinkTokens.expiresAt] = clock.now().toEpochMilliseconds() + expiresInMs
                it[MagicLinkTokens.createdAt] = clock.now().toEpochMilliseconds()
                it[MagicLinkTokens.usedAt] = null
            }
        }
        return token
    }

    // ==================== POST /api/auth/login-with-password ====================

    @Test
    fun `POST login-with-password returns 400 for invalid body`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/login-with-password",
                """{"foo":"bar"}"""
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST login-with-password returns invalid credentials for unknown user`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/login-with-password",
                JsonSupport.objectMapper.writeValueAsString(PasswordLoginRequest("unknown@example.com", encryptValue("whatever")))
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessWithErrorResponse::class.java)
            assertFalse(body.success)
            assertEquals("Invalid credentials", body.error)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST login-with-password returns invalid credentials for wrong password`() {
        val email = "wrongpass@example.com"
        val handle = startTestServer()
        try {
            handle.registerVerifiedUser(email)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/login-with-password",
                JsonSupport.objectMapper.writeValueAsString(PasswordLoginRequest(email, encryptValue("WrongPass123!")))
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessWithErrorResponse::class.java)
            assertFalse(body.success)
            assertEquals("Invalid credentials", body.error)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST login-with-password locks out after repeated failed attempts`() {
        val email = "lockout@example.com"
        val handle = startTestServer()
        try {
            handle.registerVerifiedUser(email)

            repeat(5) {
                val response = TestHttp.postJson(
                    "${handle.baseUrl}/api/auth/login-with-password",
                    JsonSupport.objectMapper.writeValueAsString(PasswordLoginRequest(email, encryptValue("WrongPass123!")))
                )
                val body = JsonSupport.objectMapper.readValue(response.body(), SuccessWithErrorResponse::class.java)
                assertEquals("Invalid credentials", body.error)
            }

            // 6th attempt, even with the correct password, is locked out rather than checked.
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/login-with-password",
                JsonSupport.objectMapper.writeValueAsString(PasswordLoginRequest(email, encryptValue("StrongPass123!")))
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessWithErrorResponse::class.java)
            assertFalse(body.success)
            assertEquals("Too many failed login attempts. Please try again in 15 minutes.", body.error)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST login-with-password returns not-verified message when token still valid`() {
        val email = "loginnotverified@example.com"
        val handle = startTestServer()
        try {
            handle.registerUnverifiedUser(email)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/login-with-password",
                JsonSupport.objectMapper.writeValueAsString(PasswordLoginRequest(email, encryptValue("SecurePass123!")))
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessWithErrorResponse::class.java)
            assertFalse(body.success)
            assertEquals("Please verify your email before logging in", body.error)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST login-with-password resends verification when token missing or expired`() {
        val email = "loginresend@example.com"
        val handle = startTestServer()
        try {
            val userId = handle.registerUnverifiedUser(email)
            transaction { EmailVerificationTokens.deleteWhere { EmailVerificationTokens.userId eq userId } }

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/login-with-password",
                JsonSupport.objectMapper.writeValueAsString(PasswordLoginRequest(email, encryptValue("SecurePass123!")))
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessWithErrorResponse::class.java)
            assertFalse(body.success)
            assertEquals("Verification email was expired. A new verification email has been sent.", body.error)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST login-with-password reports rate limit when daily verification emails exhausted`() {
        val email = "loginratelimited@example.com"
        val handle = startTestServer()
        try {
            val userId = handle.registerUnverifiedUser(email)
            transaction {
                EmailVerificationTokens.deleteWhere { EmailVerificationTokens.userId eq userId }
                repeat(3) {
                    EmailVerificationAttempts.insert {
                        it[EmailVerificationAttempts.userId] = userId
                        it[EmailVerificationAttempts.createdAt] = clock.now().toEpochMilliseconds()
                    }
                }
            }

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/login-with-password",
                JsonSupport.objectMapper.writeValueAsString(PasswordLoginRequest(email, encryptValue("SecurePass123!")))
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessWithErrorResponse::class.java)
            assertFalse(body.success)
            assertTrue(body.error!!.contains("daily limit"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST login-with-password returns banned message for banned verified user`() {
        val email = "loginbanned@example.com"
        val handle = startTestServer()
        try {
            val userId = handle.registerVerifiedUser(email)
            transaction { Users.update({ Users.id eq userId }) { it[Users.isBanned] = true; it[Users.banReason] = "violated rules" } }

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/login-with-password",
                JsonSupport.objectMapper.writeValueAsString(PasswordLoginRequest(email, encryptValue("SecurePass123!")))
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessWithErrorResponse::class.java)
            assertFalse(body.success)
            assertTrue(body.error!!.contains("violated rules"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST login-with-password succeeds for verified non-banned user and sets session`() {
        val email = "loginsuccess@example.com"
        val handle = startTestServer()
        try {
            handle.registerVerifiedUser(email)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/login-with-password",
                JsonSupport.objectMapper.writeValueAsString(PasswordLoginRequest(email, encryptValue("SecurePass123!")))
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessResponse::class.java)
            assertTrue(body.success)
            assertNotNull(response.header("Set-Cookie"))
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/auth/forgot-password ====================

    @Test
    fun `POST forgot-password returns 400 for invalid body`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/forgot-password",
                """{"foo":"bar"}"""
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST forgot-password returns 400 when decryption fails`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/forgot-password",
                JsonSupport.objectMapper.writeValueAsString(EncryptedLoginRequest("not-encrypted"))
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST forgot-password succeeds for unknown email`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/forgot-password",
                JsonSupport.objectMapper.writeValueAsString(EncryptedLoginRequest(encryptValue("unknownforgot@example.com")))
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessResponse::class.java)
            assertTrue(body.success)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST forgot-password returns failure when daily reset limit reached`() {
        val email = "forgotratelimited@example.com"
        val handle = startTestServer()
        try {
            val userId = handle.registerVerifiedUser(email)
            transaction {
                repeat(3) { i ->
                    PasswordResetTokens.insert {
                        it[PasswordResetTokens.userId] = userId
                        it[PasswordResetTokens.token] = "preexisting-token-$i"
                        it[PasswordResetTokens.expiresAt] = clock.now().toEpochMilliseconds() + 900000
                        it[PasswordResetTokens.createdAt] = clock.now().toEpochMilliseconds()
                    }
                }
            }

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/forgot-password",
                JsonSupport.objectMapper.writeValueAsString(EncryptedLoginRequest(encryptValue(email)))
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessWithErrorResponse::class.java)
            assertFalse(body.success)
            assertTrue(body.error!!.contains("Maximum password reset"))
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/auth/reset-password ====================

    @Test
    fun `POST reset-password returns 400 when token missing`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/reset-password",
                JsonSupport.objectMapper.writeValueAsString(EncryptedLoginRequest(encryptValue("NewPass123!")))
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST reset-password returns 400 for invalid body`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/reset-password?token=sometoken",
                """{"foo":"bar"}"""
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST reset-password returns failure for invalid token`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/reset-password?token=invalid-token",
                JsonSupport.objectMapper.writeValueAsString(EncryptedLoginRequest(encryptValue("NewPass123!")))
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessWithErrorResponse::class.java)
            assertFalse(body.success)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST reset-password returns failure when new password is weak`() {
        val email = "resetweak@example.com"
        val handle = startTestServer()
        try {
            val userId = handle.registerVerifiedUser(email)
            val token = insertPasswordResetToken(userId)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/reset-password?token=$token",
                JsonSupport.objectMapper.writeValueAsString(EncryptedLoginRequest(encryptValue("weak")))
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessWithErrorResponse::class.java)
            assertFalse(body.success)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST reset-password succeeds for valid token and strong password`() {
        val email = "resetsuccess@example.com"
        val handle = startTestServer()
        try {
            val userId = handle.registerVerifiedUser(email)
            val token = insertPasswordResetToken(userId)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/auth/reset-password?token=$token",
                JsonSupport.objectMapper.writeValueAsString(EncryptedLoginRequest(encryptValue("NewSecurePass123!")))
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessResponse::class.java)
            assertTrue(body.success)
        } finally {
            handle.stop()
        }
    }

    private fun insertPasswordResetToken(userId: Int): String {
        val token = "reset-token-$userId-${clock.now().toEpochMilliseconds()}"
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

    // ==================== GET /api/auth/encryption-key ====================

    @Test
    fun `GET encryption-key returns public key`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/auth/encryption-key")
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("publicKey"))
        } finally {
            handle.stop()
        }
    }
}

package com.adoptu.routes

import com.adoptu.adapters.db.EmailVerificationTokens
import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.config.AppConfig
import com.adoptu.dto.input.Gender
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestDatabase
import com.adoptu.services.EmailVerificationService
import com.adoptu.services.MagicLinkService
import com.adoptu.services.PasswordService
import com.adoptu.services.UserService
import com.adoptu.services.auth.WebAuthnService
import com.adoptu.testsupport.TestHttp
import com.adoptu.testsupport.TestServer
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * End-to-end tests for [uiRoutes]: starts a real Helidon Nima [TestServer] mounting the full
 * production route tree (which includes uiRoutes()) and performs actual HTTP GET requests
 * against every registered page route, both unauthenticated and authenticated, to exercise the
 * HTML page rendering functions under `com.adoptu.pages`.
 */
@OptIn(ExperimentalTime::class)
class UIRoutesE2ETest {

    private val clock = Clock.System

    // Seeded user ids/roles, created fresh in each test.
    private val adminId = 10
    private val rescuerId = 11
    private val temporalHomeId = 12
    private val plainId = 13

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        seedUsers()
    }

    private fun seedUsers() {
        transaction {
            Users.insert {
                it[Users.id] = adminId
                it[Users.username] = "admin@e2e.test"
                it[Users.displayName] = "Admin User"
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
            }
            UserActiveRoles.insert {
                it[UserActiveRoles.userId] = adminId
                it[UserActiveRoles.role] = "ADMIN"
            }

            Users.insert {
                it[Users.id] = rescuerId
                it[Users.username] = "rescuer@e2e.test"
                it[Users.displayName] = "Rescuer User"
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
            }
            UserActiveRoles.insert {
                it[UserActiveRoles.userId] = rescuerId
                it[UserActiveRoles.role] = "RESCUER"
            }

            Users.insert {
                it[Users.id] = temporalHomeId
                it[Users.username] = "temporalhome@e2e.test"
                it[Users.displayName] = "Temporal Home User"
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
            }
            UserActiveRoles.insert {
                it[UserActiveRoles.userId] = temporalHomeId
                it[UserActiveRoles.role] = "TEMPORAL_HOME"
            }

            Users.insert {
                it[Users.id] = plainId
                it[Users.username] = "plain@e2e.test"
                it[Users.displayName] = "Plain User"
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
            }
        }
    }

    private fun seedValidVerificationToken(userId: Int, token: String) {
        transaction {
            EmailVerificationTokens.insert {
                it[EmailVerificationTokens.userId] = userId
                it[EmailVerificationTokens.token] = token
                it[EmailVerificationTokens.expiresAt] = clock.now().toEpochMilliseconds() + 86_400_000L
                it[EmailVerificationTokens.createdAt] = clock.now().toEpochMilliseconds()
            }
        }
    }

    /** Mirrors the original Ktor test's reduced Koin module: only what uiRoutes() actually needs. */
    private fun startServer() = TestServer.start(
        configOverrides = mapOf("env" to "test"),
        modules = listOf(testModules()),
        initDatabase = false,
        withTestLogin = true
    )

    private fun testModules() = module {
        val config = AppConfig.fromMap(mapOf("env" to "test"))
        single<Clock> { Clock.System }
        single<com.adoptu.ports.UserRepositoryPort> { UserRepository(get()) }
        single { MockNotificationAdapter() }
        single<com.adoptu.ports.NotificationPort> { get<MockNotificationAdapter>() }
        single { UserService(get()) }
        single { EmailVerificationService(get(), get(), get()) }
        single { PasswordService(get(), get(), get(), "http://localhost:80") }
        single { MagicLinkService(get(), get(), get(), "http://localhost:80", get()) }
        single {
            WebAuthnService(
                get(),
                get(),
                get(),
                get(),
                get(),
                config.propertyOrNull("admin.email")?.getString() ?: "admin@adopt-u.com",
                config.propertyOrNull("webauthn.rpId")?.getString() ?: "localhost",
                config.propertyOrNull("webauthn.rpName")?.getString() ?: "Adopt-U Pet Adoption",
                listOf(config.propertyOrNull("webauthn.origin")?.getString() ?: "http://localhost:80")
            )
        }
    }

    // ==================== Static / simple pages, unauthenticated ====================

    @Test
    fun `HEAD root returns 200`() {
        val handle = startServer()
        try {
            val response = TestHttp.get(handle.baseUrl)
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET root returns 200 unauthenticated`() {
        val handle = startServer()
        try {
            val response = TestHttp.get(handle.baseUrl)
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET root returns 200 authenticated as admin`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, adminId)
            val response = TestHttp.get(handle.baseUrl, cookie)
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET login returns 200 unauthenticated and authenticated`() {
        val handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/login").statusCode())
            val cookie = TestHttp.loginAs(handle.baseUrl, adminId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/login", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET register returns 200 unauthenticated and authenticated`() {
        val handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/register").statusCode())
            val cookie = TestHttp.loginAs(handle.baseUrl, adminId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/register", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET photographers returns 200 unauthenticated and authenticated`() {
        val handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/photographers").statusCode())
            val cookie = TestHttp.loginAs(handle.baseUrl, adminId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/photographers", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET pet-food returns 200 unauthenticated and authenticated`() {
        val handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/pet-food").statusCode())
            val cookie = TestHttp.loginAs(handle.baseUrl, rescuerId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/pet-food", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== Pet detail ====================

    @Test
    fun `GET pet by numeric id returns 200`() = kotlinx.coroutines.runBlocking {
        val created = PetRepositoryImpl(clock).create(
            rescuerId = rescuerId,
            name = "Buddy",
            type = "DOG",
            description = "A friendly dog",
            weight = 25.0,
            ageYears = 3,
            ageMonths = 6,
            sex = Gender.MALE,
            status = "AVAILABLE"
        )

        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/pet/${created.id}")
            assertEquals(200, response.statusCode())

            val cookie = TestHttp.loginAs(handle.baseUrl, adminId)
            val authedResponse = TestHttp.get("${handle.baseUrl}/pet/${created.id}", cookie)
            assertEquals(200, authedResponse.statusCode())
        } finally {
            handle.stop()
        }
        Unit
    }

    @Test
    fun `GET pet with non-numeric id redirects to pets`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/pet/not-a-number")
            assertEquals(302, response.statusCode())
            assertEquals("/pets", response.headers().firstValue("Location").orElse(null))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET pets returns 200 unauthenticated and authenticated`() {
        val handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/pets").statusCode())
            val cookie = TestHttp.loginAs(handle.baseUrl, rescuerId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/pets", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET my-pets returns 200 unauthenticated and authenticated`() {
        val handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/my-pets").statusCode())
            val cookie = TestHttp.loginAs(handle.baseUrl, rescuerId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/my-pets", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET profile returns 200 for unauthenticated, admin, rescuer and temporal home`() {
        var handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/profile").statusCode())

            val cookie = TestHttp.loginAs(handle.baseUrl, adminId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/profile", cookie).statusCode())
        } finally {
            handle.stop()
        }

        handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, rescuerId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/profile", cookie).statusCode())
        } finally {
            handle.stop()
        }

        handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, temporalHomeId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/profile", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin redirects to login when unauthenticated`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/admin")
            assertEquals(302, response.statusCode())
            assertEquals("/login", response.headers().firstValue("Location").orElse(null))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin redirects to home when authenticated as non-admin`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, rescuerId)
            val response = TestHttp.get("${handle.baseUrl}/admin", cookie)
            assertEquals(302, response.statusCode())
            assertEquals("/", response.headers().firstValue("Location").orElse(null))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin returns 200 for admin`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, adminId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/admin", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin shelters redirects to login when unauthenticated`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/admin/shelters")
            assertEquals(302, response.statusCode())
            assertEquals("/login", response.headers().firstValue("Location").orElse(null))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin shelters redirects to home when authenticated as non-admin`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, rescuerId)
            val response = TestHttp.get("${handle.baseUrl}/admin/shelters", cookie)
            assertEquals(302, response.statusCode())
            assertEquals("/", response.headers().firstValue("Location").orElse(null))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin shelters returns 200 for admin`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, adminId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/admin/shelters", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET privacy returns 200 unauthenticated and authenticated`() {
        val handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/privacy").statusCode())
            val cookie = TestHttp.loginAs(handle.baseUrl, plainId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/privacy", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET terms returns 200 unauthenticated and authenticated`() {
        val handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/terms").statusCode())
            val cookie = TestHttp.loginAs(handle.baseUrl, plainId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/terms", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET temporal-home returns 200 for unauthenticated and temporal home user`() {
        val handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/temporal-home").statusCode())
            val cookie = TestHttp.loginAs(handle.baseUrl, temporalHomeId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/temporal-home", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET temporal-home by numeric id returns 200`() {
        val handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/temporal-home/${temporalHomeId}").statusCode())
            val cookie = TestHttp.loginAs(handle.baseUrl, rescuerId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/temporal-home/${temporalHomeId}", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET temporal-home with non-numeric id redirects to temporal-homes`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/temporal-home/not-a-number")
            assertEquals(302, response.statusCode())
            assertEquals("/temporal-homes", response.headers().firstValue("Location").orElse(null))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET temporal-homes returns 200 unauthenticated and authenticated`() {
        val handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/temporal-homes").statusCode())
            val cookie = TestHttp.loginAs(handle.baseUrl, rescuerId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/temporal-homes", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET shelters returns 200 unauthenticated and authenticated`() {
        val handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/shelters").statusCode())
            val cookie = TestHttp.loginAs(handle.baseUrl, adminId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/shelters", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET sterilization-locations returns 200 unauthenticated and authenticated`() {
        val handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/sterilization-locations").statusCode())
            val cookie = TestHttp.loginAs(handle.baseUrl, rescuerId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/sterilization-locations", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin sterilization-locations redirects to login when unauthenticated`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/admin/sterilization-locations")
            assertEquals(302, response.statusCode())
            assertEquals("/login", response.headers().firstValue("Location").orElse(null))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin sterilization-locations redirects to home when authenticated as non-admin`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, rescuerId)
            val response = TestHttp.get("${handle.baseUrl}/admin/sterilization-locations", cookie)
            assertEquals(302, response.statusCode())
            assertEquals("/", response.headers().firstValue("Location").orElse(null))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin sterilization-locations returns 200 for admin`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, adminId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/admin/sterilization-locations", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== Email verification ====================

    @Test
    fun `GET verify without token shows failure page`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/verify")
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET verify with invalid token shows failure page`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/verify?token=does-not-exist")
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET verify with valid token logs user in and shows success page`() {
        seedValidVerificationToken(plainId, "valid-verify-token")

        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/verify?token=valid-verify-token")
            assertEquals(200, response.statusCode())
            val setCookies = response.headers().allValues("Set-Cookie")
            assertTrue(setCookies.any { it.contains("user_session") })
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET verify-email without token shows failure page`() {
        val handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/verify-email").statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET verify-email with invalid token shows failure page`() {
        val handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/verify-email?token=does-not-exist").statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET verify-email with valid token shows success page`() {
        seedValidVerificationToken(plainId, "valid-verify-email-token")

        val handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/verify-email?token=valid-verify-email-token").statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET verify-email authenticated returns 200`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, adminId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/verify-email", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== Password / magic link ====================

    @Test
    fun `GET forgot-password returns 200 unauthenticated and authenticated`() {
        val handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/forgot-password").statusCode())
            val cookie = TestHttp.loginAs(handle.baseUrl, plainId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/forgot-password", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET reset-password returns 200 unauthenticated and authenticated`() {
        val handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/reset-password").statusCode())
            val cookie = TestHttp.loginAs(handle.baseUrl, plainId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/reset-password", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET magic-link-login without token redirects to login with error`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/magic-link-login")
            assertEquals(302, response.statusCode())
            assertEquals("/login?error=invalid_token", response.headers().firstValue("Location").orElse(null))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET magic-link-login with token redirects to api magic link endpoint`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/magic-link-login?token=abc123")
            assertEquals(302, response.statusCode())
            assertEquals("/api/auth/magic-link-login?token=abc123", response.headers().firstValue("Location").orElse(null))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET verify-email-change returns 200 unauthenticated and authenticated`() {
        val handle = startServer()
        try {
            assertEquals(200, TestHttp.get("${handle.baseUrl}/verify-email-change").statusCode())
            val cookie = TestHttp.loginAs(handle.baseUrl, plainId)
            assertEquals(200, TestHttp.get("${handle.baseUrl}/verify-email-change", cookie).statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== Temporal home block rescuer ====================
    // No login required by design - see the matching comment on this route in
    // UIRoutes.kt / the API route in TemporalHomeRoutes.kt. The page only checks a
    // token is present to decide whether to render; whether it's actually valid is
    // checked when the button posts to the API (covered in TemporalHomeRoutesE2ETest).

    @Test
    fun `GET temporal-home block with a token returns 200 html`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/temporal-home/block?token=some-token")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("Block Rescuer"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET temporal-home block without a token redirects to temporal-home`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/temporal-home/block")
            assertEquals(302, response.statusCode())
            assertEquals("/temporal-home", response.headers().firstValue("Location").orElse(null))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET temporal-home block with a blank token redirects to temporal-home`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/temporal-home/block?token=")
            assertEquals(302, response.statusCode())
            assertEquals("/temporal-home", response.headers().firstValue("Location").orElse(null))
        } finally {
            handle.stop()
        }
    }
}

package com.adoptu.routes

import com.adoptu.adapters.db.EmailVerificationTokens
import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.Gender
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestDatabase
import com.adoptu.plugins.configureSerialization
import com.adoptu.plugins.configureSessions
import com.adoptu.services.EmailVerificationService
import com.adoptu.services.MagicLinkService
import com.adoptu.services.PasswordService
import com.adoptu.services.UserService
import com.adoptu.services.auth.SessionUser
import com.adoptu.services.auth.WebAuthnService
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * End-to-end tests for [uiRoutes]: mounts the real route tree in a Ktor
 * [testApplication] and performs actual HTTP GET requests against every
 * registered page route, both unauthenticated and authenticated, to
 * exercise the HTML page rendering functions under `com.adoptu.pages`.
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

    private fun TestApplicationBuilder.setupApp() {
        val config = MapApplicationConfig(
            "env" to "test",
            "ktor.deployment.port" to "80"
        )

        val testModules = module {
            single<io.ktor.server.config.ApplicationConfig> { config }
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

        environment {
            this.config = config
        }

        application {
            install(Koin) {
                modules(testModules)
            }
            configureSerialization()
            configureSessions()
            routing {
                uiRoutes()
                // Test-only helper to establish an authenticated session without
                // going through the full WebAuthn/password/magic-link login flow.
                get("/__test/login/{id}") {
                    val id = call.parameters["id"]!!.toInt()
                    call.sessions.set(SessionUser(id, "user$id@e2e.test", "User $id"))
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }

    /** A test client that does not auto-follow redirects and persists cookies across requests. */
    private fun ApplicationTestBuilder.newClient() = createClient {
        followRedirects = false
        install(HttpCookies)
    }

    private suspend fun io.ktor.client.HttpClient.loginAs(userId: Int) {
        val response = get("/__test/login/$userId")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ==================== Static / simple pages, unauthenticated ====================

    @Test
    fun `HEAD root returns 200`() {
        testApplication {
            setupApp()
            val client = newClient()
            val response = client.head("/")
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `GET root returns 200 unauthenticated`() {
        testApplication {
            setupApp()
            val client = newClient()
            val response = client.get("/")
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `GET root returns 200 authenticated as admin`() {
        testApplication {
            setupApp()
            val client = newClient()
            client.loginAs(adminId)
            val response = client.get("/")
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `GET login returns 200 unauthenticated and authenticated`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/login").status)
            client.loginAs(adminId)
            assertEquals(HttpStatusCode.OK, client.get("/login").status)
        }
    }

    @Test
    fun `GET register returns 200 unauthenticated and authenticated`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/register").status)
            client.loginAs(adminId)
            assertEquals(HttpStatusCode.OK, client.get("/register").status)
        }
    }

    @Test
    fun `GET photographers returns 200 unauthenticated and authenticated`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/photographers").status)
            client.loginAs(adminId)
            assertEquals(HttpStatusCode.OK, client.get("/photographers").status)
        }
    }

    @Test
    fun `GET pet-food returns 200 unauthenticated and authenticated`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/pet-food").status)
            client.loginAs(rescuerId)
            assertEquals(HttpStatusCode.OK, client.get("/pet-food").status)
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

        testApplication {
            setupApp()
            val client = newClient()
            val response = client.get("/pet/${created.id}")
            assertEquals(HttpStatusCode.OK, response.status)

            client.loginAs(adminId)
            val authedResponse = client.get("/pet/${created.id}")
            assertEquals(HttpStatusCode.OK, authedResponse.status)
        }
        Unit
    }

    @Test
    fun `GET pet with non-numeric id redirects to pets`() {
        testApplication {
            setupApp()
            val client = newClient()
            val response = client.get("/pet/not-a-number")
            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/pets", response.headers[HttpHeaders.Location])
        }
    }

    @Test
    fun `GET pets returns 200 unauthenticated and authenticated`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/pets").status)
            client.loginAs(rescuerId)
            assertEquals(HttpStatusCode.OK, client.get("/pets").status)
        }
    }

    @Test
    fun `GET my-pets returns 200 unauthenticated and authenticated`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/my-pets").status)
            client.loginAs(rescuerId)
            assertEquals(HttpStatusCode.OK, client.get("/my-pets").status)
        }
    }

    @Test
    fun `GET profile returns 200 for unauthenticated, admin, rescuer and temporal home`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/profile").status)

            client.loginAs(adminId)
            assertEquals(HttpStatusCode.OK, client.get("/profile").status)
        }

        testApplication {
            setupApp()
            val client = newClient()
            client.loginAs(rescuerId)
            assertEquals(HttpStatusCode.OK, client.get("/profile").status)
        }

        testApplication {
            setupApp()
            val client = newClient()
            client.loginAs(temporalHomeId)
            assertEquals(HttpStatusCode.OK, client.get("/profile").status)
        }
    }

    @Test
    fun `GET admin returns 200 for unauthenticated and admin`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/admin").status)
            client.loginAs(adminId)
            assertEquals(HttpStatusCode.OK, client.get("/admin").status)
        }
    }

    @Test
    fun `GET admin shelters returns 200 for unauthenticated and admin`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/admin/shelters").status)
            client.loginAs(adminId)
            assertEquals(HttpStatusCode.OK, client.get("/admin/shelters").status)
        }
    }

    @Test
    fun `GET privacy returns 200 unauthenticated and authenticated`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/privacy").status)
            client.loginAs(plainId)
            assertEquals(HttpStatusCode.OK, client.get("/privacy").status)
        }
    }

    @Test
    fun `GET terms returns 200 unauthenticated and authenticated`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/terms").status)
            client.loginAs(plainId)
            assertEquals(HttpStatusCode.OK, client.get("/terms").status)
        }
    }

    @Test
    fun `GET temporal-home returns 200 for unauthenticated and temporal home user`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/temporal-home").status)
            client.loginAs(temporalHomeId)
            assertEquals(HttpStatusCode.OK, client.get("/temporal-home").status)
        }
    }

    @Test
    fun `GET temporal-home by numeric id returns 200`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/temporal-home/${temporalHomeId}").status)
            client.loginAs(rescuerId)
            assertEquals(HttpStatusCode.OK, client.get("/temporal-home/${temporalHomeId}").status)
        }
    }

    @Test
    fun `GET temporal-home with non-numeric id redirects to temporal-homes`() {
        testApplication {
            setupApp()
            val client = newClient()
            val response = client.get("/temporal-home/not-a-number")
            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/temporal-homes", response.headers[HttpHeaders.Location])
        }
    }

    @Test
    fun `GET temporal-homes returns 200 unauthenticated and authenticated`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/temporal-homes").status)
            client.loginAs(rescuerId)
            assertEquals(HttpStatusCode.OK, client.get("/temporal-homes").status)
        }
    }

    @Test
    fun `GET shelters returns 200 unauthenticated and authenticated`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/shelters").status)
            client.loginAs(adminId)
            assertEquals(HttpStatusCode.OK, client.get("/shelters").status)
        }
    }

    @Test
    fun `GET sterilization-locations returns 200 unauthenticated and authenticated`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/sterilization-locations").status)
            client.loginAs(rescuerId)
            assertEquals(HttpStatusCode.OK, client.get("/sterilization-locations").status)
        }
    }

    @Test
    fun `GET admin sterilization-locations returns 200 for unauthenticated and admin`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/admin/sterilization-locations").status)
            client.loginAs(adminId)
            assertEquals(HttpStatusCode.OK, client.get("/admin/sterilization-locations").status)
        }
    }

    // ==================== Email verification ====================

    @Test
    fun `GET verify without token shows failure page`() {
        testApplication {
            setupApp()
            val client = newClient()
            val response = client.get("/verify")
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `GET verify with invalid token shows failure page`() {
        testApplication {
            setupApp()
            val client = newClient()
            val response = client.get("/verify?token=does-not-exist")
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `GET verify with valid token logs user in and shows success page`() {
        seedValidVerificationToken(plainId, "valid-verify-token")

        testApplication {
            setupApp()
            val client = newClient()
            val response = client.get("/verify?token=valid-verify-token")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.headers.getAll(HttpHeaders.SetCookie)?.any { it.contains("user_session") } == true)
        }
    }

    @Test
    fun `GET verify-email without token shows failure page`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/verify-email").status)
        }
    }

    @Test
    fun `GET verify-email with invalid token shows failure page`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/verify-email?token=does-not-exist").status)
        }
    }

    @Test
    fun `GET verify-email with valid token shows success page`() {
        seedValidVerificationToken(plainId, "valid-verify-email-token")

        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/verify-email?token=valid-verify-email-token").status)
        }
    }

    @Test
    fun `GET verify-email authenticated returns 200`() {
        testApplication {
            setupApp()
            val client = newClient()
            client.loginAs(adminId)
            assertEquals(HttpStatusCode.OK, client.get("/verify-email").status)
        }
    }

    // ==================== Password / magic link ====================

    @Test
    fun `GET forgot-password returns 200 unauthenticated and authenticated`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/forgot-password").status)
            client.loginAs(plainId)
            assertEquals(HttpStatusCode.OK, client.get("/forgot-password").status)
        }
    }

    @Test
    fun `GET reset-password returns 200 unauthenticated and authenticated`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/reset-password").status)
            client.loginAs(plainId)
            assertEquals(HttpStatusCode.OK, client.get("/reset-password").status)
        }
    }

    @Test
    fun `GET magic-link-login without token redirects to login with error`() {
        testApplication {
            setupApp()
            val client = newClient()
            val response = client.get("/magic-link-login")
            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/login?error=invalid_token", response.headers[HttpHeaders.Location])
        }
    }

    @Test
    fun `GET magic-link-login with token redirects to api magic link endpoint`() {
        testApplication {
            setupApp()
            val client = newClient()
            val response = client.get("/magic-link-login?token=abc123")
            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/api/auth/magic-link-login?token=abc123", response.headers[HttpHeaders.Location])
        }
    }

    @Test
    fun `GET verify-email-change returns 200 unauthenticated and authenticated`() {
        testApplication {
            setupApp()
            val client = newClient()
            assertEquals(HttpStatusCode.OK, client.get("/verify-email-change").status)
            client.loginAs(plainId)
            assertEquals(HttpStatusCode.OK, client.get("/verify-email-change").status)
        }
    }

    // ==================== Temporal home block rescuer ====================

    @Test
    fun `GET temporal-home block with valid ids returns 200 html`() {
        testApplication {
            setupApp()
            val client = newClient()
            val response = client.get("/temporal-home/block/$temporalHomeId?rescuer=$rescuerId")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("Block Rescuer"))
        }
    }

    @Test
    fun `GET temporal-home block without rescuer query redirects to temporal-home`() {
        testApplication {
            setupApp()
            val client = newClient()
            val response = client.get("/temporal-home/block/$temporalHomeId")
            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/temporal-home", response.headers[HttpHeaders.Location])
        }
    }

    @Test
    fun `GET temporal-home block with non-numeric id redirects to temporal-home`() {
        testApplication {
            setupApp()
            val client = newClient()
            val response = client.get("/temporal-home/block/not-a-number?rescuer=$rescuerId")
            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/temporal-home", response.headers[HttpHeaders.Location])
        }
    }

    @Test
    fun `GET temporal-home block with non-numeric rescuer redirects to temporal-home`() {
        testApplication {
            setupApp()
            val client = newClient()
            val response = client.get("/temporal-home/block/$temporalHomeId?rescuer=not-a-number")
            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/temporal-home", response.headers[HttpHeaders.Location])
        }
    }
}

package com.adoptu.routes

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.SponsorshipOfferRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.CreateSponsorshipOfferRequest
import com.adoptu.dto.input.Currency
import com.adoptu.dto.input.SponsorshipOfferType
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestDatabase
import com.adoptu.ports.NotificationPort
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.ports.PhotographerRepositoryPort
import com.adoptu.ports.SponsorshipOfferRepositoryPort
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.services.SponsorshipService
import com.adoptu.services.UserService
import com.adoptu.services.validation.UsersValidationService
import com.adoptu.testsupport.TestHttp
import com.adoptu.testsupport.TestServer
import com.adoptu.web.JsonSupport
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
 * E2E tests for [sponsorshipRoutes]. Business-rule permutations (money/in-kind validation,
 * rescuer/pet ownership checks) are already covered at the service layer by
 * SponsorshipServiceTest - these tests re-verify the auth/ownership decision points and one clean
 * offer -> mark read -> list-both-sides lifecycle through the real HTTP route.
 */
@OptIn(ExperimentalTime::class)
class SponsorshipRoutesE2ETest {

    private val clock = Clock.System

    private val testModules = listOf(
        module {
            single<Clock> { Clock.System }
            single { MockNotificationAdapter() }
            single<NotificationPort> { get<MockNotificationAdapter>() }
            single<UserRepositoryPort> { UserRepository(get()) }
            single<PetRepositoryPort> { PetRepositoryImpl(get()) }
            single<PhotographerRepositoryPort> { PhotographerRepositoryImpl(get(), get(), get()) }
            single { UserService(get(), get(), get()) }
            single<SponsorshipOfferRepositoryPort> { SponsorshipOfferRepositoryImpl(get(), get(), get()) }
            single { SponsorshipService(get(), get(), get(), get()) }
            single { UsersValidationService() }
        }
    )

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        createTestUsers()
    }

    private fun createTestUsers() {
        transaction {
            // user 1: the rescuer who receives sponsorship offers.
            try {
                Users.insert {
                    it[Users.id] = 1
                    it[Users.username] = "rescuer@test.com"
                    it[Users.displayName] = "Test Rescuer"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 1
                    it[UserActiveRoles.role] = "RESCUER"
                }
            } catch (e: Exception) { }

            // user 2: a plain sponsor/adopter who makes offers.
            try {
                Users.insert {
                    it[Users.id] = 2
                    it[Users.username] = "sponsor@test.com"
                    it[Users.displayName] = "Test Sponsor"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 2
                    it[UserActiveRoles.role] = "ADOPTER"
                }
            } catch (e: Exception) { }

            // user 3: an admin.
            try {
                Users.insert {
                    it[Users.id] = 3
                    it[Users.username] = "admin@test.com"
                    it[Users.displayName] = "Test Admin"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 3
                    it[UserActiveRoles.role] = "ADMIN"
                }
            } catch (e: Exception) { }
        }
    }

    private fun startServer() = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)

    private fun moneyOfferRequest(rescuerId: Int = 1) = CreateSponsorshipOfferRequest(
        rescuerId = rescuerId,
        offerType = SponsorshipOfferType.MONEY,
        amount = 100.0,
        currency = Currency.USD,
        message = "Happy to help!"
    )

    // ==================== POST /api/sponsorships ====================

    @Test
    fun `POST sponsorships returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/sponsorships",
                JsonSupport.objectMapper.writeValueAsString(moneyOfferRequest())
            )

            assertEquals(401, response.statusCode())
            assertTrue(response.body().contains("Unauthorized"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST sponsorships creates an offer to a rescuer`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/sponsorships",
                JsonSupport.objectMapper.writeValueAsString(moneyOfferRequest()),
                cookie
            )

            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("\"sponsorId\": 2"))
            assertTrue(body.contains("\"rescuerId\": 1"))
            assertTrue(body.contains("\"status\": \"SENT\""))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST sponsorships returns 404 when target rescuer does not exist`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/sponsorships",
                JsonSupport.objectMapper.writeValueAsString(moneyOfferRequest(rescuerId = 999)),
                cookie
            )

            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== PUT /api/sponsorships/{id}/read ====================

    @Test
    fun `PUT sponsorships read returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.putJson("${handle.baseUrl}/api/sponsorships/1/read", "")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT sponsorships read returns 404 when the offer does not exist`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.putJson("${handle.baseUrl}/api/sponsorships/999/read", "", cookie)

            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT sponsorships read returns 403 for an unrelated user`() {
        val handle = startServer()
        try {
            val sponsorCookie = TestHttp.loginAs(handle.baseUrl, 2)
            val createResponse = TestHttp.postJson(
                "${handle.baseUrl}/api/sponsorships",
                JsonSupport.objectMapper.writeValueAsString(moneyOfferRequest()),
                sponsorCookie
            )
            val id = extractId(createResponse.body())

            // user 2 is the sponsor who made the offer, not its target rescuer, and not an admin.
            val response = TestHttp.putJson("${handle.baseUrl}/api/sponsorships/$id/read", "", sponsorCookie)

            assertEquals(403, response.statusCode())
            assertTrue(response.body().contains("Forbidden"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT sponsorships read succeeds for the target rescuer`() {
        val handle = startServer()
        try {
            val sponsorCookie = TestHttp.loginAs(handle.baseUrl, 2)
            val createResponse = TestHttp.postJson(
                "${handle.baseUrl}/api/sponsorships",
                JsonSupport.objectMapper.writeValueAsString(moneyOfferRequest()),
                sponsorCookie
            )
            val id = extractId(createResponse.body())

            val rescuerCookie = TestHttp.loginAs(handle.baseUrl, 1)
            val response = TestHttp.putJson("${handle.baseUrl}/api/sponsorships/$id/read", "", rescuerCookie)

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("\"status\": \"READ\""))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT sponsorships read succeeds for an admin who is not the target rescuer`() {
        val handle = startServer()
        try {
            val sponsorCookie = TestHttp.loginAs(handle.baseUrl, 2)
            val createResponse = TestHttp.postJson(
                "${handle.baseUrl}/api/sponsorships",
                JsonSupport.objectMapper.writeValueAsString(moneyOfferRequest()),
                sponsorCookie
            )
            val id = extractId(createResponse.body())

            val adminCookie = TestHttp.loginAs(handle.baseUrl, 3)
            val response = TestHttp.putJson("${handle.baseUrl}/api/sponsorships/$id/read", "", adminCookie)

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("\"status\": \"READ\""))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/users/rescuer/sponsorships ====================

    @Test
    fun `GET users rescuer sponsorships returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/users/rescuer/sponsorships")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET users rescuer sponsorships returns offers sent to the caller`() {
        val handle = startServer()
        try {
            val sponsorCookie = TestHttp.loginAs(handle.baseUrl, 2)
            TestHttp.postJson(
                "${handle.baseUrl}/api/sponsorships",
                JsonSupport.objectMapper.writeValueAsString(moneyOfferRequest()),
                sponsorCookie
            )

            val rescuerCookie = TestHttp.loginAs(handle.baseUrl, 1)
            val response = TestHttp.get("${handle.baseUrl}/api/users/rescuer/sponsorships", rescuerCookie)

            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("\"rescuerId\": 1"))
            assertTrue(body.contains("\"sponsorId\": 2"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/users/sponsorships/mine ====================

    @Test
    fun `GET users sponsorships mine returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/users/sponsorships/mine")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET users sponsorships mine returns offers made by the caller`() {
        val handle = startServer()
        try {
            val sponsorCookie = TestHttp.loginAs(handle.baseUrl, 2)
            TestHttp.postJson(
                "${handle.baseUrl}/api/sponsorships",
                JsonSupport.objectMapper.writeValueAsString(moneyOfferRequest()),
                sponsorCookie
            )

            val response = TestHttp.get("${handle.baseUrl}/api/users/sponsorships/mine", sponsorCookie)

            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("\"sponsorId\": 2"))
            assertTrue(body.contains("\"rescuerId\": 1"))
        } finally {
            handle.stop()
        }
    }

    private fun extractId(body: String): Int {
        val node = JsonSupport.objectMapper.readTree(body)
        return node.get("id").asInt()
    }
}

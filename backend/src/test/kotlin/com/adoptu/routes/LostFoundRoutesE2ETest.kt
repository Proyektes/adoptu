package com.adoptu.routes

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.LostFoundRepositoryImpl
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.LostFoundKind
import com.adoptu.dto.input.SubmitLostFoundReportRequest
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestDatabase
import com.adoptu.ports.CaptchaPort
import com.adoptu.ports.GeocodeResult
import com.adoptu.ports.GeocodingPort
import com.adoptu.ports.LostFoundRepositoryPort
import com.adoptu.ports.NotificationPort
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.ports.PhotographerRepositoryPort
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.services.LostFoundService
import com.adoptu.services.UserService
import com.adoptu.testsupport.TestHttp
import com.adoptu.testsupport.TestServer
import com.adoptu.web.JsonSupport
import com.universaliun.ratelimit.common.InMemoryRateLimitStateAdapter
import com.universaliun.ratelimit.common.RateLimiter
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private object FakeGeocodingPort : GeocodingPort {
    override suspend fun geocode(country: String, state: String?, city: String): GeocodeResult? =
        GeocodeResult(latitude = 0.0, longitude = 0.0, radiusKm = 50.0)
}

private object AlwaysPassCaptchaPort : CaptchaPort {
    override suspend fun verify(token: String, remoteIp: String?): Boolean = true
}

@OptIn(ExperimentalTime::class)
class LostFoundRoutesE2ETest {

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
            single<LostFoundRepositoryPort> { LostFoundRepositoryImpl(get()) }
            single<GeocodingPort> { FakeGeocodingPort }
            single<CaptchaPort> { AlwaysPassCaptchaPort }
            single { RateLimiter(InMemoryRateLimitStateAdapter()) }
            single { LostFoundService(get(), get(), get(), get(), get()) }
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
            try {
                Users.insert {
                    it[Users.id] = 1
                    it[Users.username] = "owner@test.com"
                    it[Users.displayName] = "Report Owner"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 1
                    it[UserActiveRoles.role] = "RESCUER"
                }
            } catch (e: Exception) { }

            try {
                Users.insert {
                    it[Users.id] = 2
                    it[Users.username] = "other@test.com"
                    it[Users.displayName] = "Someone Else"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 2
                    it[UserActiveRoles.role] = "RESCUER"
                }
            } catch (e: Exception) { }
        }
    }

    private fun startServer() = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)

    private fun submitRequestJson(
        kind: LostFoundKind = LostFoundKind.LOST,
        description: String = "Small brown dog, friendly, missing collar",
        reporterEmail: String? = null,
        captchaToken: String? = null,
        country: String = "United States"
    ): String {
        val request = SubmitLostFoundReportRequest(
            kind = kind,
            petType = "Dog",
            description = description,
            reporterEmail = reporterEmail,
            captchaToken = captchaToken,
            country = country,
            state = "CA",
            city = "LA",
            latitude = 34.05,
            longitude = -118.25
        )
        return JsonSupport.objectMapper.writeValueAsString(request)
    }

    // Creates a report directly via the HTTP API and returns its id, for use as a fixture in
    // other tests (browse/get/contact/resolve).
    private fun createReportViaApi(baseUrl: String, cookie: String? = null, kind: LostFoundKind = LostFoundKind.LOST): Int {
        val response = if (cookie != null) {
            TestHttp.postJson("$baseUrl/api/lost-found/reports", submitRequestJson(kind = kind), cookie)
        } else {
            TestHttp.postJson(
                "$baseUrl/api/lost-found/reports",
                submitRequestJson(kind = kind, reporterEmail = "anon@test.com", captchaToken = "any-token")
            )
        }
        assertEquals(200, response.statusCode())
        val node = JsonSupport.objectMapper.readTree(response.body())
        return node.get("id").asInt()
    }

    // ==================== POST /api/lost-found/reports ====================

    @Test
    fun `POST lost-found reports succeeds anonymously with captcha token and reporter email`() {
        val handle = startServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/lost-found/reports",
                submitRequestJson(reporterEmail = "finder@test.com", captchaToken = "valid-token")
            )

            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("\"reporterEmail\": \"finder@test.com\""))
            assertTrue(body.contains("\"status\": \"OPEN\""))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST lost-found reports fails anonymously without captcha token`() {
        val handle = startServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/lost-found/reports",
                submitRequestJson(reporterEmail = "finder@test.com", captchaToken = null)
            )

            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("CAPTCHA"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST lost-found reports fails anonymously without reporter email`() {
        val handle = startServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/lost-found/reports",
                submitRequestJson(reporterEmail = null, captchaToken = "valid-token")
            )

            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("email"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST lost-found reports succeeds authenticated using session email`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/lost-found/reports",
                submitRequestJson(),
                cookie
            )

            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("\"reporterEmail\": \"owner@test.com\""))
            assertTrue(body.contains("\"reporterUserId\": 1"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/lost-found/reports (browse) ====================

    @Test
    fun `GET lost-found reports returns 400 when kind is missing`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/lost-found/reports?country=United%20States")
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("kind is required"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET lost-found reports returns 400 when country is missing`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/lost-found/reports?kind=LOST")
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Country is required"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET lost-found reports is public and returns matching open reports`() {
        val handle = startServer()
        try {
            createReportViaApi(handle.baseUrl, cookie = null, kind = LostFoundKind.LOST)

            val response = TestHttp.get("${handle.baseUrl}/api/lost-found/reports?kind=LOST&country=United%20States")

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Small brown dog"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET lost-found reports excludes reports of the opposite kind`() {
        val handle = startServer()
        try {
            createReportViaApi(handle.baseUrl, cookie = null, kind = LostFoundKind.LOST)

            val response = TestHttp.get("${handle.baseUrl}/api/lost-found/reports?kind=FOUND&country=United%20States")

            assertEquals(200, response.statusCode())
            assertEquals("[]", response.body())
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/lost-found/reports/{id} ====================

    @Test
    fun `GET lost-found reports by id returns 200 for existing report`() {
        val handle = startServer()
        try {
            val id = createReportViaApi(handle.baseUrl)

            val response = TestHttp.get("${handle.baseUrl}/api/lost-found/reports/$id")

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Small brown dog"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET lost-found reports by id returns 404 for missing report`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/lost-found/reports/999999")

            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/lost-found/reports/{id}/contact ====================

    @Test
    fun `POST lost-found reports contact relays a message without auth`() {
        val handle = startServer()
        try {
            val id = createReportViaApi(handle.baseUrl)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/lost-found/reports/$id/contact",
                """{"fromEmail":"passerby@test.com","message":"I think I found your pet!"}"""
            )

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("\"success\": true"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST lost-found reports contact returns error for missing report`() {
        val handle = startServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/lost-found/reports/999999/contact",
                """{"fromEmail":"passerby@test.com","message":"Hello"}"""
            )

            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Report not found"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/lost-found/resolve (token-based, unauthenticated) =========

    @Test
    fun `GET lost-found resolve returns 400 when token is missing`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/lost-found/resolve")
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Token is required"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET lost-found resolve resolves the report for a valid token`() {
        val handle = startServer()
        try {
            val createResponse = TestHttp.postJson(
                "${handle.baseUrl}/api/lost-found/reports",
                submitRequestJson(reporterEmail = "finder@test.com", captchaToken = "valid-token")
            )
            val node = JsonSupport.objectMapper.readTree(createResponse.body())
            val token = node.get("resolveToken").asText()

            val response = TestHttp.get("${handle.baseUrl}/api/lost-found/resolve?token=$token")

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("\"status\": \"RESOLVED\""))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET lost-found resolve returns 409 for an invalid token`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/lost-found/resolve?token=not-a-real-token")

            assertEquals(409, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/lost-found/reports/{id}/resolve ====================

    @Test
    fun `POST lost-found reports resolve returns 401 when no session`() {
        val handle = startServer()
        try {
            val id = createReportViaApi(handle.baseUrl)

            val response = TestHttp.post("${handle.baseUrl}/api/lost-found/reports/$id/resolve")

            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST lost-found reports resolve succeeds for the report owner`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)
            val id = createReportViaApi(handle.baseUrl, cookie = cookie)

            val response = TestHttp.post("${handle.baseUrl}/api/lost-found/reports/$id/resolve", cookie)

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("\"status\": \"RESOLVED\""))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST lost-found reports resolve returns 409 for a non-owner`() {
        val handle = startServer()
        try {
            val ownerCookie = TestHttp.loginAs(handle.baseUrl, 1)
            val id = createReportViaApi(handle.baseUrl, cookie = ownerCookie)

            val otherCookie = TestHttp.loginAs(handle.baseUrl, 2)
            val response = TestHttp.post("${handle.baseUrl}/api/lost-found/reports/$id/resolve", otherCookie)

            assertEquals(409, response.statusCode())
            assertTrue(response.body().contains("didn't file this report"))
        } finally {
            handle.stop()
        }
    }
}

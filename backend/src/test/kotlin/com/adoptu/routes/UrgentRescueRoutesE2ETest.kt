package com.adoptu.routes

import com.adoptu.adapters.db.UrgentReportPages
import com.adoptu.adapters.db.UrgentReports
import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.UrgentRescueRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.CreateUrgentRescuerProfileRequest
import com.adoptu.dto.input.LocationInputMode
import com.adoptu.dto.input.SubmitUrgentReportRequest
import com.adoptu.dto.input.UpdateUrgentRescuerProfileRequest
import com.adoptu.dto.input.UrgentDangerType
import com.adoptu.dto.input.UrgentReportPageStatus
import com.adoptu.dto.input.UrgentReportStatus
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestDatabase
import com.adoptu.ports.CaptchaPort
import com.adoptu.ports.GeocodeResult
import com.adoptu.ports.GeocodingPort
import com.adoptu.ports.NotificationPort
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.ports.PhotographerRepositoryPort
import com.adoptu.ports.SmsNotificationPort
import com.adoptu.ports.UrgentRescueRepositoryPort
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.services.UrgentRescueService
import com.adoptu.services.UserService
import com.adoptu.testsupport.TestHttp
import com.adoptu.testsupport.TestServer
import com.adoptu.web.JsonSupport
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class UrgentRescueRoutesE2ETest {

    private val clock = Clock.System

    private object FakeSmsPort : SmsNotificationPort {
        override suspend fun sendUrgentRescueAlert(
            phone: String,
            description: String,
            dangerType: String,
            locationLabel: String,
            acceptLink: String
        ): Boolean = true
    }

    private object FakeGeocodingPort : GeocodingPort {
        override suspend fun geocode(country: String, state: String?, city: String): GeocodeResult? =
            GeocodeResult(latitude = 0.0, longitude = 0.0, radiusKm = 50.0)
    }

    private object AlwaysPassCaptchaPort : CaptchaPort {
        override suspend fun verify(token: String, remoteIp: String?): Boolean = true
    }

    private val testModules = listOf(
        module {
            single<Clock> { Clock.System }
            single { MockNotificationAdapter() }
            single<NotificationPort> { get<MockNotificationAdapter>() }
            single<UserRepositoryPort> { UserRepository(get()) }
            single<UrgentRescueRepositoryPort> { UrgentRescueRepositoryImpl(get()) }
            single<SmsNotificationPort> { FakeSmsPort }
            single<GeocodingPort> { FakeGeocodingPort }
            single<CaptchaPort> { AlwaysPassCaptchaPort }
            single { com.universaliun.ratelimit.common.RateLimiter(com.universaliun.ratelimit.common.InMemoryRateLimitStateAdapter()) }
            single { UrgentRescueService(get(), get(), get(), get(), get(), get(), get(), "http://localhost:80") }
            single<PetRepositoryPort> { PetRepositoryImpl(get()) }
            single<PhotographerRepositoryPort> { PhotographerRepositoryImpl(get(), get(), get()) }
            single { UserService(get(), get(), get()) }
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
                    it[Users.username] = "rescuer@test.com"
                    it[Users.displayName] = "Test Rescuer"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 1
                    it[UserActiveRoles.role] = "URGENT_RESCUER"
                }
            } catch (e: Exception) { }

            try {
                Users.insert {
                    it[Users.id] = 2
                    it[Users.username] = "plainuser@test.com"
                    it[Users.displayName] = "Plain User"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 2
                    it[UserActiveRoles.role] = "ADOPTER"
                }
            } catch (e: Exception) { }
        }
    }

    private fun startServer() = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)

    // Directly seeds report + page rows, bypassing UrgentRescueService.submitReport's async
    // matchAndPage (launched on a background coroutine) so accept tests aren't racing it.
    private fun createReportInDb(reporterUserId: Int? = null, reporterEmail: String = "victim@test.com"): Int {
        return transaction {
            UrgentReports.insert {
                it[UrgentReports.reporterUserId] = reporterUserId
                it[UrgentReports.reporterEmail] = reporterEmail
                it[UrgentReports.reporterPhone] = null
                it[UrgentReports.description] = "Injured dog near the highway"
                it[UrgentReports.dangerType] = UrgentDangerType.INJURED.name
                it[UrgentReports.photoUrl] = null
                it[UrgentReports.latitude] = 40.0
                it[UrgentReports.longitude] = -74.0
                it[UrgentReports.locationLabel] = "Some City, Some Country"
                it[UrgentReports.status] = UrgentReportStatus.PENDING.name
                it[UrgentReports.createdAt] = clock.now().toEpochMilliseconds()
            } get UrgentReports.id
        }
    }

    private fun createReportPageInDb(reportId: Int, rescuerId: Int, token: String = "test-token-$reportId-$rescuerId"): Int {
        return transaction {
            UrgentReportPages.insert {
                it[UrgentReportPages.reportId] = reportId
                it[UrgentReportPages.rescuerId] = rescuerId
                it[UrgentReportPages.token] = token
                it[UrgentReportPages.status] = UrgentReportPageStatus.PAGED.name
                it[UrgentReportPages.createdAt] = clock.now().toEpochMilliseconds()
            } get UrgentReportPages.id
        }
    }

    // ==================== GET/POST/PUT /api/urgent-rescuers/me ====================

    @Test
    fun `GET urgent-rescuers me returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/urgent-rescuers/me")
            assertEquals(401, response.statusCode())
            assertTrue(response.body().contains("Unauthorized"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST urgent-rescuers me returns 401 when no session`() {
        val handle = startServer()
        try {
            val request = CreateUrgentRescuerProfileRequest(
                phone = "+15551234567",
                inputMode = LocationInputMode.COORDINATES,
                latitude = 40.0,
                longitude = -74.0,
                radiusKm = 25.0
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/urgent-rescuers/me",
                JsonSupport.objectMapper.writeValueAsString(request)
            )

            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT urgent-rescuers me returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/urgent-rescuers/me",
                JsonSupport.objectMapper.writeValueAsString(UpdateUrgentRescuerProfileRequest(phone = "+15559999999"))
            )

            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST urgent-rescuers me creates a profile for the authenticated user`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val request = CreateUrgentRescuerProfileRequest(
                phone = "+15551234567",
                inputMode = LocationInputMode.COORDINATES,
                latitude = 40.0,
                longitude = -74.0,
                radiusKm = 25.0
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/urgent-rescuers/me",
                JsonSupport.objectMapper.writeValueAsString(request),
                cookie
            )

            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("+15551234567"))
            assertTrue(body.contains("\"userId\":1") || body.contains("\"userId\": 1"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET urgent-rescuers me returns the created profile`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val createRequest = CreateUrgentRescuerProfileRequest(
                phone = "+15551234567",
                inputMode = LocationInputMode.COORDINATES,
                latitude = 40.0,
                longitude = -74.0,
                radiusKm = 25.0
            )
            TestHttp.postJson(
                "${handle.baseUrl}/api/urgent-rescuers/me",
                JsonSupport.objectMapper.writeValueAsString(createRequest),
                cookie
            )

            val response = TestHttp.get("${handle.baseUrl}/api/urgent-rescuers/me", cookie)

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("+15551234567"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT urgent-rescuers me updates the existing profile`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val createRequest = CreateUrgentRescuerProfileRequest(
                phone = "+15551234567",
                inputMode = LocationInputMode.COORDINATES,
                latitude = 40.0,
                longitude = -74.0,
                radiusKm = 25.0
            )
            TestHttp.postJson(
                "${handle.baseUrl}/api/urgent-rescuers/me",
                JsonSupport.objectMapper.writeValueAsString(createRequest),
                cookie
            )

            val updateRequest = UpdateUrgentRescuerProfileRequest(phone = "+15559999999")
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/urgent-rescuers/me",
                JsonSupport.objectMapper.writeValueAsString(updateRequest),
                cookie
            )

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("+15559999999"))
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/urgent-reports/submit ====================

    @Test
    fun `POST urgent-reports submit succeeds anonymously with captcha and reporter email`() {
        val handle = startServer()
        try {
            val request = SubmitUrgentReportRequest(
                description = "Injured cat by the road",
                dangerType = UrgentDangerType.INJURED,
                reporterEmail = "anon@test.com",
                captchaToken = "valid-token",
                latitude = 40.0,
                longitude = -74.0
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/urgent-reports/submit",
                JsonSupport.objectMapper.writeValueAsString(request)
            )

            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("anon@test.com"))
            assertTrue(body.contains("Injured cat by the road"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST urgent-reports submit returns 400 when anonymous and missing captcha token`() {
        val handle = startServer()
        try {
            val request = SubmitUrgentReportRequest(
                description = "Injured cat by the road",
                dangerType = UrgentDangerType.INJURED,
                reporterEmail = "anon@test.com",
                captchaToken = null,
                latitude = 40.0,
                longitude = -74.0
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/urgent-reports/submit",
                JsonSupport.objectMapper.writeValueAsString(request)
            )

            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST urgent-reports submit succeeds authenticated without a captcha token`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val request = SubmitUrgentReportRequest(
                description = "Starving puppy found",
                dangerType = UrgentDangerType.STARVING,
                captchaToken = null,
                latitude = 40.0,
                longitude = -74.0
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/urgent-reports/submit",
                JsonSupport.objectMapper.writeValueAsString(request),
                cookie
            )

            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("plainuser@test.com"))
            assertTrue(body.contains("Starving puppy found"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/urgent-rescuers/leaderboard ====================

    @Test
    fun `GET urgent-rescuers leaderboard is public and returns 200 with no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/urgent-rescuers/leaderboard")
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/urgent-rescuers/my-pages ====================

    @Test
    fun `GET urgent-rescuers my-pages returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/urgent-rescuers/my-pages")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET urgent-rescuers my-pages returns pending pages for the authenticated rescuer`() {
        val reportId = createReportInDb()
        createReportPageInDb(reportId, rescuerId = 1)

        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.get("${handle.baseUrl}/api/urgent-rescuers/my-pages", cookie)

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Injured dog near the highway"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/urgent-reports/accept (token-based, unauthenticated) ====================

    @Test
    fun `GET urgent-reports accept returns 400 when token is missing`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/urgent-reports/accept")
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET urgent-reports accept succeeds with a valid token and no session`() {
        val reportId = createReportInDb()
        createReportPageInDb(reportId, rescuerId = 1, token = "valid-accept-token")

        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/urgent-reports/accept?token=valid-accept-token")

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("ACCEPTED"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET urgent-reports accept returns 409 for an unknown token`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/urgent-reports/accept?token=does-not-exist")
            assertEquals(409, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/urgent-rescuers/reports/{id}/accept ====================

    @Test
    fun `POST urgent-rescuers reports accept returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.post("${handle.baseUrl}/api/urgent-rescuers/reports/1/accept")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST urgent-rescuers reports accept succeeds for a paged rescuer`() {
        val reportId = createReportInDb()
        createReportPageInDb(reportId, rescuerId = 1)

        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.post("${handle.baseUrl}/api/urgent-rescuers/reports/$reportId/accept", cookie)

            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("\"status\":\"ACCEPTED\"") || body.contains("ACCEPTED"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST urgent-rescuers reports accept returns 409 when already accepted`() {
        val reportId = createReportInDb()
        createReportPageInDb(reportId, rescuerId = 1)
        createReportPageInDb(reportId, rescuerId = 2, token = "test-token-$reportId-2")
        // UserActiveRoles for user 2 defaults to ADOPTER above; acceptAsRescuer only checks
        // pending pages, not role, so that's fine for this race scenario.
        transaction {
            UrgentReports.update({ UrgentReports.id eq reportId }) {
                it[UrgentReports.status] = UrgentReportStatus.ACCEPTED.name
                it[UrgentReports.acceptedByUserId] = 1
                it[UrgentReports.acceptedAt] = clock.now().toEpochMilliseconds()
            }
        }

        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val response = TestHttp.post("${handle.baseUrl}/api/urgent-rescuers/reports/$reportId/accept", cookie)

            assertEquals(409, response.statusCode())
        } finally {
            handle.stop()
        }
    }
}

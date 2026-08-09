package com.adoptu.services

import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.LostFoundRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.LostFoundKind
import com.adoptu.dto.input.SubmitLostFoundReportRequest
import com.adoptu.mocks.FakeCaptchaPort
import com.adoptu.mocks.FakeGeocodingPort
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestDatabase
import com.adoptu.ports.GeocodeResult
import com.universaliun.ratelimit.backend.adapter.out.persistence.ExposedRateLimitStateAdapter
import com.universaliun.ratelimit.common.RateLimiter
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LostFoundServiceTest {

    private lateinit var service: LostFoundService
    private lateinit var userRepository: UserRepository
    private lateinit var notificationAdapter: MockNotificationAdapter
    private lateinit var captchaPort: FakeCaptchaPort
    private val clock = Clock.System
    private val zoneResult = GeocodeResult(latitude = 40.0, longitude = -3.0, radiusKm = 25.0)
    private val geocodingPort = FakeGeocodingPort(mapOf(Triple("Spain", null, "Madrid") to zoneResult))

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        userRepository = UserRepository(clock)
        notificationAdapter = MockNotificationAdapter()
        captchaPort = FakeCaptchaPort(valid = true)
        service = LostFoundService(
            LostFoundRepositoryImpl(clock), notificationAdapter, geocodingPort, captchaPort,
            RateLimiter(ExposedRateLimitStateAdapter()), "http://localhost:4000"
        )
        transaction {
            Users.insert {
                it[Users.id] = 1
                it[Users.username] = "reporter@mocks.com"
                it[Users.displayName] = "Test Reporter"
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
            }
        }
    }

    private fun anonymousRequest(
        kind: LostFoundKind = LostFoundKind.LOST,
        email: String? = "reporter@example.com",
        captcha: String? = "tok",
        lat: Double? = 19.4,
        lon: Double? = -99.1,
        country: String? = "United States",
        city: String? = null,
        lastSeenAt: Long? = null
    ) = SubmitLostFoundReportRequest(
        kind = kind, description = "Brown dog", reporterEmail = email, captchaToken = captcha,
        country = country ?: "United States", city = city, latitude = lat, longitude = lon, lastSeenAt = lastSeenAt
    )

    @Test
    fun `anonymous submitReport succeeds with coordinates`() = runBlocking {
        val result = service.submitReport(anonymousRequest(), sessionUser = null, clientIp = "1.1.1.1")

        assertTrue(result.isSuccess)
        assertEquals("reporter@example.com", result.getOrThrow().reporterEmail)
    }

    @Test
    fun `anonymous submitReport geocodes a city when coordinates are absent`() = runBlocking {
        val request = anonymousRequest(lat = null, lon = null, country = "Spain", city = "Madrid")

        val result = service.submitReport(request, sessionUser = null, clientIp = "2.2.2.2")

        assertTrue(result.isSuccess)
        assertEquals(zoneResult.latitude, result.getOrThrow().latitude)
    }

    @Test
    fun `anonymous submitReport fails when location cannot be resolved`() = runBlocking {
        val request = anonymousRequest(lat = null, lon = null, country = null, city = null)

        val result = service.submitReport(request, sessionUser = null, clientIp = "3.3.3.3")

        assertTrue(result.isFailure)
    }

    @Test
    fun `anonymous submitReport is rate-limited per ip`() = runBlocking {
        repeat(5) { service.submitReport(anonymousRequest(), sessionUser = null, clientIp = "9.1.1.1") }

        val result = service.submitReport(anonymousRequest(), sessionUser = null, clientIp = "9.1.1.1")

        assertTrue(result.isFailure)
    }

    @Test
    fun `anonymous submitReport rejects an invalid captcha`() = runBlocking {
        captchaPort.setValid(false)

        val result = service.submitReport(anonymousRequest(), sessionUser = null, clientIp = "4.4.4.4")

        assertTrue(result.isFailure)
    }

    @Test
    fun `anonymous submitReport requires an email address`() = runBlocking {
        val result = service.submitReport(anonymousRequest(email = null), sessionUser = null, clientIp = "5.5.5.5")

        assertTrue(result.isFailure)
    }

    @Test
    fun `session submitReport skips captcha and rate limiting`() = runBlocking {
        val sessionUser = userRepository.getById(1)!!

        val result = service.submitReport(anonymousRequest(email = null, captcha = null), sessionUser = sessionUser, clientIp = "6.6.6.6")

        assertTrue(result.isSuccess)
        assertEquals(sessionUser.username, result.getOrThrow().reporterEmail)
    }

    @Test
    fun `browse filters open reports by country, case-insensitively`() = runBlocking {
        service.submitReport(anonymousRequest(country = "united states"), sessionUser = null, clientIp = "7.7.7.7")
        service.submitReport(anonymousRequest(kind = LostFoundKind.FOUND, country = "Mexico"), sessionUser = null, clientIp = "7.7.7.8")

        val usReports = service.browse(LostFoundKind.LOST, "United States")

        assertEquals(1, usReports.size)
    }

    @Test
    fun `contactReporter relays a message without exposing the reporter's email`() = runBlocking {
        val report = service.submitReport(anonymousRequest(), sessionUser = null, clientIp = "8.8.8.8").getOrThrow()

        val result = service.contactReporter(report.id, fromEmail = "finder@example.com", message = "I think I found your dog!")
        delay(200)

        assertTrue(result.isSuccess)
        val email = notificationAdapter.getSentEmails().single { it.to == report.reporterEmail && it.subject.contains("match for your") }
        assertTrue(email.body.contains("finder@example.com"))
    }

    @Test
    fun `contactReporter fails for an unknown report`() = runBlocking {
        val result = service.contactReporter(999, fromEmail = "x@example.com", message = "hi")

        assertTrue(result.isFailure)
    }

    @Test
    fun `resolveAsOwner marks the report resolved for its own reporter`() = runBlocking {
        val sessionUser = userRepository.getById(1)!!
        val report = service.submitReport(anonymousRequest(email = null, captcha = null), sessionUser = sessionUser, clientIp = "1.2.1.2").getOrThrow()

        val result = service.resolveAsOwner(report.id, userId = 1)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `resolveAsOwner rejects a user who didn't file the report`() = runBlocking {
        val report = service.submitReport(anonymousRequest(), sessionUser = null, clientIp = "1.2.1.3").getOrThrow()

        val result = service.resolveAsOwner(report.id, userId = 999)

        assertTrue(result.isFailure)
    }

    @Test
    fun `resolveViaToken resolves the report and rejects an unknown token`() = runBlocking {
        val report = service.submitReport(anonymousRequest(), sessionUser = null, clientIp = "1.2.1.4").getOrThrow()
        val token = report.resolveToken!!

        val result = service.resolveViaToken(token)
        assertTrue(result.isSuccess)

        val alreadyUsed = service.resolveViaToken(token)
        assertTrue(alreadyUsed.isFailure)

        val unknown = service.resolveViaToken("does-not-exist")
        assertTrue(unknown.isFailure)
    }

    @Test
    fun `a nearby opposite-kind report within the match window triggers a match email`() = runBlocking {
        val now = clock.now().toEpochMilliseconds()
        service.submitReport(anonymousRequest(kind = LostFoundKind.FOUND, lastSeenAt = now), sessionUser = null, clientIp = "1.3.1.1")

        service.submitReport(anonymousRequest(kind = LostFoundKind.LOST, lastSeenAt = now), sessionUser = null, clientIp = "1.3.1.2")
        delay(300)

        val matchEmails = notificationAdapter.getSentEmails().filter { it.subject.contains("match your pet") }
        assertEquals(1, matchEmails.size)
    }
}

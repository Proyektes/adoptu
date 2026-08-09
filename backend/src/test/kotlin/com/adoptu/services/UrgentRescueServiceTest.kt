package com.adoptu.services

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.UrgentRescueRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.CreateUrgentRescuerProfileRequest
import com.adoptu.dto.input.LocationInputMode
import com.adoptu.dto.input.SubmitUrgentReportRequest
import com.adoptu.dto.input.UpdateUrgentRescuerProfileRequest
import com.adoptu.dto.input.UrgentDangerType
import com.adoptu.mocks.FakeCaptchaPort
import com.adoptu.mocks.FakeGeocodingPort
import com.adoptu.mocks.FakeSmsNotificationPort
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrgentRescueServiceTest {

    private lateinit var service: UrgentRescueService
    private lateinit var userRepository: UserRepository
    private lateinit var notificationAdapter: MockNotificationAdapter
    private lateinit var smsAdapter: FakeSmsNotificationPort
    private lateinit var captchaPort: FakeCaptchaPort
    private val clock = Clock.System
    private val zoneResult = GeocodeResult(latitude = 40.0, longitude = -3.0, radiusKm = 25.0)
    private val geocodingPort = FakeGeocodingPort(mapOf(Triple("Spain", null, "Madrid") to zoneResult))

    private fun newRateLimiter() = RateLimiter(ExposedRateLimitStateAdapter())

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        userRepository = UserRepository(clock)
        notificationAdapter = MockNotificationAdapter()
        smsAdapter = FakeSmsNotificationPort()
        captchaPort = FakeCaptchaPort(valid = true)
        service = UrgentRescueService(
            UrgentRescueRepositoryImpl(clock), userRepository, notificationAdapter, smsAdapter,
            geocodingPort, captchaPort, newRateLimiter(), "http://localhost:4000"
        )
        transaction {
            Users.insert {
                it[Users.id] = 1
                it[Users.username] = "rescuer@mocks.com"
                it[Users.displayName] = "Test Rescuer"
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
            }
            UserActiveRoles.insert {
                it[UserActiveRoles.userId] = 1
                it[UserActiveRoles.role] = "URGENT_RESCUER"
            }
        }
    }

    // --- Profile: create -----------------------------------------------------------------

    @Test
    fun `createProfile succeeds with explicit coordinates`() = runBlocking {
        val result = service.createProfile(1, CreateUrgentRescuerProfileRequest(
            phone = "+15551234567", inputMode = LocationInputMode.COORDINATES,
            latitude = 19.4, longitude = -99.1, radiusKm = 10.0
        ))

        assertTrue(result.isSuccess)
        assertEquals(19.4, result.getOrThrow().latitude)
    }

    @Test
    fun `createProfile fails when coordinates mode is missing a field`() = runBlocking {
        val result = service.createProfile(1, CreateUrgentRescuerProfileRequest(
            phone = "+15551234567", inputMode = LocationInputMode.COORDINATES, latitude = 19.4
        ))

        assertTrue(result.isFailure)
    }

    @Test
    fun `createProfile geocodes a zone into coordinates`() = runBlocking {
        val result = service.createProfile(1, CreateUrgentRescuerProfileRequest(
            phone = "+15551234567", inputMode = LocationInputMode.ZONE, zoneCountry = "Spain", zoneCity = "Madrid"
        ))

        assertTrue(result.isSuccess)
        assertEquals(zoneResult.latitude, result.getOrThrow().latitude)
        assertEquals(zoneResult.radiusKm, result.getOrThrow().radiusKm)
    }

    @Test
    fun `createProfile fails when the zone cannot be geocoded`() = runBlocking {
        val result = service.createProfile(1, CreateUrgentRescuerProfileRequest(
            phone = "+15551234567", inputMode = LocationInputMode.ZONE, zoneCountry = "Nowhere", zoneCity = "Nowhereville"
        ))

        assertTrue(result.isFailure)
    }

    // --- Profile: update -------------------------------------------------------------------

    @Test
    fun `updateProfile re-geocodes when switching to a zone`() = runBlocking {
        service.createProfile(1, CreateUrgentRescuerProfileRequest(
            phone = "+15551234567", inputMode = LocationInputMode.COORDINATES, latitude = 1.0, longitude = 1.0, radiusKm = 5.0
        ))

        val result = service.updateProfile(1, UpdateUrgentRescuerProfileRequest(
            inputMode = LocationInputMode.ZONE, zoneCountry = "Spain", zoneCity = "Madrid"
        ))

        assertTrue(result.isSuccess)
        assertEquals(zoneResult.latitude, result.getOrThrow().latitude)
    }

    @Test
    fun `updateProfile fails for a user with no existing profile`() = runBlocking {
        val result = service.updateProfile(1, UpdateUrgentRescuerProfileRequest(phone = "+15550000000"))

        assertTrue(result.isFailure)
    }

    @Test
    fun `activateProfile and deactivateProfile toggle the user's role`() = runBlocking {
        val activated = service.activateProfile(1)
        assertTrue(activated?.activeRoles?.contains(com.adoptu.dto.input.UserRole.URGENT_RESCUER) == true)

        val deactivated = service.deactivateProfile(1)
        assertFalse(deactivated?.activeRoles?.contains(com.adoptu.dto.input.UserRole.URGENT_RESCUER) == true)
    }

    // --- Report submission -----------------------------------------------------------------

    private fun anonymousReport(email: String? = "reporter@example.com", captcha: String? = "tok") =
        SubmitUrgentReportRequest(
            description = "Injured dog", dangerType = UrgentDangerType.INJURED,
            reporterEmail = email, captchaToken = captcha, latitude = 19.4, longitude = -99.1
        )

    @Test
    fun `anonymous submitReport succeeds with captcha and email`() = runBlocking {
        val result = service.submitReport(anonymousReport(), sessionUser = null, clientIp = "1.2.3.4")

        assertTrue(result.isSuccess)
        assertEquals("reporter@example.com", result.getOrThrow().reporterEmail)
    }

    @Test
    fun `anonymous submitReport is rate-limited per ip`() = runBlocking {
        repeat(5) { service.submitReport(anonymousReport(), sessionUser = null, clientIp = "9.9.9.9") }

        val result = service.submitReport(anonymousReport(), sessionUser = null, clientIp = "9.9.9.9")

        assertTrue(result.isFailure)
    }

    @Test
    fun `anonymous submitReport rejects an invalid captcha`() = runBlocking {
        captchaPort.setValid(false)

        val result = service.submitReport(anonymousReport(), sessionUser = null, clientIp = "5.5.5.5")

        assertTrue(result.isFailure)
    }

    @Test
    fun `anonymous submitReport requires an email address`() = runBlocking {
        val result = service.submitReport(anonymousReport(email = null), sessionUser = null, clientIp = "6.6.6.6")

        assertTrue(result.isFailure)
    }

    @Test
    fun `anonymous submitReport fails when location cannot be resolved`() = runBlocking {
        val request = SubmitUrgentReportRequest(
            description = "Injured dog", dangerType = UrgentDangerType.INJURED,
            reporterEmail = "x@example.com", captchaToken = "tok"
        )

        val result = service.submitReport(request, sessionUser = null, clientIp = "7.7.7.7")

        assertTrue(result.isFailure)
    }

    @Test
    fun `session submitReport skips captcha and rate limiting`() = runBlocking {
        val sessionUser = userRepository.getById(1)!!
        val request = SubmitUrgentReportRequest(
            description = "Injured cat", dangerType = UrgentDangerType.INJURED,
            latitude = 19.4, longitude = -99.1
        )

        val result = service.submitReport(request, sessionUser = sessionUser, clientIp = "8.8.8.8")

        assertTrue(result.isSuccess)
        assertEquals(sessionUser.username, result.getOrThrow().reporterEmail)
    }

    @Test
    fun `a matching active rescuer profile gets paged and alerted after submission`() = runBlocking {
        service.createProfile(1, CreateUrgentRescuerProfileRequest(
            phone = "+15551234567", inputMode = LocationInputMode.COORDINATES, latitude = 19.4, longitude = -99.1, radiusKm = 10.0
        ))

        service.submitReport(anonymousReport(), sessionUser = null, clientIp = "2.2.2.2")
        delay(300) // matchAndPage/notify run in a background scope.launch{}

        val pending = service.getMyPendingPages(1)
        assertEquals(1, pending.size)
        assertEquals(1, smsAdapter.getSent().size)
        assertTrue(notificationAdapter.getSentEmails().any { it.subject.contains("URGENT") })
    }

    // --- Accept --------------------------------------------------------------------------

    @Test
    fun `acceptAsRescuer succeeds for a rescuer who was actually paged`() = runBlocking {
        service.createProfile(1, CreateUrgentRescuerProfileRequest(
            phone = "+15551234567", inputMode = LocationInputMode.COORDINATES, latitude = 19.4, longitude = -99.1, radiusKm = 10.0
        ))
        val report = service.submitReport(anonymousReport(), sessionUser = null, clientIp = "3.3.3.3").getOrThrow()
        delay(300)

        val result = service.acceptAsRescuer(report.id, rescuerId = 1)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `acceptAsRescuer fails for a rescuer who was never paged`() = runBlocking {
        val result = service.acceptAsRescuer(reportId = 999, rescuerId = 1)

        assertTrue(result.isFailure)
    }

    @Test
    fun `acceptViaToken fails for an unknown token`() = runBlocking {
        val result = service.acceptViaToken("does-not-exist")

        assertTrue(result.isFailure)
    }

    // --- Leaderboard -----------------------------------------------------------------------

    @Test
    fun `getLeaderboard returns an empty list with no accepted reports`() = runBlocking {
        assertTrue(service.getLeaderboard().isEmpty())
    }

    @Test
    fun `getLeaderboard reflects an accepted report`() = runBlocking {
        service.createProfile(1, CreateUrgentRescuerProfileRequest(
            phone = "+15551234567", inputMode = LocationInputMode.COORDINATES, latitude = 19.4, longitude = -99.1, radiusKm = 10.0
        ))
        val report = service.submitReport(anonymousReport(), sessionUser = null, clientIp = "4.4.4.4").getOrThrow()
        delay(300)
        service.acceptAsRescuer(report.id, rescuerId = 1)

        val leaderboard = service.getLeaderboard()

        assertEquals(1, leaderboard.size)
        assertEquals(1, leaderboard.first().userId)
        assertEquals(1, leaderboard.first().acceptedCount)
    }
}

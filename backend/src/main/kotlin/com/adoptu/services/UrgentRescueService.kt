package com.adoptu.services

import com.adoptu.dto.input.CreateUrgentRescuerProfileRequest
import com.adoptu.dto.input.LocationInputMode
import com.adoptu.dto.input.SubmitUrgentReportRequest
import com.adoptu.dto.input.UpdateUrgentRescuerProfileRequest
import com.adoptu.dto.input.UrgentReportDto
import com.adoptu.dto.input.UrgentReportPageDto
import com.adoptu.dto.input.UrgentRescuerLeaderboardEntryDto
import com.adoptu.dto.input.UrgentRescuerProfileDto
import com.adoptu.dto.input.UserDto
import com.adoptu.ports.CaptchaPort
import com.adoptu.ports.GeocodingPort
import com.adoptu.ports.NotificationPort
import com.adoptu.ports.SmsNotificationPort
import com.adoptu.ports.UrgentRescueRepositoryPort
import com.adoptu.ports.UserRepositoryPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.universaliun.ratelimit.common.RateLimitPolicy
import com.universaliun.ratelimit.common.RateLimiter
import kotlin.time.Duration.Companion.hours

private const val SUBMIT_RATE_LIMIT_KIND = "URGENT_REPORT_SUBMIT"
private const val MAX_REPORTS_PER_IP_PER_DAY = 5

class UrgentRescueService(
    private val urgentRescueRepository: UrgentRescueRepositoryPort,
    private val userRepository: UserRepositoryPort,
    private val notificationAdapter: NotificationPort,
    private val smsAdapter: SmsNotificationPort,
    private val geocodingPort: GeocodingPort,
    private val captchaPort: CaptchaPort,
    private val rateLimiter: RateLimiter,
    private val baseUrl: String = "http://localhost:80"
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val submitPolicy = RateLimitPolicy(window = 24.hours, maxEventsPerWindow = MAX_REPORTS_PER_IP_PER_DAY)

    // --- Rescuer profile -----------------------------------------------------------------

    suspend fun getProfile(userId: Int): UrgentRescuerProfileDto? = urgentRescueRepository.getProfile(userId)

    suspend fun createProfile(userId: Int, request: CreateUrgentRescuerProfileRequest): Result<UrgentRescuerProfileDto> {
        val (lat, lon, radius) = resolveCoordinates(
            request.inputMode, request.latitude, request.longitude, request.radiusKm,
            request.zoneCountry, request.zoneState, request.zoneCity
        ) ?: return Result.failure(IllegalArgumentException("Could not determine a location for this profile"))

        return Result.success(urgentRescueRepository.createProfile(userId, request, lat, lon, radius))
    }

    suspend fun updateProfile(userId: Int, request: UpdateUrgentRescuerProfileRequest): Result<UrgentRescuerProfileDto> {
        var lat = request.latitude
        var lon = request.longitude
        var radius = request.radiusKm

        if (request.inputMode == LocationInputMode.ZONE && (request.zoneCountry != null || request.zoneCity != null)) {
            val existing = urgentRescueRepository.getProfile(userId)
            val country = request.zoneCountry ?: existing?.zoneCountry
            val city = request.zoneCity ?: existing?.zoneCity
            if (country != null && city != null) {
                val geocoded = geocodingPort.geocode(country, request.zoneState ?: existing?.zoneState, city)
                    ?: return Result.failure(IllegalArgumentException("Could not find that location"))
                lat = geocoded.latitude
                lon = geocoded.longitude
                radius = geocoded.radiusKm
            }
        }

        val updated = urgentRescueRepository.updateProfile(userId, request, lat, lon, radius)
            ?: return Result.failure(IllegalArgumentException("Profile not found"))
        return Result.success(updated)
    }

    suspend fun activateProfile(userId: Int): UserDto? = userRepository.activateUrgentRescuerProfile(userId)
    suspend fun deactivateProfile(userId: Int): UserDto? = userRepository.deactivateUrgentRescuerProfile(userId)

    private suspend fun resolveCoordinates(
        inputMode: LocationInputMode,
        latitude: Double?,
        longitude: Double?,
        radiusKm: Double?,
        zoneCountry: String?,
        zoneState: String?,
        zoneCity: String?
    ): Triple<Double, Double, Double>? = when (inputMode) {
        LocationInputMode.COORDINATES -> {
            if (latitude == null || longitude == null || radiusKm == null) null
            else Triple(latitude, longitude, radiusKm)
        }
        LocationInputMode.ZONE -> {
            if (zoneCountry == null || zoneCity == null) null
            else geocodingPort.geocode(zoneCountry, zoneState, zoneCity)
                ?.let { Triple(it.latitude, it.longitude, it.radiusKm) }
        }
    }

    // --- Report submission -----------------------------------------------------------------

    suspend fun submitReport(request: SubmitUrgentReportRequest, sessionUser: UserDto?, clientIp: String): Result<UrgentReportDto> {
        if (sessionUser == null) {
            // Anonymous submitters are rate-limited by IP (no account to key on) and must pass CAPTCHA.
            if (!rateLimiter.verify(clientIp, SUBMIT_RATE_LIMIT_KIND, submitPolicy)) {
                return Result.failure(IllegalStateException("Too many reports from this network - please try again later"))
            }
            if (request.captchaToken.isNullOrBlank() || !captchaPort.verify(request.captchaToken, clientIp)) {
                return Result.failure(IllegalArgumentException("CAPTCHA verification failed"))
            }
            if (request.reporterEmail.isNullOrBlank()) {
                return Result.failure(IllegalArgumentException("An email address is required so we can follow up"))
            }
        }

        val reporterEmail = sessionUser?.username ?: request.reporterEmail!!

        val (lat, lon, locationLabel) = resolveReportLocation(request)
            ?: return Result.failure(IllegalArgumentException("Could not determine the pet's location"))

        val report = urgentRescueRepository.createReport(
            request = request,
            reporterUserId = sessionUser?.id,
            reporterEmail = reporterEmail,
            latitude = lat,
            longitude = lon,
            locationLabel = locationLabel
        )

        scope.launch { matchAndPage(report) }

        return Result.success(report)
    }

    private suspend fun resolveReportLocation(request: SubmitUrgentReportRequest): Triple<Double, Double, String>? {
        if (request.latitude != null && request.longitude != null) {
            val label = listOfNotNull(request.city, request.country).joinToString(", ").ifBlank { "reported location" }
            return Triple(request.latitude, request.longitude, label)
        }
        if (request.country != null && request.city != null) {
            val geocoded = geocodingPort.geocode(request.country, request.state, request.city) ?: return null
            val label = listOfNotNull(request.city, request.country).joinToString(", ")
            return Triple(geocoded.latitude, geocoded.longitude, label)
        }
        return null
    }

    // --- Matching + paging -----------------------------------------------------------------

    private suspend fun matchAndPage(report: UrgentReportDto) {
        val matches = urgentRescueRepository.getActiveProfiles().filter {
            haversineDistanceKm(report.latitude, report.longitude, it.latitude, it.longitude) <= it.radiusKm
        }

        for (rescuerProfile in matches) {
            val rescuer = userRepository.getById(rescuerProfile.userId) ?: continue
            val page = urgentRescueRepository.createReportPage(report.id, rescuerProfile.userId)
            // Flat slug, not "/urgent-rescue/accept" - SiteGenerator writes one flat .html file per
            // page (see its pages map), so a link with a path segment 404s on the static site.
            val acceptLink = "$baseUrl/urgent-rescue-accept?token=${page.token}"

            scope.launch {
                notificationAdapter.sendUrgentRescueAlert(
                    rescuerEmail = rescuer.username,
                    rescuerName = rescuer.displayName,
                    description = report.description,
                    dangerType = report.dangerType.name,
                    locationLabel = report.locationLabel,
                    acceptLink = acceptLink
                )
                smsAdapter.sendUrgentRescueAlert(
                    phone = rescuerProfile.phone,
                    description = report.description,
                    dangerType = report.dangerType.name,
                    locationLabel = report.locationLabel,
                    acceptLink = acceptLink
                )
            }
        }
    }

    // --- Accept (first-wins race) -----------------------------------------------------------

    suspend fun acceptViaToken(token: String): Result<UrgentReportDto> {
        val (reportId, rescuerId) = urgentRescueRepository.resolveAcceptToken(token)
            ?: return Result.failure(IllegalArgumentException("This report has already been accepted by another rescuer, or the link is invalid"))
        return doAccept(reportId, rescuerId)
    }

    suspend fun acceptAsRescuer(reportId: Int, rescuerId: Int): Result<UrgentReportDto> {
        val pending = urgentRescueRepository.getPendingPagesForRescuer(rescuerId)
        if (pending.none { it.reportId == reportId }) {
            return Result.failure(IllegalArgumentException("You were not paged for this report"))
        }
        return doAccept(reportId, rescuerId)
    }

    private suspend fun doAccept(reportId: Int, rescuerId: Int): Result<UrgentReportDto> {
        val won = urgentRescueRepository.tryAcceptReport(reportId, rescuerId)
        if (!won) {
            return Result.failure(IllegalStateException("This report has already been accepted by another rescuer"))
        }
        urgentRescueRepository.resolvePages(reportId, rescuerId)
        val report = urgentRescueRepository.getReport(reportId)
            ?: return Result.failure(IllegalStateException("Report not found after accept"))
        return Result.success(report)
    }

    // Route-friendly shape (never includes UrgentReportPageDto.token) joined with enough report
    // context for a rescuer's dashboard to actually be useful, not just bare ids.
    suspend fun getMyPendingPages(rescuerId: Int): List<Map<String, Any?>> =
        urgentRescueRepository.getPendingPagesForRescuer(rescuerId).mapNotNull { page ->
            val report = urgentRescueRepository.getReport(page.reportId) ?: return@mapNotNull null
            mapOf(
                "pageId" to page.id,
                "reportId" to page.reportId,
                "status" to page.status,
                "createdAt" to page.createdAt,
                "description" to report.description,
                "dangerType" to report.dangerType,
                "locationLabel" to report.locationLabel,
                "photoUrl" to report.photoUrl
            )
        }

    // --- Leaderboard -----------------------------------------------------------------------

    suspend fun getLeaderboard(): List<UrgentRescuerLeaderboardEntryDto> {
        val twelveMonthsAgoMs = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000
        return urgentRescueRepository.getLeaderboard(twelveMonthsAgoMs)
    }
}

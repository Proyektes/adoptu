package com.adoptu.services

import com.adoptu.dto.input.LostFoundKind
import com.adoptu.dto.input.LostFoundReportDto
import com.adoptu.dto.input.SubmitLostFoundReportRequest
import com.adoptu.dto.input.UserDto
import com.adoptu.ports.CaptchaPort
import com.adoptu.ports.GeocodingPort
import com.adoptu.ports.LostFoundRepositoryPort
import com.adoptu.ports.NotificationPort
import com.universaliun.ratelimit.common.RateLimitPolicy
import com.universaliun.ratelimit.common.RateLimiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.hours

private const val SUBMIT_RATE_LIMIT_KIND = "LOST_FOUND_SUBMIT"
private const val MAX_REPORTS_PER_IP_PER_DAY = 5
private const val MATCH_RADIUS_KM = 15.0
private const val MATCH_WINDOW_MS = 30L * 24 * 60 * 60 * 1000 // 30 days either side of lastSeenAt

class LostFoundService(
    private val lostFoundRepository: LostFoundRepositoryPort,
    private val notificationAdapter: NotificationPort,
    private val geocodingPort: GeocodingPort,
    private val captchaPort: CaptchaPort,
    private val rateLimiter: RateLimiter,
    private val baseUrl: String = "http://localhost:80"
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val submitPolicy = RateLimitPolicy(window = 24.hours, maxEventsPerWindow = MAX_REPORTS_PER_IP_PER_DAY)

    suspend fun submitReport(request: SubmitLostFoundReportRequest, sessionUser: UserDto?, clientIp: String): Result<LostFoundReportDto> {
        if (sessionUser == null) {
            // Same anonymous gating as UrgentRescueService.submitReport - no account to key
            // rate-limiting on, so IP + CAPTCHA instead.
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

        val lastSeenAt = request.lastSeenAt ?: System.currentTimeMillis()

        val report = lostFoundRepository.createReport(
            request = request,
            reporterUserId = sessionUser?.id,
            reporterEmail = reporterEmail,
            latitude = lat,
            longitude = lon,
            locationLabel = locationLabel,
            lastSeenAt = lastSeenAt
        )

        scope.launch { notifyMatches(report) }

        return Result.success(report)
    }

    private suspend fun resolveReportLocation(request: SubmitLostFoundReportRequest): Triple<Double, Double, String>? {
        if (request.latitude != null && request.longitude != null) {
            val label = listOfNotNull(request.city, request.country).joinToString(", ").ifBlank { "reported location" }
            return Triple(request.latitude, request.longitude, label)
        }
        if (request.city != null) {
            val geocoded = geocodingPort.geocode(request.country, request.state, request.city) ?: return null
            val label = listOfNotNull(request.city, request.country).joinToString(", ")
            return Triple(geocoded.latitude, geocoded.longitude, label)
        }
        return null
    }

    // --- Browse -----------------------------------------------------------------------------

    suspend fun browse(kind: LostFoundKind, country: String): List<LostFoundReportDto> =
        lostFoundRepository.getOpenReports(kind).filter { it.country.equals(country, ignoreCase = true) }

    suspend fun getReport(id: Int): LostFoundReportDto? = lostFoundRepository.getReport(id)

    // --- Contact relay - never exposes the reporter's raw email to the other party -----------

    suspend fun contactReporter(id: Int, fromEmail: String, message: String): Result<Unit> {
        val report = lostFoundRepository.getReport(id) ?: return Result.failure(IllegalArgumentException("Report not found"))
        scope.launch {
            notificationAdapter.sendEmail(
                to = report.reporterEmail,
                subject = "Someone may have a match for your ${report.kind.name.lowercase()} pet report - Adopt-U",
                body = "$fromEmail wrote about your report (\"${report.description.take(80)}\"):\n\n$message\n\nReply directly to this email to respond."
            )
        }
        return Result.success(Unit)
    }

    // --- Resolve ------------------------------------------------------------------------------

    suspend fun resolveViaToken(token: String): Result<LostFoundReportDto> {
        val id = lostFoundRepository.resolveToken(token)
            ?: return Result.failure(IllegalArgumentException("This report was already resolved, or the link is invalid"))
        val updated = lostFoundRepository.markResolved(id)
            ?: return Result.failure(IllegalStateException("Report not found after resolve"))
        return Result.success(updated)
    }

    suspend fun resolveAsOwner(id: Int, userId: Int): Result<LostFoundReportDto> {
        val report = lostFoundRepository.getReport(id) ?: return Result.failure(IllegalArgumentException("Report not found"))
        if (report.reporterUserId != userId) {
            return Result.failure(IllegalArgumentException("You didn't file this report"))
        }
        val updated = lostFoundRepository.markResolved(id) ?: return Result.failure(IllegalStateException("Report not found after resolve"))
        return Result.success(updated)
    }

    // --- Matching -----------------------------------------------------------------------------

    private suspend fun notifyMatches(report: LostFoundReportDto) {
        val oppositeKind = if (report.kind == LostFoundKind.LOST) LostFoundKind.FOUND else LostFoundKind.LOST
        val matches = lostFoundRepository.getOpenReports(oppositeKind).filter {
            haversineDistanceKm(report.latitude, report.longitude, it.latitude, it.longitude) <= MATCH_RADIUS_KM &&
                abs(it.lastSeenAt - report.lastSeenAt) <= MATCH_WINDOW_MS
        }

        val resolveLink = "$baseUrl/lost-found-resolve?token=${report.resolveToken}"
        val confirmBody = buildString {
            append("Your report has been posted. If you find your pet (or realize this was a false alarm), ")
            append("resolve it here so others stop being notified: $resolveLink\n\n")
            if (matches.isNotEmpty()) {
                append("We also found ${matches.size} possible match(es) already posted:\n\n")
                matches.forEach { append("- ${it.description.take(100)} (near ${it.locationLabel}) - $baseUrl/lost-found/${it.id}\n") }
            }
        }
        scope.launch {
            notificationAdapter.sendEmail(report.reporterEmail, "Your pet report is live - Adopt-U", confirmBody)
        }

        matches.forEach { match ->
            scope.launch {
                notificationAdapter.sendEmail(
                    match.reporterEmail,
                    "A new report may match your pet - Adopt-U",
                    "A new ${report.kind.name.lowercase()} report may match yours:\n\n${report.description.take(100)} (near ${report.locationLabel})\n\n$baseUrl/lost-found/${report.id}"
                )
            }
        }
    }
}

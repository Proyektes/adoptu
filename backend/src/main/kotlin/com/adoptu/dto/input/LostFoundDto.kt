package com.adoptu.dto.input

enum class LostFoundKind { LOST, FOUND }

enum class LostFoundStatus { OPEN, RESOLVED }

data class LostFoundReportDto(
    val id: Int,
    val kind: LostFoundKind,
    val reporterUserId: Int? = null,
    val reporterEmail: String,
    val reporterPhone: String? = null,
    val petType: String? = null,
    val description: String,
    val photoUrl: String? = null,
    val latitude: Double,
    val longitude: Double,
    val locationLabel: String,
    val country: String,
    val lastSeenAt: Long,
    val status: LostFoundStatus,
    val createdAt: Long,
    // Single-use "resolve this report" token - only ever populated on the create-report response
    // (so the reporter's own confirmation email/UI can build the resolve link), never on
    // getReport/browse (would leak another party's token to whoever looks up their report).
    val resolveToken: String? = null
)

// Anonymous submitters must supply reporterEmail + captchaToken; a logged-in session fills
// reporterEmail from the account and skips CAPTCHA (see LostFoundRoutes) - same gating as
// SubmitUrgentReportRequest. country is always explicit (a dropdown, like every other search/browse
// page in this app) so browsing-by-country never needs reverse geocoding; latitude/longitude
// (browser Geolocation) are optional and only sharpen the distance-based match/notify step.
data class SubmitLostFoundReportRequest(
    val kind: LostFoundKind,
    val petType: String? = null,
    val description: String,
    val reporterEmail: String? = null,
    val reporterPhone: String? = null,
    val captchaToken: String? = null,
    val country: String,
    val state: String? = null,
    val city: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    // Defaults to "now" (report-submission time) when absent.
    val lastSeenAt: Long? = null
)

data class ContactLostFoundReporterRequest(
    val fromEmail: String,
    val message: String
)

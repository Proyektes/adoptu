package com.adoptu.dto.input

// COORDINATES: rescuer/report location is a browser-Geolocation-captured lat/lng pair.
// ZONE: rescuer/report location was entered as country/state/city text and geocoded server-side
// (see GeocodingPort) into a lat/lng + covering radius - zoneCountry/zoneState/zoneCity are kept
// alongside the derived coordinates purely for display/editing, matching is always coordinate-based.
enum class LocationInputMode { COORDINATES, ZONE }

enum class UrgentReportStatus { PENDING, ACCEPTED, RESOLVED, CANCELLED }

enum class UrgentReportPageStatus { PAGED, ACCEPTED, MISSED }

enum class UrgentDangerType { INJURED, ABUSED, STARVING, TOO_YOUNG, OTHER }

data class UrgentRescuerProfileDto(
    val userId: Int,
    val phone: String,
    val latitude: Double,
    val longitude: Double,
    val radiusKm: Double,
    val inputMode: LocationInputMode,
    val zoneCountry: String? = null,
    val zoneState: String? = null,
    val zoneCity: String? = null,
    val active: Boolean,
    val createdAt: Long
)

data class CreateUrgentRescuerProfileRequest(
    val phone: String,
    val inputMode: LocationInputMode,
    // Required when inputMode == COORDINATES (client captured via navigator.geolocation).
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusKm: Double? = null,
    // Required when inputMode == ZONE - geocoded server-side into latitude/longitude/radiusKm.
    val zoneCountry: String? = null,
    val zoneState: String? = null,
    val zoneCity: String? = null
)

data class UpdateUrgentRescuerProfileRequest(
    val phone: String? = null,
    val active: Boolean? = null,
    val inputMode: LocationInputMode? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusKm: Double? = null,
    val zoneCountry: String? = null,
    val zoneState: String? = null,
    val zoneCity: String? = null
)

data class UrgentReportDto(
    val id: Int,
    val reporterUserId: Int? = null,
    val reporterEmail: String,
    val reporterPhone: String? = null,
    val description: String,
    val dangerType: UrgentDangerType,
    val photoUrl: String? = null,
    val latitude: Double,
    val longitude: Double,
    val locationLabel: String,
    // Street/exteriorNumber/referenceNotes are what make locationLabel concrete enough to hand a
    // taxi driver or a maps app, rather than just a city/country. All optional - not every report
    // will have a formally addressed location.
    val street: String? = null,
    val exteriorNumber: String? = null,
    val referenceNotes: String? = null,
    val status: UrgentReportStatus,
    val acceptedByUserId: Int? = null,
    val acceptedByName: String? = null,
    val acceptedAt: Long? = null,
    val createdAt: Long
)

// Anonymous submitters must supply reporterEmail + captchaToken; a logged-in session fills
// reporterEmail from the account and skips CAPTCHA (see UrgentReportRoutes).
data class SubmitUrgentReportRequest(
    val description: String,
    val dangerType: UrgentDangerType,
    val reporterEmail: String? = null,
    val reporterPhone: String? = null,
    val captchaToken: String? = null,
    // Prefer latitude/longitude (browser Geolocation). Falls back to geocoding country/state/city
    // when coordinates are absent (permission denied / unsupported browser).
    val latitude: Double? = null,
    val longitude: Double? = null,
    val country: String? = null,
    val state: String? = null,
    val city: String? = null,
    // User-entered, not derived from geolocation/reverse-geocoding alone - a reverse-geocoded
    // street is offered as a starting point (see GET /api/urgent-reports/reverse-geocode) but the
    // exterior number and any reference notes ("blue house, black gate") can only come from the
    // reporter. This is what actually makes the report's location usable for a taxi or maps app.
    val street: String? = null,
    val exteriorNumber: String? = null,
    val referenceNotes: String? = null
)

data class UrgentReportPageDto(
    val id: Int,
    val reportId: Int,
    val rescuerId: Int,
    // Single-use accept token (mailed/texted as the "tap to accept" link). Routes must not
    // include this in any JSON response for a *list* of pages (e.g. a rescuer's own pending-pages
    // dashboard) - map to a route-local response shape that omits it there. It's only needed at
    // page-creation time, to build that one notification's accept link.
    val token: String,
    val status: UrgentReportPageStatus,
    val createdAt: Long
)

data class UrgentRescuerLeaderboardEntryDto(
    val userId: Int,
    val displayName: String,
    val acceptedCount: Int
)

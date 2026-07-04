package com.adoptu.routes

import com.adoptu.common.Country
import com.adoptu.web.queryParam
import io.helidon.http.HeaderNames
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRules

private val CLOUDFRONT_VIEWER_COUNTRY = HeaderNames.create("CloudFront-Viewer-Country")

// Prefers CloudFront's CloudFront-Viewer-Country header (IP-based, only present when
// requests go through the app's CloudFront distribution - see infra/cloudfront.tf).
// Falls back to the region subtag of the browser's own locale (e.g. "es-MX" -> "MX"),
// passed by the client, for local dev or any request that bypasses CloudFront.
fun HttpRules.countryRoutes() {
    get("/api/detect-country", Handler { req, res ->
        val viewerCountry = Country.fromIso2(req.headers().first(CLOUDFRONT_VIEWER_COUNTRY).orElse(null))
        val country = viewerCountry ?: Country.fromIso2(regionFromLocale(req.queryParam("locale")))
        res.send(mapOf("country" to country?.displayName))
    })
}

private fun regionFromLocale(locale: String?): String? {
    if (locale.isNullOrBlank()) return null
    val parts = locale.split('-')
    if (parts.size < 2) return null
    return parts.last()
}

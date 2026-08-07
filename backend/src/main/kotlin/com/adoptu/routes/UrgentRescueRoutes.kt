package com.adoptu.routes

import com.adoptu.dto.input.CreateUrgentRescuerProfileRequest
import com.adoptu.dto.input.SubmitUrgentReportRequest
import com.adoptu.dto.input.UpdateUrgentRescuerProfileRequest
import com.adoptu.services.UrgentRescueService
import com.adoptu.services.UserService
import com.adoptu.web.Deps
import com.universaliun.auth.backend.infrastructure.currentPrincipal
import com.adoptu.web.pathParam
import com.adoptu.web.queryParam
import com.adoptu.web.receiveJson
import com.adoptu.web.respondError
import com.adoptu.web.respondNotFound
import com.adoptu.web.respondUnauthorized
import io.helidon.http.HeaderNames
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRules
import io.helidon.webserver.http.ServerRequest
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject

private val CLOUDFRONT_VIEWER_ADDRESS = HeaderNames.create("CloudFront-Viewer-Address")

// CloudFront injects this itself (viewer IP:port) when explicitly whitelisted on the origin
// request policy (same treatment as CloudFront-Viewer-Country - see infra/cloudfront.tf and
// CountryRoutes.kt). Falls back to a shared "unknown" bucket for local dev / requests that bypass
// CloudFront entirely, where per-visitor IP rate limiting isn't meaningful anyway.
private fun clientIp(req: ServerRequest): String =
    req.headers().first(CLOUDFRONT_VIEWER_ADDRESS).orElse(null)?.substringBeforeLast(':') ?: "unknown"

fun HttpRules.urgentRescueRoutes() {
    val urgentRescueService by Deps.inject<UrgentRescueService>()
    val userService by Deps.inject<UserService>()

    // --- Rescuer profile (coverage area, phone) -------------------------------------------

    get("/api/urgent-rescuers/me", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val profile = urgentRescueService.getProfile(principal.userId.value.toInt()) ?: return@runBlocking res.respondNotFound("No urgent-rescuer profile yet")
            res.send(profile)
        }
    })

    post("/api/urgent-rescuers/me", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val body = req.receiveJson<CreateUrgentRescuerProfileRequest>()
            urgentRescueService.createProfile(principal.userId.value.toInt(), body).fold(
                onSuccess = { res.send(it) },
                onFailure = { res.respondError(it.message ?: "Could not create profile") }
            )
        }
    })

    put("/api/urgent-rescuers/me", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val body = req.receiveJson<UpdateUrgentRescuerProfileRequest>()
            urgentRescueService.updateProfile(principal.userId.value.toInt(), body).fold(
                onSuccess = { res.send(it) },
                onFailure = { res.respondError(it.message ?: "Could not update profile") }
            )
        }
    })

    // --- Report submission (works with or without a session) -----------------------------

    post("/api/urgent-reports/submit", Handler { req, res ->
        runBlocking {
            val principal = req.currentPrincipal()
            val sessionUser = principal?.let { userService.getById(it.userId.value.toInt()) }
            val body = req.receiveJson<SubmitUrgentReportRequest>()

            urgentRescueService.submitReport(body, sessionUser, clientIp(req)).fold(
                onSuccess = { res.send(it) },
                onFailure = { res.respondError(it.message ?: "Could not submit report") }
            )
        }
    })

    // --- Accept (first rescuer to tap/click wins) ------------------------------------------

    // Tapped from the emailed/texted alert - deliberately unauthenticated (the signed single-use
    // token IS the authorization, same reasoning as TemporalHomeRoutes' block-rescuer link).
    get("/api/urgent-reports/accept", Handler { req, res ->
        val token = req.queryParam("token")
        if (token.isNullOrBlank()) return@Handler res.respondError("Token is required")
        runBlocking {
            urgentRescueService.acceptViaToken(token).fold(
                onSuccess = { res.send(it) },
                onFailure = { res.respondError(it.message ?: "Could not accept report", 409) }
            )
        }
    })

    // In-app dashboard accept button - same race, resolved the same way, just authenticated
    // instead of token-based.
    post("/api/urgent-rescuers/reports/{id}/accept", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        val reportId = req.pathParam("id").toIntOrNull() ?: return@Handler res.respondError("Invalid report id")
        runBlocking {
            urgentRescueService.acceptAsRescuer(reportId, principal.userId.value.toInt()).fold(
                onSuccess = { res.send(it) },
                onFailure = { res.respondError(it.message ?: "Could not accept report", 409) }
            )
        }
    })

    get("/api/urgent-rescuers/my-pages", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            res.send(urgentRescueService.getMyPendingPages(principal.userId.value.toInt()))
        }
    })

    // --- Leaderboard ------------------------------------------------------------------------

    get("/api/urgent-rescuers/leaderboard", Handler { _, res ->
        runBlocking {
            res.send(urgentRescueService.getLeaderboard())
        }
    })
}

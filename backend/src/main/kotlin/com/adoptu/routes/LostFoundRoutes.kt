package com.adoptu.routes

import com.adoptu.dto.input.ContactLostFoundReporterRequest
import com.adoptu.dto.input.LostFoundKind
import com.adoptu.dto.input.SubmitLostFoundReportRequest
import com.adoptu.services.LostFoundService
import com.adoptu.services.UserService
import com.adoptu.services.validation.ValidationConstants
import com.adoptu.web.Deps
import com.adoptu.web.getSession
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

private fun clientIp(req: ServerRequest): String =
    req.headers().first(CLOUDFRONT_VIEWER_ADDRESS).orElse(null)?.substringBeforeLast(':') ?: "unknown"

fun HttpRules.lostFoundRoutes() {
    val lostFoundService by Deps.inject<LostFoundService>()
    val userService by Deps.inject<UserService>()

    // --- Report submission (works with or without a session, same as urgent reports) --------

    post("/api/lost-found/reports", Handler { req, res ->
        runBlocking {
            val session = req.getSession()
            val sessionUser = session?.let { userService.getById(it.userId) }
            val body = req.receiveJson<SubmitLostFoundReportRequest>()

            lostFoundService.submitReport(body, sessionUser, clientIp(req)).fold(
                onSuccess = { res.send(it) },
                onFailure = { res.respondError(it.message ?: "Could not submit report") }
            )
        }
    })

    // --- Browse (public) ----------------------------------------------------------------------

    get("/api/lost-found/reports", Handler { req, res ->
        val kind = req.queryParam("kind")?.let { runCatching { LostFoundKind.valueOf(it) }.getOrNull() }
            ?: return@Handler res.respondError("kind is required (LOST or FOUND)")
        val country = req.queryParam("country")
        if (country.isNullOrBlank()) return@Handler res.respondError(ValidationConstants.COUNTRY_IS_REQUIRED)
        runBlocking { res.send(lostFoundService.browse(kind, country)) }
    })

    get("/api/lost-found/reports/{id}", Handler { req, res ->
        val id = req.pathParam("id").toIntOrNull() ?: return@Handler res.respondError("Invalid report id")
        runBlocking {
            val report = lostFoundService.getReport(id) ?: return@runBlocking res.respondNotFound("Report not found")
            res.send(report)
        }
    })

    // --- Contact relay - never exposes the reporter's raw email to the caller ----------------

    post("/api/lost-found/reports/{id}/contact", Handler { req, res ->
        val id = req.pathParam("id").toIntOrNull() ?: return@Handler res.respondError("Invalid report id")
        val body = req.receiveJson<ContactLostFoundReporterRequest>()
        runBlocking {
            lostFoundService.contactReporter(id, body.fromEmail, body.message).fold(
                onSuccess = { res.send(mapOf("success" to true)) },
                onFailure = { res.respondError(it.message ?: "Could not send message") }
            )
        }
    })

    // --- Resolve --------------------------------------------------------------------------

    // Tapped from the confirmation email - deliberately unauthenticated (the signed single-use
    // token IS the authorization), same reasoning as urgent-reports/accept.
    get("/api/lost-found/resolve", Handler { req, res ->
        val token = req.queryParam("token")
        if (token.isNullOrBlank()) return@Handler res.respondError("Token is required")
        runBlocking {
            lostFoundService.resolveViaToken(token).fold(
                onSuccess = { res.send(it) },
                onFailure = { res.respondError(it.message ?: "Could not resolve report", 409) }
            )
        }
    })

    post("/api/lost-found/reports/{id}/resolve", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()
        val id = req.pathParam("id").toIntOrNull() ?: return@Handler res.respondError("Invalid report id")
        runBlocking {
            lostFoundService.resolveAsOwner(id, session.userId).fold(
                onSuccess = { res.send(it) },
                onFailure = { res.respondError(it.message ?: "Could not resolve report", 409) }
            )
        }
    })
}

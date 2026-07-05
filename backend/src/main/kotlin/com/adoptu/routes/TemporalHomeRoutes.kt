package com.adoptu.routes

import com.adoptu.dto.input.*
import com.adoptu.services.ServiceResult
import com.adoptu.services.TemporalHomeService
import com.adoptu.services.validation.TemporalHomesValidationService
import com.adoptu.services.validation.ValidationConstants
import com.adoptu.web.Deps
import com.adoptu.web.getSession
import com.adoptu.web.pathParam
import com.adoptu.web.queryParam
import com.adoptu.web.receiveJson
import com.adoptu.web.respondError
import com.adoptu.web.respondForbidden
import com.adoptu.web.respondNotFound
import com.adoptu.web.respondUnauthorized
import io.helidon.http.HeaderNames
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRules
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject

fun HttpRules.temporalHomeRoutes() {
    val temporalHomeService by Deps.inject<TemporalHomeService>()
    val validationService by Deps.inject<TemporalHomesValidationService>()

    post("/api/users/temporal-home", Handler { req, res ->
        runBlocking {
            val sessionResult = validationService.validateSession(req.getSession())
            if (sessionResult is ServiceResult.Forbidden) {
                return@runBlocking res.respondUnauthorized()
            }
            val session = (sessionResult as ServiceResult.Success).data

            val body = req.receiveJson<CreateTemporalHomeRequest>()
            val validationResult = validationService.validateCreateTemporalHomeRequest(session.userId, body)
            if (validationResult is ServiceResult.Error) {
                return@runBlocking res.respondError(validationResult.message, 400)
            }

            try {
                val temporalHome = temporalHomeService.createTemporalHome(session.userId, body)
                temporalHomeService.activateTemporalHomeProfile(session.userId)
                res.send(temporalHome)
            } catch (e: Exception) {
                res.respondError(e.message ?: "Failed to create temporal home", 500)
            }
        }
    })

    get("/api/users/temporal-home", Handler { req, res ->
        runBlocking {
            val sessionResult = validationService.validateSession(req.getSession())
            if (sessionResult is ServiceResult.Forbidden) {
                return@runBlocking res.respondUnauthorized()
            }
            val session = (sessionResult as ServiceResult.Success).data

            val temporalHome = temporalHomeService.getTemporalHome(session.userId)
            if (temporalHome == null) {
                return@runBlocking res.respondError(ValidationConstants.TEMPORAL_HOME_PROFILE_NOT_FOUND, 404)
            }
            res.send(temporalHome)
        }
    })

    put("/api/users/temporal-home", Handler { req, res ->
        runBlocking {
            val sessionResult = validationService.validateSession(req.getSession())
            if (sessionResult is ServiceResult.Forbidden) {
                return@runBlocking res.respondUnauthorized()
            }
            val session = (sessionResult as ServiceResult.Success).data

            val profileResult = validationService.validateTemporalHomeProfile(session.userId)
            if (profileResult is ServiceResult.Error) {
                return@runBlocking res.respondError(profileResult.message, 404)
            }

            val body = req.receiveJson<UpdateTemporalHomeRequest>()

            try {
                val updated = temporalHomeService.updateTemporalHome(session.userId, body)
                if (updated == null) {
                    return@runBlocking res.respondError("Failed to update temporal home", 500)
                }
                res.send(updated)
            } catch (e: Exception) {
                res.respondError(e.message ?: "Failed to update temporal home", 500)
            }
        }
    })

    get("/api/users/temporal-home/requests", Handler { req, res ->
        runBlocking {
            val sessionResult = validationService.validateSession(req.getSession())
            if (sessionResult is ServiceResult.Forbidden) {
                return@runBlocking res.respondUnauthorized()
            }
            val session = (sessionResult as ServiceResult.Success).data

            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data

            val roleResult = validationService.validateRole(user, "TEMPORAL_HOME")
            if (roleResult is ServiceResult.Forbidden) {
                return@runBlocking res.respondForbidden()
            }

            val requests = temporalHomeService.getMyRequests(session.userId)
            res.send(requests)
        }
    })

    get("/api/temporal-homes", Handler { req, res ->
        val country = req.queryParam("country")
        val state = req.queryParam("state")
        val city = req.queryParam("city")
        val zip = req.queryParam("zip")
        val neighborhood = req.queryParam("neighborhood")

        val params = TemporalHomeSearchParams(
            country = country,
            state = state,
            city = city,
            zip = zip,
            neighborhood = neighborhood
        )

        val results = runBlocking { temporalHomeService.searchTemporalHomes(params) }
        // Public, unauthenticated listing - cached at the CDN edge via an
        // EXACT path_pattern ("/api/temporal-homes", no wildcard) in
        // infra/cloudfront.tf. POST /api/temporal-homes/request shares
        // this prefix and is authenticated - a wildcard would also force
        // that route's cache behavior to a GET/HEAD/OPTIONS-only
        // allowed_methods list, which would make CloudFront reject the POST.
        res.header(HeaderNames.CACHE_CONTROL, "public, max-age=30")
        res.send(results)
    })

    // Registered before "/api/temporal-homes/{id}" below: Helidon matches route rules
    // in registration order, so this literal-segment route must precede the templated
    // one at the same path depth or "block" would be shadowed as if it were {id} (see
    // the identical note in PetsRoutes.kt). No session required by design - this is a
    // no-login, one-click link sent in the request-notification email (see
    // TemporalHomeService.sendRequest). Security comes from the token being a
    // single-use, signed secret, not from the caller's identity - the previous version
    // of this endpoint trusted a raw temporalHomeId/rescuerId pair straight from the
    // URL with no proof the caller was actually that temporal home.
    get("/api/temporal-homes/block", Handler { req, res ->
        val token = req.queryParam("token")
        if (token.isNullOrBlank()) {
            return@Handler res.respondError("Token is required", 400)
        }
        runBlocking {
            val blocked = temporalHomeService.blockRescuerByToken(token)
            res.send(mapOf("blocked" to blocked))
        }
    })

    get("/api/temporal-homes/{id}", Handler { req, res ->
        runBlocking {
            val temporalHomeIdResult = validationService.validateTemporalHomeId(req.pathParam("id"))
            if (temporalHomeIdResult is ServiceResult.Error) {
                return@runBlocking res.respondError(temporalHomeIdResult.message, 400)
            }
            val temporalHomeId = (temporalHomeIdResult as ServiceResult.Success).data

            val temporalHome = temporalHomeService.getTemporalHome(temporalHomeId)
            if (temporalHome == null) {
                return@runBlocking res.respondNotFound()
            }
            res.header(HeaderNames.CACHE_CONTROL, "public, max-age=30")
            res.send(temporalHome)
        }
    })

    post("/api/temporal-homes/request", Handler { req, res ->
        runBlocking {
            val sessionResult = validationService.validateSession(req.getSession())
            if (sessionResult is ServiceResult.Forbidden) {
                return@runBlocking res.respondUnauthorized()
            }
            val session = (sessionResult as ServiceResult.Success).data

            val userResult = validationService.validateRescuerRole(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            if (userResult is ServiceResult.Error) {
                return@runBlocking res.respondError(userResult.message, 403)
            }

            val body = req.receiveJson<SendTemporalHomeRequestRequest>()

            val messageResult = validationService.validateRequired(body.message, "Message")
            if (messageResult is ServiceResult.Error) {
                return@runBlocking res.respondError(messageResult.message, 400)
            }

            val result = temporalHomeService.sendRequest(session.userId, body)
            if (result.isFailure) {
                return@runBlocking res.respondError(ValidationConstants.FAILED_TO_SEND_REQUEST, 400)
            }
            res.send(mapOf("success" to true, "requestId" to result.getOrNull()))
        }
    })

    post("/api/temporal-homes/block", Handler { req, res ->
        runBlocking {
            val sessionResult = validationService.validateSession(req.getSession())
            if (sessionResult is ServiceResult.Forbidden) {
                return@runBlocking res.respondUnauthorized()
            }
            val session = (sessionResult as ServiceResult.Success).data

            val userResult = validationService.validateBlockRescuerRequest(session.userId)
            when (userResult) {
                is ServiceResult.NotFound -> return@runBlocking res.respondNotFound()
                is ServiceResult.Forbidden -> return@runBlocking res.respondForbidden()
                is ServiceResult.Error -> return@runBlocking res.respondError(userResult.message, 403)
                else -> {}
            }

            val body = req.receiveJson<BlockRescuerRequest>()

            val blocked = temporalHomeService.blockRescuer(session.userId, body.rescuerId)
            res.send(mapOf("blocked" to blocked))
        }
    })
}

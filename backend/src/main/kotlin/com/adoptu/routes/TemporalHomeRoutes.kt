package com.adoptu.routes

import com.adoptu.dto.input.*
import com.adoptu.plugins.respondError
import com.adoptu.plugins.respondForbidden
import com.adoptu.plugins.respondNotFound
import com.adoptu.plugins.respondUnauthorized
import com.adoptu.services.ServiceResult
import com.adoptu.services.TemporalHomeService
import com.adoptu.services.auth.SessionUser
import com.adoptu.services.validation.TemporalHomesValidationService
import com.adoptu.services.validation.ValidationConstants
import io.ktor.http.HttpHeaders
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.koin.ktor.ext.inject

fun Route.temporalHomeRoutes() {
    val temporalHomeService by inject<TemporalHomeService>()
    val validationService by inject<TemporalHomesValidationService>()

    route("/api/users") {
        post("/temporal-home") {
            val sessionResult = validationService.validateSession(call.sessions.get<SessionUser>())
            if (sessionResult is ServiceResult.Forbidden) {
                return@post call.respondUnauthorized()
            }
            val session = (sessionResult as ServiceResult.Success).data
            
            val body = call.receive<CreateTemporalHomeRequest>()
            val validationResult = validationService.validateCreateTemporalHomeRequest(session.userId, body)
            if (validationResult is ServiceResult.Error) {
                return@post call.respondError(validationResult.message, 400)
            }

            try {
                val temporalHome = temporalHomeService.createTemporalHome(session.userId, body)
                temporalHomeService.activateTemporalHomeProfile(session.userId)
                call.respond(temporalHome)
            } catch (e: Exception) {
                call.respondError(e.message ?: "Failed to create temporal home", 500)
            }
        }

        get("/temporal-home") {
            val sessionResult = validationService.validateSession(call.sessions.get<SessionUser>())
            if (sessionResult is ServiceResult.Forbidden) {
                return@get call.respondUnauthorized()
            }
            val session = (sessionResult as ServiceResult.Success).data

            val temporalHome = temporalHomeService.getTemporalHome(session.userId)
            if (temporalHome == null) {
                return@get call.respondError(ValidationConstants.TEMPORAL_HOME_PROFILE_NOT_FOUND, 404)
            }
            call.respond(temporalHome)
        }

        put("/temporal-home") {
            val sessionResult = validationService.validateSession(call.sessions.get<SessionUser>())
            if (sessionResult is ServiceResult.Forbidden) {
                return@put call.respondUnauthorized()
            }
            val session = (sessionResult as ServiceResult.Success).data

            val profileResult = validationService.validateTemporalHomeProfile(session.userId)
            if (profileResult is ServiceResult.Error) {
                return@put call.respondError(profileResult.message, 404)
            }

            val body = call.receive<UpdateTemporalHomeRequest>()
            
            try {
                val updated = temporalHomeService.updateTemporalHome(session.userId, body)
                if (updated == null) {
                    return@put call.respondError("Failed to update temporal home", 500)
                }
                call.respond(updated)
            } catch (e: Exception) {
                call.respondError(e.message ?: "Failed to update temporal home", 500)
            }
        }

        get("/temporal-home/requests") {
            val sessionResult = validationService.validateSession(call.sessions.get<SessionUser>())
            if (sessionResult is ServiceResult.Forbidden) {
                return@get call.respondUnauthorized()
            }
            val session = (sessionResult as ServiceResult.Success).data

            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@get call.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data

            val roleResult = validationService.validateRole(user, "TEMPORAL_HOME")
            if (roleResult is ServiceResult.Forbidden) {
                return@get call.respondForbidden()
            }

            val requests = temporalHomeService.getMyRequests(session.userId)
            call.respond(requests)
        }
    }

    route("/api/temporal-homes") {
        get {
            val country = call.parameters["country"]
            val state = call.parameters["state"]
            val city = call.parameters["city"]
            val zip = call.parameters["zip"]
            val neighborhood = call.parameters["neighborhood"]

            val params = TemporalHomeSearchParams(
                country = country,
                state = state,
                city = city,
                zip = zip,
                neighborhood = neighborhood
            )

            val results = temporalHomeService.searchTemporalHomes(params)
            // Public, unauthenticated listing - cached at the CDN edge via an
            // EXACT path_pattern ("/api/temporal-homes", no wildcard) in
            // infra/cloudfront.tf. POST /api/temporal-homes/request shares
            // this prefix and is authenticated - a wildcard would also force
            // that route's cache behavior to a GET/HEAD/OPTIONS-only
            // allowed_methods list, which would make CloudFront reject the POST.
            call.response.header(HttpHeaders.CacheControl, "public, max-age=30")
            call.respond(results)
        }

        get("/{id}") {
            val temporalHomeIdResult = validationService.validateTemporalHomeId(call.parameters["id"])
            if (temporalHomeIdResult is ServiceResult.Error) {
                return@get call.respondError(temporalHomeIdResult.message, 400)
            }
            val temporalHomeId = (temporalHomeIdResult as ServiceResult.Success).data

            val temporalHome = temporalHomeService.getTemporalHome(temporalHomeId)
            if (temporalHome == null) {
                return@get call.respondNotFound()
            }
            call.response.header(HttpHeaders.CacheControl, "public, max-age=30")
            call.respond(temporalHome)
        }

        post("/request") {
            val sessionResult = validationService.validateSession(call.sessions.get<SessionUser>())
            if (sessionResult is ServiceResult.Forbidden) {
                return@post call.respondUnauthorized()
            }
            val session = (sessionResult as ServiceResult.Success).data

            val userResult = validationService.validateRescuerRole(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@post call.respondNotFound()
            }
            if (userResult is ServiceResult.Error) {
                return@post call.respondError(userResult.message, 403)
            }

            val body = call.receive<SendTemporalHomeRequestRequest>()

            val messageResult = validationService.validateRequired(body.message, "Message")
            if (messageResult is ServiceResult.Error) {
                return@post call.respondError(messageResult.message, 400)
            }

            val result = temporalHomeService.sendRequest(session.userId, body)
            if (result.isFailure) {
                return@post call.respondError(ValidationConstants.FAILED_TO_SEND_REQUEST, 400)
            }
            call.respond(mapOf("success" to true, "requestId" to result.getOrNull()))
        }
    }

    route("/api/temporal-homes") {
        get("/block/{temporalHomeId}") {
            val temporalHomeIdResult = validationService.validateTemporalHomeId(call.parameters["temporalHomeId"])
            if (temporalHomeIdResult is ServiceResult.Error) {
                return@get call.respondError(temporalHomeIdResult.message, 400)
            }
            val temporalHomeId = (temporalHomeIdResult as ServiceResult.Success).data
            
            val rescuerIdResult = validationService.validateRescuerId(call.parameters["rescuer"])
            if (rescuerIdResult is ServiceResult.Error) {
                return@get call.respondError(rescuerIdResult.message, 400)
            }
            val rescuerId = (rescuerIdResult as ServiceResult.Success).data

            val blocked = temporalHomeService.blockRescuer(temporalHomeId, rescuerId)
            call.respond(mapOf("blocked" to blocked))
        }

        post("/block") {
            val sessionResult = validationService.validateSession(call.sessions.get<SessionUser>())
            if (sessionResult is ServiceResult.Forbidden) {
                return@post call.respondUnauthorized()
            }
            val session = (sessionResult as ServiceResult.Success).data

            val userResult = validationService.validateBlockRescuerRequest(session.userId)
            when (userResult) {
                is ServiceResult.NotFound -> return@post call.respondNotFound()
                is ServiceResult.Forbidden -> return@post call.respondForbidden()
                is ServiceResult.Error -> return@post call.respondError(userResult.message, 403)
                else -> {}
            }

            val body = call.receive<BlockRescuerRequest>()

            val blocked = temporalHomeService.blockRescuer(session.userId, body.rescuerId)
            call.respond(mapOf("blocked" to blocked))
        }
    }
}
package com.adoptu.routes

import com.adoptu.dto.input.CreateMultiPhotographerRequestRequest
import com.adoptu.dto.input.CreatePhotographyRequestRequest
import com.adoptu.dto.input.PhotographerSettingsRequest
import com.adoptu.dto.input.RoleActivationRequest
import com.adoptu.dto.input.UpdatePhotographyRequestRequest
import com.adoptu.dto.input.UserDto
import com.adoptu.services.PhotographerService
import com.adoptu.services.ServiceResult
import com.adoptu.services.UserService
import com.adoptu.services.validation.PhotographersValidationService
import com.adoptu.web.Deps
import com.adoptu.web.pathParam
import com.adoptu.web.queryParam
import com.adoptu.web.receiveJson
import com.adoptu.web.respondData
import com.adoptu.web.respondError
import com.adoptu.web.respondForbidden
import com.adoptu.web.respondNotFound
import com.adoptu.web.respondUnauthorized
import com.universaliun.auth.backend.infrastructure.currentPrincipal
import io.helidon.http.HeaderNames
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRules
import io.helidon.webserver.http.ServerRequest
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject

fun HttpRules.photographerRoutes() {
    val photographerService by Deps.inject<PhotographerService>()
    val validationService by Deps.inject<PhotographersValidationService>()
    val userService by Deps.inject<UserService>()

    fun validateUser(req: ServerRequest): ServiceResult<UserDto> = runBlocking {
        val principal = req.currentPrincipal() ?: return@runBlocking ServiceResult.Forbidden
        validationService.validateUserById(principal.userId.value.toInt())
    }

    get("/api/photographers", Handler { req, res ->
        val country = req.queryParam("country")
        val state = req.queryParam("state")
        val photographers = runBlocking { photographerService.getPhotographers(country, state) }
        // Public, unauthenticated listing - cached at the CDN edge via an
        // EXACT path_pattern ("/api/photographers", no wildcard) in
        // infra/cloudfront.tf. This prefix also has authenticated routes
        // like GET /api/photographers/requests (a user's own requests) -
        // a wildcard here would risk serving one user's private request
        // list to another from the shared edge cache.
        res.header(HeaderNames.CACHE_CONTROL, "public, max-age=30")
        res.send(photographers)
    })

    // Own photographer settings - a second call rather than denormalizing these fields onto
    // AuthMeResponse/UserDto, so /api/auth/me stays a plain identity/session payload.
    get("/api/photographers/me", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val photographer = photographerService.getPhotographerById(principal.userId.value.toInt())
                ?: return@runBlocking res.respondNotFound()
            res.send(photographer)
        }
    })

    post("/api/photographers/profile", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userId = principal.userId.value.toInt()
            val body = req.receiveJson<RoleActivationRequest>()
            if (body.activate) {
                val existing = userService.getById(userId) ?: return@runBlocking res.respondNotFound()
                if (!existing.isEmailVerified) {
                    return@runBlocking res.respondError("Please verify your account email before publishing this profile", 403)
                }
            }
            val user = if (body.activate) {
                photographerService.activatePhotographerProfile(userId)
            } else {
                photographerService.deactivatePhotographerProfile(userId)
            }
            val userResult = validationService.validateUser(user)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }

            res.send(user!!)
        }
    })

    put("/api/photographers/settings", Handler { req, res ->
        runBlocking {
            val userResult = validateUser(req)
            when (userResult) {
                is ServiceResult.Forbidden -> return@runBlocking res.respondUnauthorized()
                is ServiceResult.NotFound -> return@runBlocking res.respondNotFound()
                else -> {}
            }
            val user = (userResult as ServiceResult.Success).data

            val roleResult = validationService.validateRole(user, "PHOTOGRAPHER")
            if (roleResult is ServiceResult.Forbidden) {
                return@runBlocking res.respondForbidden()
            }

            val body = req.receiveJson<PhotographerSettingsRequest>()
            val feeResult = validationService.validatePhotographerFee(body.photographerFee)
            if (feeResult is ServiceResult.Error) {
                return@runBlocking res.respondError(feeResult.message, 400)
            }

            try {
                // user.id is the same id validateUser(req) just resolved principal.userId to -
                // no need to re-derive it from the request a second time.
                val photographer = photographerService.updatePhotographerSettings(user.id, body)
                if (photographer == null) {
                    return@runBlocking res.respondNotFound()
                }
                res.send(photographer)
            } catch (e: IllegalArgumentException) {
                res.respondError(e.message ?: "Invalid request", 400)
            }
        }
    })

    post("/api/photographers/requests", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val body = req.receiveJson<CreatePhotographyRequestRequest>()

            val result = photographerService.createPhotographyRequest(
                requesterId = principal.userId.value.toInt(),
                photographerId = body.photographerId,
                petId = body.petId,
                message = body.message
            )

            res.send(result)
        }
    })

    post("/api/photographers/requests/multiple", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val body = req.receiveJson<CreateMultiPhotographerRequestRequest>()

            val result = photographerService.createPhotographyRequest(
                requesterId = principal.userId.value.toInt(),
                photographerIds = body.photographerIds,
                petId = body.petId,
                message = body.message
            )

            result.fold(
                onSuccess = { requestIds ->
                    res.send(mapOf("success" to true, "requestIds" to requestIds))
                },
                onFailure = { error ->
                    res.respondError(error.message ?: "Failed to create requests", 400)
                }
            )
        }
    })

    get("/api/photographers/requests", Handler { req, res ->
        runBlocking {
            val userResult = validateUser(req)
            when (userResult) {
                is ServiceResult.Forbidden -> return@runBlocking res.respondUnauthorized()
                is ServiceResult.NotFound -> return@runBlocking res.respondNotFound()
                else -> {}
            }
            val user = (userResult as ServiceResult.Success).data

            val result = photographerService.getRequestsForUser(user)
            res.send(result)
        }
    })

    put("/api/photographers/requests/{id}", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val idResult = validationService.validateId(req.pathParam("id"))
            if (idResult is ServiceResult.Error) {
                return@runBlocking res.respondError(idResult.message, 400)
            }
            val requestId = (idResult as ServiceResult.Success).data

            val body = req.receiveJson<UpdatePhotographyRequestRequest>()
            val userId = principal.userId.value.toInt()
            val userResult = validationService.validateUserById(userId)
            val user = if (userResult is ServiceResult.Success) userResult.data else null

            try {
                res.respondData(photographerService.updatePhotographyRequest(userId, user, requestId, body))
            } catch (e: IllegalArgumentException) {
                res.respondError(e.message ?: "Invalid request", 400)
            }
        }
    })
}

package com.adoptu.routes

import com.adoptu.dto.input.CreateVolunteerApplicationRequest
import com.adoptu.dto.input.UpdateVolunteerStatusRequest
import com.adoptu.services.ServiceResult
import com.adoptu.services.VolunteerService
import com.adoptu.services.validation.UsersValidationService
import com.adoptu.web.Deps
import com.universaliun.auth.backend.infrastructure.currentPrincipal
import com.adoptu.web.pathParam
import com.adoptu.web.receiveJson
import com.adoptu.web.respondData
import com.adoptu.web.respondError
import com.adoptu.web.respondNotFound
import com.adoptu.web.respondUnauthorized
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRules
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject

// All authenticated - a volunteer application is a relationship between two accounts, not
// public directory data (that's the redacted RescuerDirectoryDto - see UsersRoutes.kt).
fun HttpRules.volunteerRoutes() {
    val volunteerService by Deps.inject<VolunteerService>()
    val validationService by Deps.inject<UsersValidationService>()

    post("/api/volunteers", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val body = req.receiveJson<CreateVolunteerApplicationRequest>()
            res.respondData(volunteerService.apply(principal.userId.value.toInt(), body))
        }
    })

    put("/api/volunteers/{id}/status", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(principal.userId.value.toInt())
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val id = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondError("Invalid id", 400)

            val body = req.receiveJson<UpdateVolunteerStatusRequest>()
            res.respondData(volunteerService.updateStatus(id, body.status, principal.userId.value.toInt(), activeRoles))
        }
    })

    get("/api/users/volunteer/applications", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            res.send(volunteerService.getMyApplications(principal.userId.value.toInt()))
        }
    })

    get("/api/users/rescuer/volunteers", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(principal.userId.value.toInt())
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()

            res.respondData(volunteerService.getApplicationsForRescuer(principal.userId.value.toInt(), principal.userId.value.toInt(), activeRoles))
        }
    })
}

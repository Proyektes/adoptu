package com.adoptu.routes

import com.adoptu.dto.input.CreateSponsorshipOfferRequest
import com.adoptu.services.ServiceResult
import com.adoptu.services.SponsorshipService
import com.adoptu.services.validation.UsersValidationService
import com.adoptu.web.Deps
import com.adoptu.web.getSession
import com.adoptu.web.pathParam
import com.adoptu.web.receiveJson
import com.adoptu.web.respondData
import com.adoptu.web.respondNotFound
import com.adoptu.web.respondUnauthorized
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRules
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject

// All authenticated - an offer is a message to a specific rescuer, not public directory data.
fun HttpRules.sponsorshipRoutes() {
    val sponsorshipService by Deps.inject<SponsorshipService>()
    val validationService by Deps.inject<UsersValidationService>()

    post("/api/sponsorships", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val body = req.receiveJson<CreateSponsorshipOfferRequest>()
            res.respondData(sponsorshipService.createOffer(session.userId, body))
        }
    })

    put("/api/sponsorships/{id}/read", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()

            val id = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondNotFound()
            res.respondData(sponsorshipService.markRead(id, session.userId, activeRoles))
        }
    })

    get("/api/users/rescuer/sponsorships", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()

            res.respondData(sponsorshipService.getForRescuer(session.userId, session.userId, activeRoles))
        }
    })

    get("/api/users/sponsorships/mine", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            res.send(sponsorshipService.getForSponsor(session.userId))
        }
    })
}

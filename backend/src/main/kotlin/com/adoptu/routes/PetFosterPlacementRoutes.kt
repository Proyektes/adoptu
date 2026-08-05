package com.adoptu.routes

import com.adoptu.dto.input.CreateFosterPlacementRequest
import com.adoptu.services.PetFosterPlacementService
import com.adoptu.services.ServiceResult
import com.adoptu.services.validation.PetsValidationService
import com.adoptu.services.validation.ValidationConstants
import com.adoptu.web.Deps
import com.adoptu.web.getSession
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

// All authenticated - unlike medical events, a foster placement isn't a public trust signal,
// it's operational logistics (who currently has the pet). Rescuer/admin for everything except
// the temporal home's own "what's currently with me" view.
fun HttpRules.petFosterPlacementRoutes() {
    val placementService by Deps.inject<PetFosterPlacementService>()
    val validationService by Deps.inject<PetsValidationService>()

    post("/api/pets/{id}/foster-placements", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val petId = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondError(ValidationConstants.INVALID_ID)

            val body = req.receiveJson<CreateFosterPlacementRequest>()
            res.respondData(placementService.createPlacement(petId, session.userId, activeRoles, body))
        }
    })

    get("/api/pets/{id}/foster-placements", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val petId = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondError(ValidationConstants.INVALID_ID)

            res.respondData(placementService.getHistoryForPet(petId, session.userId, activeRoles))
        }
    })

    put("/api/pets/foster-placements/{id}/end", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val placementId = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondError(ValidationConstants.INVALID_ID)

            res.respondData(placementService.endPlacement(placementId, session.userId, activeRoles))
        }
    })
}

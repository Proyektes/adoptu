package com.adoptu.routes

import com.adoptu.dto.input.CreatePetMedicalEventRequest
import com.adoptu.services.PetMedicalEventService
import com.adoptu.services.ServiceResult
import com.adoptu.services.validation.PetsValidationService
import com.adoptu.services.validation.ValidationConstants
import com.adoptu.web.Deps
import com.universaliun.auth.backend.infrastructure.currentPrincipal
import com.adoptu.web.pathParam
import com.adoptu.web.receiveJson
import com.adoptu.web.respondData
import com.adoptu.web.respondError
import com.adoptu.web.respondNotFound
import com.adoptu.web.respondSuccess
import com.adoptu.web.respondUnauthorized
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRules
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject

// GET is public (a pet's vaccination/deworming history is a trust signal shown on its detail
// page, same visibility as the existing free-text vaccinations field); POST/DELETE require the
// owning rescuer or an admin, matching pet images/adoption-request review.
fun HttpRules.petMedicalEventRoutes() {
    val medicalEventService by Deps.inject<PetMedicalEventService>()
    val validationService by Deps.inject<PetsValidationService>()

    get("/api/pets/{id}/medical-events", Handler { req, res ->
        val petId = req.pathParam("id").toIntOrNull() ?: return@Handler res.respondError(ValidationConstants.INVALID_ID)
        runBlocking { res.send(medicalEventService.getForPet(petId)) }
    })

    post("/api/pets/{id}/medical-events", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(principal.userId.value.toInt())
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val petId = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondError(ValidationConstants.INVALID_ID)

            val body = req.receiveJson<CreatePetMedicalEventRequest>()
            res.respondData(medicalEventService.create(petId, principal.userId.value.toInt(), activeRoles, body))
        }
    })

    delete("/api/pets/medical-events/{eventId}", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(principal.userId.value.toInt())
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val eventId = req.pathParam("eventId").toIntOrNull() ?: return@runBlocking res.respondError(ValidationConstants.INVALID_ID)

            res.respondSuccess(medicalEventService.delete(eventId, principal.userId.value.toInt(), activeRoles))
        }
    })
}

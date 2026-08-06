package com.adoptu.routes

import com.adoptu.dto.input.CreatePetEditSuggestionRequest
import com.adoptu.dto.input.PetEditSuggestionStatus
import com.adoptu.services.PetEditSuggestionService
import com.adoptu.services.ServiceResult
import com.adoptu.services.validation.UsersValidationService
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

// All authenticated - a suggestion is a proposal for a specific pet, not public directory data.
fun HttpRules.petEditSuggestionRoutes() {
    val suggestionService by Deps.inject<PetEditSuggestionService>()
    val validationService by Deps.inject<UsersValidationService>()

    post("/api/pets/{id}/edit-suggestions", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val petId = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondError("Invalid id", 400)
            val body = req.receiveJson<CreatePetEditSuggestionRequest>()
            res.respondData(suggestionService.createSuggestion(petId, session.userId, body))
        }
    })

    put("/api/pets/edit-suggestions/{id}/status", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()

            val id = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondError("Invalid id", 400)
            val body = req.receiveJson<Map<String, String>>()
            val status = try {
                PetEditSuggestionStatus.valueOf(body["status"] ?: "")
            } catch (e: Exception) {
                return@runBlocking res.respondError("Invalid status", 400)
            }
            res.respondData(suggestionService.updateStatus(id, status, session.userId, activeRoles))
        }
    })

    get("/api/users/rescuer/edit-suggestions", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()

            res.respondData(suggestionService.getPendingForRescuer(session.userId, session.userId, activeRoles))
        }
    })

    get("/api/users/volunteer/edit-suggestions", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            res.send(suggestionService.getMySuggestions(session.userId))
        }
    })
}

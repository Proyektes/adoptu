package com.adoptu.routes

import com.adoptu.dto.input.CreateAdoptionRequestRequest
import com.adoptu.dto.input.CreatePetRequest
import com.adoptu.dto.input.UpdatePetRequest
import com.adoptu.dto.input.UserRole
import com.adoptu.plugins.DataResponder
import com.adoptu.plugins.SuccessResponder
import com.adoptu.plugins.respondData
import com.adoptu.plugins.respondError
import com.adoptu.plugins.respondForbidden
import com.adoptu.plugins.respondNotFound
import com.adoptu.plugins.respondServiceResult
import com.adoptu.plugins.respondSuccess
import com.adoptu.plugins.respondUnauthorized
import com.adoptu.services.validation.ValidationConstants
import com.adoptu.services.PetService
import com.adoptu.services.ServiceResult
import com.adoptu.services.UserService
import com.adoptu.services.auth.SessionUser
import com.adoptu.services.validation.PetsValidationService
import io.ktor.http.HttpHeaders
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.koin.ktor.ext.inject

fun Route.petsRoutes() {
    val petService by inject<PetService>()
    val validationService by inject<PetsValidationService>()

    route("/api/pets") {
        get {
            val type = call.request.queryParameters["type"]
            val promoted = call.request.queryParameters["promoted"]?.toBoolean() ?: false
            val country = call.request.queryParameters["country"]
            if (country.isNullOrBlank()) {
                return@get call.respondError(ValidationConstants.COUNTRY_IS_REQUIRED, 400)
            }
            val pets = petService.getAll(type, promoted, country)
            // Public, unauthenticated, read-heavy listing - safe to cache at the CDN edge.
            // Paired with an ordered_cache_behavior for the exact "/api/pets" path in
            // infra/cloudfront.tf (not a wildcard, so sibling authenticated routes like
            // /api/pets/my-adoption-requests and /api/pets/mine are never swept into
            // the same cache behavior).
            call.response.header(HttpHeaders.CacheControl, "public, max-age=30")
            call.respond(pets)
        }

        get("/mine") {
            val session = call.sessions.get<SessionUser>()
                ?: return@get call.respondUnauthorized()
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@get call.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }
            if (!activeRoles.contains("RESCUER") && !activeRoles.contains("ADMIN")) return@get call.respondForbidden()

            call.respond(petService.getMine())
        }

        get("/{id}") {
            val id = call.parameters["id"]!!.toIntOrNull() ?: return@get call.respondError(ValidationConstants.INVALID_ID)
            val pet = petService.getById(id)
            if (pet != null) call.respond(pet) else call.respondError(ValidationConstants.NOT_FOUND, 404)
        }

        post {
            val session = call.sessions.get<SessionUser>()
                ?: return@post call.respondUnauthorized()
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@post call.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }
            if (!activeRoles.contains("RESCUER") && !activeRoles.contains("ADMIN")) return@post call.respondForbidden()

            val request = call.receive<CreatePetRequest>()
            try {
                val pet = petService.create(session.userId, request)
                call.respond(pet)
            } catch (e: IllegalArgumentException) {
                call.respondError(e.message ?: "Invalid request", 400)
            }
        }

        put("/{id}") {
            val session = call.sessions.get<SessionUser>()
                ?: return@put call.respondUnauthorized()
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@put call.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val id = call.parameters["id"]!!.toIntOrNull() ?: return@put call.respondError(ValidationConstants.INVALID_ID)

            val body = call.receive<UpdatePetRequest>()
            try {
                call.respondData(petService.update(id, session.userId, activeRoles, body))
            } catch (e: IllegalArgumentException) {
                call.respondError(e.message ?: "Invalid request", 400)
            }
        }

        delete("/{id}") {
            val session = call.sessions.get<SessionUser>()
                ?: return@delete call.respondUnauthorized()
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@delete call.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val id = call.parameters["id"]!!.toIntOrNull() ?: return@delete call.respondError(ValidationConstants.INVALID_ID)

            call.respondSuccess(petService.delete(id, session.userId, activeRoles))
        }

        post("/{id}/images") {
            val session = call.sessions.get<SessionUser>()
                ?: return@post call.respondUnauthorized()
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@post call.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val petId = call.parameters["id"]?.toIntOrNull() ?: return@post call.respondError(ValidationConstants.INVALID_ID)

            val imageIdsParam = call.request.queryParameters["imageIds"]
            if (imageIdsParam != null) {
                try {
                    val imageIds = imageIdsParam.split(",").mapNotNull { it.toIntOrNull() }
                    val result = petService.updatePetImages(petId, session.userId, activeRoles, imageIds)
                    when (result) {
                        is ServiceResult.Success -> call.respond(mapOf("images" to result.data))
                        is ServiceResult.NotFound -> call.respondError(ValidationConstants.NOT_FOUND, 404)
                        is ServiceResult.Forbidden -> call.respondError("Forbidden", 403)
                        is ServiceResult.Error -> call.respondError(result.message)
                    }
                } catch (e: Exception) {
                    call.respondError("Failed to update images. Please try again later.", 500)
                }
                return@post
            }

            val multipart = call.receiveMultipart()
            var imageData: ByteArray? = null
            var fileName = "image"
            var contentType = "image/jpeg"
            var isPrimary = false

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FileItem -> {
                        fileName = part.originalFileName ?: "image"
                        contentType = part.contentType?.toString() ?: "image/jpeg"
                        @Suppress("DEPRECATION")
                        imageData = part.streamProvider().readBytes()
                    }
                    is PartData.FormItem -> {
                        if (part.name == "isPrimary") {
                            isPrimary = part.value.toBoolean()
                        }
                    }
                    else -> { /* NO action */ }
                }
            }

            if (imageData == null) {
                return@post call.respondError("No storage provided")
            }

            try {
                call.respondData(
                    petService.uploadAndAddImage(
                        petId = petId,
                        userId = session.userId,
                        userRoles = activeRoles,
                        imageName = fileName,
                        contentType = contentType,
                        imageData = imageData,
                        isPrimary = isPrimary
                    )
                )
            } catch (e: Exception) {
                call.respondError("Failed to upload storage. Please try again later.", 500)
            }
        }

        delete("/{petId}/images/{imageId}") {
            val session = call.sessions.get<SessionUser>()
                ?: return@delete call.respondUnauthorized()
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@delete call.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val petId = call.parameters["petId"]?.toIntOrNull() ?: return@delete call.respondError("Invalid pet ID")
            val imageId = call.parameters["imageId"]?.toIntOrNull() ?: return@delete call.respondError("Invalid storage ID")

            try {
                call.respondSuccess(
                    petService.removeImage(petId, imageId, session.userId, activeRoles)
                )
            } catch (e: Exception) {
                call.respondError("Failed to delete storage. Please try again later.", 500)
            }
        }

        put("/{petId}/images/{imageId}/primary") {
            val session = call.sessions.get<SessionUser>()
                ?: return@put call.respondUnauthorized()
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@put call.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val petId = call.parameters["petId"]?.toIntOrNull() ?: return@put call.respondError("Invalid pet ID")
            val imageId = call.parameters["imageId"]?.toIntOrNull() ?: return@put call.respondError("Invalid storage ID")

            try {
                call.respondSuccess(
                    petService.setPrimaryImage(petId, imageId, session.userId, activeRoles)
                )
            } catch (e: Exception) {
                call.respondError("Failed to set primary storage. Please try again later.", 500)
            }
        }

        post("/{id}/adopt") {
            val session = call.sessions.get<SessionUser>()
                ?: return@post call.respondUnauthorized()
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@post call.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { UserRole.valueOf(it.name) }
            if (!activeRoles.contains(UserRole.ADOPTER)) return@post call.respondError("Only adopters can request adoption", 403)

            val id = call.parameters["id"]!!.toIntOrNull() ?: return@post call.respondError(ValidationConstants.INVALID_ID)
            val body = call.receive<CreateAdoptionRequestRequest>()
            val message = body.message

            val request = petService.createAdoptionRequest(id, session.userId, message)
            call.respond(request)
        }

        get("/{id}/adoption-requests") {
            val session = call.sessions.get<SessionUser>()
                ?: return@get call.respondUnauthorized()
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@get call.respondError("User not found", 404)
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val id = call.parameters["id"]!!.toIntOrNull() ?: return@get call.respondError(ValidationConstants.INVALID_ID)

            call.respondData(petService.getAdoptionRequestsForPet(id, session.userId, activeRoles))
        }

        put("/adoption-requests/{requestId}") {
            val session = call.sessions.get<SessionUser>()
                ?: return@put call.respondUnauthorized()
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@put call.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val requestId = call.parameters["requestId"]!!.toIntOrNull() ?: return@put call.respondError(ValidationConstants.INVALID_ID)
            val params = call.receiveParameters()
            val status = params["status"] ?: return@put call.respondError("status required")

            call.respondData(petService.updateAdoptionRequest(requestId, status, session.userId, activeRoles))
        }

        get("/my-adoption-requests") {
            val session = call.sessions.get<SessionUser>()
                ?: return@get call.respondUnauthorized()
            val requests = petService.getMyAdoptionRequests(session.userId)
            call.respond(requests)
        }
    }
}

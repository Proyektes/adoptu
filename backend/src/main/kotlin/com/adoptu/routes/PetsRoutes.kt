package com.adoptu.routes

import com.adoptu.config.AppConfig
import com.adoptu.dto.input.CreateAdoptionRequestRequest
import com.adoptu.dto.input.CreatePetRequest
import com.adoptu.dto.input.UpdatePetRequest
import com.adoptu.dto.input.UserRole
import com.adoptu.services.PetService
import com.adoptu.services.ServiceResult
import com.adoptu.services.UserService
import com.adoptu.services.validation.PetsValidationService
import com.adoptu.services.validation.ValidationConstants
import com.adoptu.web.Deps
import com.adoptu.web.SuccessResponse
import com.adoptu.web.getSession
import com.adoptu.web.pathParam
import com.adoptu.web.queryParam
import com.adoptu.web.receiveFormParameters
import com.adoptu.web.receiveJson
import com.adoptu.web.receiveMultipart
import com.adoptu.web.respondData
import com.adoptu.web.respondError
import com.adoptu.web.respondForbidden
import com.adoptu.web.respondHtml
import com.adoptu.web.respondInvalidId
import com.adoptu.web.respondNotFound
import com.adoptu.web.respondSuccess
import com.adoptu.web.respondUnauthorized
import io.helidon.http.HeaderNames
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRules
import kotlinx.coroutines.runBlocking
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.title
import org.koin.core.component.inject

fun HttpRules.petsRoutes() {
    val petService by Deps.inject<PetService>()
    val validationService by Deps.inject<PetsValidationService>()
    val config by Deps.inject<AppConfig>()

    // Bot-only route: pet-detail is a client-rendered static shell (real pet data is fetched via
    // JS after load), which social-preview crawlers never see because they don't run JavaScript.
    // A CloudFront Function on /pet/{id} (infra/cloudfront-functions/site-rewrite.js) detects known
    // crawler user-agents and routes just those requests here instead of the static site, so
    // WhatsApp/Facebook/etc. get real per-pet og:image/title tags. Real visitors never hit this -
    // they keep getting /pet-detail.html untouched.
    get("/api/share/pet/{id}", Handler { req, res ->
        val id = req.pathParam("id").toIntOrNull() ?: return@Handler res.respondNotFound("Pet not found")
        val pet = runBlocking { petService.getById(id) } ?: return@Handler res.respondNotFound("Pet not found")

        val baseUrl = config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:4000"
        val canonicalUrl = "$baseUrl/pet/${pet.id}"
        val image = pet.images.firstOrNull { it.isPrimary } ?: pet.images.firstOrNull()
        val pageTitle = "${pet.name} - Adopt-U"
        val description = pet.description.take(160).ifBlank { "Meet ${pet.name} on Adopt-U." }

        res.respondHtml {
            head {
                meta { charset = "utf-8" }
                title { +pageTitle }
                meta { name = "description"; content = description }
                link { rel = "canonical"; href = canonicalUrl }
                meta { attributes["property"] = "og:type"; attributes["content"] = "website" }
                meta { attributes["property"] = "og:title"; attributes["content"] = pageTitle }
                meta { attributes["property"] = "og:description"; attributes["content"] = description }
                meta { attributes["property"] = "og:url"; attributes["content"] = canonicalUrl }
                image?.let { meta { attributes["property"] = "og:image"; attributes["content"] = it.imageUrl } }
                meta { name = "twitter:card"; content = "summary_large_image" }
                meta { name = "twitter:title"; content = pageTitle }
                meta { name = "twitter:description"; content = description }
                image?.let { meta { name = "twitter:image"; content = it.imageUrl } }
            }
            body { a(href = canonicalUrl) { +"View $pageTitle" } }
        }
    })

    get("/api/pets", Handler { req, res ->
        val type = req.queryParam("type")
        val promoted = req.queryParam("promoted")?.toBoolean() ?: false
        val country = req.queryParam("country")
        if (country.isNullOrBlank()) {
            return@Handler res.respondError(ValidationConstants.COUNTRY_IS_REQUIRED, 400)
        }
        runBlocking {
            val pets = petService.getAll(type, promoted, country)
            // Public, unauthenticated, read-heavy listing - safe to cache at the CDN edge.
            // Paired with an ordered_cache_behavior for the exact "/api/pets" path in
            // infra/cloudfront.tf (not a wildcard, so sibling authenticated routes like
            // /api/pets/my-adoption-requests and /api/pets/mine are never swept into
            // the same cache behavior).
            res.header(HeaderNames.CACHE_CONTROL, "public, max-age=30")
            res.send(pets)
        }
    })

    // Registered before the "/api/pets/{id}" template below: Helidon matches route rules for a
    // given method in registration order, so literal-segment routes must precede templated ones
    // at the same path depth or they'd be shadowed (unlike Ktor's specificity-first routing tree).
    get("/api/pets/mine", Handler { req, res ->
        val session = req.getSession()
            ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }
            if (!activeRoles.contains("RESCUER") && !activeRoles.contains("ADMIN")) return@runBlocking res.respondForbidden()

            res.send(petService.getMine())
        }
    })

    get("/api/pets/my-adoption-requests", Handler { req, res ->
        val session = req.getSession()
            ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val requests = petService.getMyAdoptionRequests(session.userId)
            res.send(requests)
        }
    })

    get("/api/pets/{id}", Handler { req, res ->
        val id = req.pathParam("id").toIntOrNull() ?: return@Handler res.respondError(ValidationConstants.INVALID_ID)
        runBlocking {
            val pet = petService.getById(id)
            if (pet != null) res.send(pet) else res.respondError(ValidationConstants.NOT_FOUND, 404)
        }
    })

    post("/api/pets", Handler { req, res ->
        val session = req.getSession()
            ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }
            if (!activeRoles.contains("RESCUER") && !activeRoles.contains("ADMIN")) return@runBlocking res.respondForbidden()
            if (!user.isEmailVerified && !activeRoles.contains("ADMIN")) {
                return@runBlocking res.respondError("Please verify your account email before publishing pets", 403)
            }

            val request = req.receiveJson<CreatePetRequest>()
            try {
                val pet = petService.create(session.userId, request)
                res.send(pet)
            } catch (e: IllegalArgumentException) {
                res.respondError(e.message ?: "Invalid request", 400)
            }
        }
    })

    put("/api/pets/{id}", Handler { req, res ->
        val session = req.getSession()
            ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val id = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondError(ValidationConstants.INVALID_ID)

            val body = req.receiveJson<UpdatePetRequest>()
            try {
                res.respondData(petService.update(id, session.userId, activeRoles, body))
            } catch (e: IllegalArgumentException) {
                res.respondError(e.message ?: "Invalid request", 400)
            }
        }
    })

    delete("/api/pets/{id}", Handler { req, res ->
        val session = req.getSession()
            ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val id = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondError(ValidationConstants.INVALID_ID)

            res.respondSuccess(petService.delete(id, session.userId, activeRoles))
        }
    })

    post("/api/pets/{id}/images", Handler { req, res ->
        val session = req.getSession()
            ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val petId = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondError(ValidationConstants.INVALID_ID)

            val imageIdsParam = req.queryParam("imageIds")
            if (imageIdsParam != null) {
                try {
                    val imageIds = imageIdsParam.split(",").mapNotNull { it.toIntOrNull() }
                    val result = petService.updatePetImages(petId, session.userId, activeRoles, imageIds)
                    when (result) {
                        is ServiceResult.Success -> res.send(mapOf("images" to result.data))
                        is ServiceResult.NotFound -> res.respondError(ValidationConstants.NOT_FOUND, 404)
                        is ServiceResult.Forbidden -> res.respondError("Forbidden", 403)
                        is ServiceResult.Error -> res.respondError(result.message)
                    }
                } catch (e: Exception) {
                    res.respondError("Failed to update images. Please try again later.", 500)
                }
                return@runBlocking
            }

            val (filePart, formFields) = req.receiveMultipart()
            val isPrimary = formFields["isPrimary"]?.toBoolean() ?: false

            if (filePart == null) {
                return@runBlocking res.respondError("No storage provided")
            }

            try {
                res.respondData(
                    petService.uploadAndAddImage(
                        petId = petId,
                        userId = session.userId,
                        userRoles = activeRoles,
                        imageName = filePart.fileName,
                        contentType = filePart.contentType,
                        imageData = filePart.bytes,
                        isPrimary = isPrimary
                    )
                )
            } catch (e: Exception) {
                res.respondError("Failed to upload storage. Please try again later.", 500)
            }
        }
    })

    delete("/api/pets/{petId}/images/{imageId}", Handler { req, res ->
        val session = req.getSession()
            ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val petId = req.pathParam("petId").toIntOrNull() ?: return@runBlocking res.respondError("Invalid pet ID")
            val imageId = req.pathParam("imageId").toIntOrNull() ?: return@runBlocking res.respondError("Invalid storage ID")

            try {
                res.respondSuccess(
                    petService.removeImage(petId, imageId, session.userId, activeRoles)
                )
            } catch (e: Exception) {
                res.respondError("Failed to delete storage. Please try again later.", 500)
            }
        }
    })

    put("/api/pets/{petId}/images/{imageId}/primary", Handler { req, res ->
        val session = req.getSession()
            ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val petId = req.pathParam("petId").toIntOrNull() ?: return@runBlocking res.respondError("Invalid pet ID")
            val imageId = req.pathParam("imageId").toIntOrNull() ?: return@runBlocking res.respondError("Invalid storage ID")

            try {
                res.respondSuccess(
                    petService.setPrimaryImage(petId, imageId, session.userId, activeRoles)
                )
            } catch (e: Exception) {
                res.respondError("Failed to set primary storage. Please try again later.", 500)
            }
        }
    })

    post("/api/pets/{id}/adopt", Handler { req, res ->
        val session = req.getSession()
            ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { UserRole.valueOf(it.name) }
            if (!activeRoles.contains(UserRole.ADOPTER)) return@runBlocking res.respondError("Only adopters can request adoption", 403)

            val id = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondError(ValidationConstants.INVALID_ID)
            val body = req.receiveJson<CreateAdoptionRequestRequest>()
            val message = body.message

            val request = petService.createAdoptionRequest(id, session.userId, message)
            res.send(request)
        }
    })

    get("/api/pets/{id}/adoption-requests", Handler { req, res ->
        val session = req.getSession()
            ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondError("User not found", 404)
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val id = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondError(ValidationConstants.INVALID_ID)

            res.respondData(petService.getAdoptionRequestsForPet(id, session.userId, activeRoles))
        }
    })

    put("/api/pets/adoption-requests/{requestId}", Handler { req, res ->
        val session = req.getSession()
            ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val userResult = validationService.validateUserById(session.userId)
            if (userResult is ServiceResult.NotFound) {
                return@runBlocking res.respondNotFound()
            }
            val user = (userResult as ServiceResult.Success).data
            val activeRoles = user.activeRoles.map { it.name }.toSet()
            val requestId = req.pathParam("requestId").toIntOrNull() ?: return@runBlocking res.respondError(ValidationConstants.INVALID_ID)
            val params = req.receiveFormParameters()
            val status = params["status"] ?: return@runBlocking res.respondError("status required")

            res.respondData(petService.updateAdoptionRequest(requestId, status, session.userId, activeRoles))
        }
    })
}

// Admin-only paginated/searchable pets overview, backing the Admin Panel's Manage Pets tab.
// Deliberately separate from getMine()/"my pets" (rescuer self-service page, also usable by
// admins for full add/edit) - that page has no pagination and this doesn't touch it.
fun HttpRules.adminPetsRoutes() {
    val petService by Deps.inject<PetService>()
    val userService by Deps.inject<UserService>()

    get("/api/admin/pets", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val admin = userService.getById(session.userId)
            if (admin == null || !admin.activeRoles.contains(UserRole.ADMIN)) {
                return@runBlocking res.respondForbidden()
            }

            val page = req.queryParam("page")?.toIntOrNull() ?: 1
            val pageSize = req.queryParam("pageSize")?.toIntOrNull() ?: 20
            val search = req.queryParam("search")?.takeIf { it.isNotBlank() }
            val includeInactive = req.queryParam("includeInactive")?.toBoolean() ?: false

            val result = petService.getAllForAdmin(page, pageSize, search, includeInactive)
            res.send(result)
        }
    })

    post("/api/admin/pets/{id}/deactivate", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val admin = userService.getById(session.userId)
            if (admin == null || !admin.activeRoles.contains(UserRole.ADMIN)) {
                return@runBlocking res.respondForbidden()
            }

            val id = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondInvalidId(ValidationConstants.INVALID_ID)
            if (petService.getById(id) == null) {
                return@runBlocking res.respondNotFound()
            }

            val deactivated = petService.deactivatePet(id, session.userId)
            if (deactivated) {
                res.send(SuccessResponse(success = true))
            } else {
                res.respondError("Failed to deactivate pet", 500)
            }
        }
    })

    post("/api/admin/pets/{id}/reactivate", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val admin = userService.getById(session.userId)
            if (admin == null || !admin.activeRoles.contains(UserRole.ADMIN)) {
                return@runBlocking res.respondForbidden()
            }

            val id = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondInvalidId(ValidationConstants.INVALID_ID)
            if (petService.getById(id) == null) {
                return@runBlocking res.respondNotFound()
            }

            val reactivated = petService.reactivatePet(id)
            if (reactivated) {
                res.send(SuccessResponse(success = true))
            } else {
                res.respondError("Failed to reactivate pet", 500)
            }
        }
    })
}

package com.adoptu.routes

import com.adoptu.adapters.authkit.AdoptuRole
import com.adoptu.dto.input.CreateShelterRequest
import com.adoptu.dto.input.UpdateShelterRequest
import com.adoptu.services.ShelterService
import com.adoptu.services.validation.ValidationConstants
import com.adoptu.web.Deps
import com.universaliun.auth.backend.infrastructure.currentPrincipal
import com.adoptu.web.pathParam
import com.adoptu.web.queryParam
import com.adoptu.web.receiveJson
import com.adoptu.web.respondData
import com.adoptu.web.respondError
import com.adoptu.web.respondForbidden
import com.adoptu.web.respondSuccess
import com.adoptu.web.respondUnauthorized
import io.helidon.http.HeaderNames
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRules
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject

fun HttpRules.shelterRoutes() {
    val shelterService by Deps.inject<ShelterService>()

    get("/api/shelters", Handler { req, res ->
        val country = req.queryParam("country")
        if (country.isNullOrBlank()) {
            res.respondError(ValidationConstants.COUNTRY_IS_REQUIRED, 400)
            return@Handler
        }
        val state = req.queryParam("state")
        val city = req.queryParam("city")
        val neighborhood = req.queryParam("neighborhood")
        val zip = req.queryParam("zip")
        val shelters = runBlocking { shelterService.getAll(country, state, city, neighborhood, zip) }
        // Public, unauthenticated listing - cached at the CDN edge (see
        // infra/cloudfront.tf: ordered_cache_behavior for "/api/shelters*").
        // The admin variant lives under the separate /api/admin/shelters
        // prefix, so this wildcard never touches an authenticated route.
        res.header(HeaderNames.CACHE_CONTROL, "public, max-age=30")
        res.send(shelters)
    })

    get("/api/shelters/countries", Handler { _, res ->
        val countries = runBlocking { shelterService.getCountries() }
        res.send(mapOf("countries" to countries))
    })

    get("/api/shelters/countries/{country}/states", Handler { req, res ->
        val country = req.pathParam("country")
        val states = runBlocking { shelterService.getStatesByCountry(country) }
        res.send(mapOf("states" to states))
    })

    get("/api/shelters/{id}", Handler { req, res ->
        val id = req.pathParam("id").toIntOrNull() ?: return@Handler res.respondError(ValidationConstants.INVALID_ID)
        val shelter = runBlocking { shelterService.getById(id) }
        if (shelter != null) {
            res.send(shelter)
        } else {
            res.respondError(ValidationConstants.SHELTER_NOT_FOUND, 404)
        }
    })
}

fun HttpRules.adminShelterRoutes() {
    val shelterService by Deps.inject<ShelterService>()

    get("/api/admin/shelters", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            if (!principal.hasRole(AdoptuRole.ADMIN)) {
                return@runBlocking res.respondForbidden()
            }
            val country = req.queryParam("country")
            val state = req.queryParam("state")
            val city = req.queryParam("city")
            val neighborhood = req.queryParam("neighborhood")
            val zip = req.queryParam("zip")
            val shelters = if (country.isNullOrBlank()) {
                emptyList()
            } else {
                shelterService.getAll(country, state, city, neighborhood, zip)
            }
            res.send(shelters)
        }
    })

    get("/api/admin/shelters/{id}", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        val id = req.pathParam("id").toIntOrNull() ?: return@Handler res.respondError(ValidationConstants.INVALID_ID)
        runBlocking {
            if (!principal.hasRole(AdoptuRole.ADMIN)) {
                return@runBlocking res.respondForbidden()
            }
            val shelter = shelterService.getById(id)
            if (shelter != null) {
                res.send(shelter)
            } else {
                res.respondError(ValidationConstants.SHELTER_NOT_FOUND, 404)
            }
        }
    })

    post("/api/admin/shelters", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            if (!principal.hasRole(AdoptuRole.ADMIN)) {
                return@runBlocking res.respondForbidden()
            }
            val request = req.receiveJson<CreateShelterRequest>()
            try {
                val shelter = shelterService.create(request)
                res.send(shelter)
            } catch (e: IllegalArgumentException) {
                res.respondError(e.message ?: "Invalid request", 400)
            }
        }
    })

    put("/api/admin/shelters/{id}", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        val id = req.pathParam("id").toIntOrNull() ?: return@Handler res.respondError(ValidationConstants.INVALID_ID)
        runBlocking {
            if (!principal.hasRole(AdoptuRole.ADMIN)) {
                return@runBlocking res.respondForbidden()
            }
            val request = req.receiveJson<UpdateShelterRequest>()
            res.respondData(shelterService.update(id, request))
        }
    })

    delete("/api/admin/shelters/{id}", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()
        val id = req.pathParam("id").toIntOrNull() ?: return@Handler res.respondError(ValidationConstants.INVALID_ID)
        runBlocking {
            if (!principal.hasRole(AdoptuRole.ADMIN)) {
                return@runBlocking res.respondForbidden()
            }
            res.respondSuccess(shelterService.delete(id))
        }
    })
}

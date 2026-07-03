package com.adoptu.routes

import com.adoptu.dto.input.CreateSterilizationLocationRequest
import com.adoptu.dto.input.UpdateSterilizationLocationRequest
import com.adoptu.services.SterilizationLocationService
import com.adoptu.services.validation.ValidationConstants
import com.adoptu.web.Deps
import com.adoptu.web.pathParam
import com.adoptu.web.queryParam
import com.adoptu.web.receiveJson
import com.adoptu.web.respondData
import com.adoptu.web.respondError
import com.adoptu.web.respondSuccess
import io.helidon.http.HeaderNames
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRules
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject

fun HttpRules.sterilizationLocationRoutes() {
    val service by Deps.inject<SterilizationLocationService>()

    get("/api/sterilization-locations", Handler { req, res ->
        val country = req.queryParam("country")
        val state = req.queryParam("state")
        val city = req.queryParam("city")
        val neighborhood = req.queryParam("neighborhood")
        val zip = req.queryParam("zip")
        val locations = runBlocking { service.getAll(country, state, city, neighborhood, zip) }
        // Public, unauthenticated listing - cached at the CDN edge (see
        // infra/cloudfront.tf: ordered_cache_behavior for
        // "/api/sterilization-locations*"). The admin variant lives under
        // the separate /api/admin/sterilization-locations prefix, so this
        // wildcard never touches an authenticated route.
        res.header(HeaderNames.CACHE_CONTROL, "public, max-age=30")
        res.send(locations)
    })

    get("/api/sterilization-locations/grouped", Handler { _, res ->
        val locations = runBlocking { service.getGroupedByLocation() }
        res.send(locations)
    })

    get("/api/sterilization-locations/countries", Handler { _, res ->
        val countries = runBlocking { service.getCountries() }
        res.send(mapOf("countries" to countries))
    })

    get("/api/sterilization-locations/countries/{country}/states", Handler { req, res ->
        val country = req.pathParam("country")
        val states = runBlocking { service.getStatesByCountry(country) }
        res.send(mapOf("states" to states))
    })

    get("/api/sterilization-locations/countries/{country}/states/{state}/cities", Handler { req, res ->
        val country = req.pathParam("country")
        val state = req.pathParam("state")
        val cities = runBlocking { service.getCitiesByCountryAndState(country, state) }
        res.send(mapOf("cities" to cities))
    })

    get("/api/sterilization-locations/{id}", Handler { req, res ->
        val id = req.pathParam("id").toIntOrNull() ?: return@Handler res.respondError(ValidationConstants.INVALID_ID)
        val location = runBlocking { service.getById(id) }
        if (location != null) {
            res.send(location)
        } else {
            res.respondError(ValidationConstants.STERILIZATION_LOCATION_NOT_FOUND, 404)
        }
    })
}

fun HttpRules.adminSterilizationLocationRoutes() {
    val service by Deps.inject<SterilizationLocationService>()

    get("/api/admin/sterilization-locations", Handler { req, res ->
        val country = req.queryParam("country")
        val state = req.queryParam("state")
        val city = req.queryParam("city")
        val neighborhood = req.queryParam("neighborhood")
        val zip = req.queryParam("zip")
        val locations = runBlocking { service.getAll(country, state, city, neighborhood, zip) }
        res.send(locations)
    })

    get("/api/admin/sterilization-locations/{id}", Handler { req, res ->
        val id = req.pathParam("id").toIntOrNull() ?: return@Handler res.respondError(ValidationConstants.INVALID_ID)
        val location = runBlocking { service.getById(id) }
        if (location != null) {
            res.send(location)
        } else {
            res.respondError(ValidationConstants.STERILIZATION_LOCATION_NOT_FOUND, 404)
        }
    })

    post("/api/admin/sterilization-locations", Handler { req, res ->
        val request = req.receiveJson<CreateSterilizationLocationRequest>()
        try {
            val location = runBlocking { service.create(request) }
            res.send(location)
        } catch (e: IllegalArgumentException) {
            res.respondError(e.message ?: "Invalid request", 400)
        }
    })

    put("/api/admin/sterilization-locations/{id}", Handler { req, res ->
        val id = req.pathParam("id").toIntOrNull() ?: return@Handler res.respondError(ValidationConstants.INVALID_ID)
        val request = req.receiveJson<UpdateSterilizationLocationRequest>()
        res.respondData(runBlocking { service.update(id, request) })
    })

    delete("/api/admin/sterilization-locations/{id}", Handler { req, res ->
        val id = req.pathParam("id").toIntOrNull() ?: return@Handler res.respondError(ValidationConstants.INVALID_ID)
        res.respondSuccess(runBlocking { service.delete(id) })
    })
}

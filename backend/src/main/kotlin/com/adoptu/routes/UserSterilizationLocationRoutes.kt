package com.adoptu.routes

import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.CreateUserSterilizationLocationRequest
import com.adoptu.dto.input.UpdateUserSterilizationLocationRequest
import com.adoptu.services.UserSterilizationLocationService
import com.adoptu.web.Deps
import com.universaliun.auth.backend.infrastructure.currentPrincipal
import com.adoptu.web.queryParam
import com.adoptu.web.receiveJson
import com.adoptu.web.respondData
import com.adoptu.web.respondError
import com.adoptu.web.respondUnauthorized
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRules
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject

fun HttpRules.userSterilizationLocationRoutes() {
    val service by Deps.inject<UserSterilizationLocationService>()
    // Constructed directly rather than injected, same as AuthRoutes.kt's own UserRepository use -
    // avoids adding a new Koin binding every route-level test module would otherwise need.
    val userRepository = UserRepository(clock = kotlin.time.Clock.System)

    post("/api/users/sterilization-location", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            // AuthPrincipal carries userId/email from the JWT, not displayName - a DB lookup
            // is the only source for it now (the old SessionUser cookie stored it directly).
            val displayName = userRepository.getById(principal.userId.value.toInt())?.displayName
                ?: return@runBlocking res.respondUnauthorized()
            val body = req.receiveJson<CreateUserSterilizationLocationRequest>()
            try {
                val location = service.create(principal.userId.value.toInt(), principal.email, displayName, body)
                res.send(location)
            } catch (e: IllegalArgumentException) {
                res.respondError(e.message ?: "Invalid request", 400)
            } catch (e: Exception) {
                res.respondError(e.message ?: "Failed to create sterilization location", 500)
            }
        }
    })

    get("/api/users/sterilization-location", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val location = service.getByUserId(principal.userId.value.toInt())
            if (location == null) {
                res.respondError("Sterilization location not found", 404)
            } else {
                res.send(location)
            }
        }
    })

    put("/api/users/sterilization-location", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val displayName = userRepository.getById(principal.userId.value.toInt())?.displayName
                ?: return@runBlocking res.respondUnauthorized()
            val body = req.receiveJson<UpdateUserSterilizationLocationRequest>()
            res.respondData(service.update(principal.userId.value.toInt(), principal.email, displayName, body))
        }
    })

    delete("/api/users/sterilization-location", Handler { req, res ->
        val principal = req.currentPrincipal() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            res.respondData(service.delete(principal.userId.value.toInt()))
        }
    })

    get("/api/user-sterilization-locations", Handler { req, res ->
        val country = req.queryParam("country")
        if (country.isNullOrBlank()) {
            return@Handler res.respondError("Country is required", 400)
        }
        val state = req.queryParam("state")
        val city = req.queryParam("city")
        val neighborhood = req.queryParam("neighborhood")
        val zip = req.queryParam("zip")

        runBlocking {
            val locations = service.search(country, state, city, neighborhood, zip)
            res.send(locations)
        }
    })
}

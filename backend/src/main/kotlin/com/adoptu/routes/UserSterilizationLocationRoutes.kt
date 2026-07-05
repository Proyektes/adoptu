package com.adoptu.routes

import com.adoptu.dto.input.CreateUserSterilizationLocationRequest
import com.adoptu.dto.input.UpdateUserSterilizationLocationRequest
import com.adoptu.services.UserSterilizationLocationService
import com.adoptu.web.Deps
import com.adoptu.web.getSession
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

    post("/api/users/sterilization-location", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val body = req.receiveJson<CreateUserSterilizationLocationRequest>()
            try {
                val location = service.create(session.userId, session.email, session.displayName, body)
                res.send(location)
            } catch (e: IllegalArgumentException) {
                res.respondError(e.message ?: "Invalid request", 400)
            } catch (e: Exception) {
                res.respondError(e.message ?: "Failed to create sterilization location", 500)
            }
        }
    })

    get("/api/users/sterilization-location", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val location = service.getByUserId(session.userId)
            if (location == null) {
                res.respondError("Sterilization location not found", 404)
            } else {
                res.send(location)
            }
        }
    })

    put("/api/users/sterilization-location", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val body = req.receiveJson<UpdateUserSterilizationLocationRequest>()
            res.respondData(service.update(session.userId, session.email, session.displayName, body))
        }
    })

    delete("/api/users/sterilization-location", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            res.respondData(service.delete(session.userId))
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

package com.adoptu.routes

import com.adoptu.dto.input.CreateUserShelterRequest
import com.adoptu.dto.input.UpdateUserShelterRequest
import com.adoptu.services.UserShelterService
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

fun HttpRules.userShelterRoutes() {
    val service by Deps.inject<UserShelterService>()

    post("/api/users/shelter", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val body = req.receiveJson<CreateUserShelterRequest>()
            try {
                val shelter = service.create(session.userId, body)
                res.send(shelter)
            } catch (e: IllegalArgumentException) {
                res.respondError(e.message ?: "Invalid request", 400)
            } catch (e: Exception) {
                res.respondError(e.message ?: "Failed to create shelter", 500)
            }
        }
    })

    get("/api/users/shelter", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val shelter = service.getByUserId(session.userId)
            if (shelter == null) {
                res.respondError("Shelter profile not found", 404)
            } else {
                res.send(shelter)
            }
        }
    })

    put("/api/users/shelter", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val body = req.receiveJson<UpdateUserShelterRequest>()
            res.respondData(service.update(session.userId, body))
        }
    })

    delete("/api/users/shelter", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            res.respondData(service.delete(session.userId))
        }
    })

    get("/api/user-shelters", Handler { req, res ->
        val country = req.queryParam("country")
        if (country.isNullOrBlank()) {
            return@Handler res.respondError("Country is required", 400)
        }
        val state = req.queryParam("state")
        val city = req.queryParam("city")
        val zip = req.queryParam("zip")

        runBlocking {
            val shelters = service.search(country, state, city, zip)
            res.send(shelters)
        }
    })
}

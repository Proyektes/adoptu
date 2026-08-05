package com.adoptu.routes

import com.adoptu.dto.input.CreateSavedSearchRequest
import com.adoptu.services.SavedSearchService
import com.adoptu.web.Deps
import com.adoptu.web.getSession
import com.adoptu.web.pathParam
import com.adoptu.web.receiveJson
import com.adoptu.web.respondError
import com.adoptu.web.respondSuccess
import com.adoptu.web.respondUnauthorized
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRules
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject

// Authenticated-only (unlike Urgent Rescuer/Lost & Found reports) - a saved search is a standing
// subscription, there's no meaningful anonymous equivalent to notify.
fun HttpRules.savedSearchRoutes() {
    val savedSearchService by Deps.inject<SavedSearchService>()

    post("/api/saved-searches", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()
        runBlocking {
            val body = req.receiveJson<CreateSavedSearchRequest>()
            try {
                res.send(savedSearchService.create(session.userId, body))
            } catch (e: IllegalArgumentException) {
                res.respondError(e.message ?: "Invalid saved search")
            }
        }
    })

    get("/api/saved-searches", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()
        runBlocking { res.send(savedSearchService.list(session.userId)) }
    })

    delete("/api/saved-searches/{id}", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()
        val id = req.pathParam("id").toIntOrNull() ?: return@Handler res.respondError("Invalid id")
        runBlocking { res.respondSuccess(savedSearchService.delete(id, session.userId)) }
    })
}

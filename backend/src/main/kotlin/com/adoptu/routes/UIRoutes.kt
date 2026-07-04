package com.adoptu.routes

import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.UserRole
import com.adoptu.pages.*
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.services.auth.SessionUser
import com.adoptu.services.auth.WebAuthnService
import com.adoptu.web.Deps
import com.adoptu.web.getSession
import com.adoptu.web.pathParam
import com.adoptu.web.queryParam
import com.adoptu.web.respondHtml
import com.adoptu.web.respondRedirect
import com.adoptu.web.setSession
import io.helidon.http.Status
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRules
import kotlinx.coroutines.runBlocking
import kotlinx.html.*
import org.koin.core.component.inject

data class NavParams(
    val isLoggedIn: Boolean = false,
    val isAdmin: Boolean = false,
    val isRescuerOrAdmin: Boolean = false,
    val isTemporalHomeOrAdmin: Boolean = false
)

fun HttpRules.uiRoutes() {
    val webAuthnService by Deps.inject<WebAuthnService>()
    val userRepository by Deps.inject<UserRepositoryPort>()

    fun getNavParams(session: SessionUser?): NavParams = runBlocking {
        if (session != null) {
            val isAdmin = userRepository.isRoleActive(session.userId, UserRole.ADMIN)
            val isRescuer = userRepository.isRoleActive(session.userId, UserRole.RESCUER)
            val isTemporalHome = userRepository.isRoleActive(session.userId, UserRole.TEMPORAL_HOME)
            NavParams(
                isLoggedIn = true,
                isAdmin = isAdmin,
                isRescuerOrAdmin = isRescuer || isAdmin,
                isTemporalHomeOrAdmin = isTemporalHome || isAdmin
            )
        } else {
            NavParams()
        }
    }

    head("/", Handler { _, res -> res.status(Status.OK_200).send() })
    get("/", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { indexPage(navParams) }
    })
    get("/login", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { loginPage(navParams) }
    })
    get("/register", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { registerPage(navParams) }
    })
    get("/photographers", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { photographersPage(navParams) }
    })
    get("/pet-food", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { petFoodPage(navParams) }
    })
    get("/pet/{id}", Handler { req, res ->
        val id = req.pathParam("id").toIntOrNull()
        if (id == null) return@Handler res.respondRedirect("/pets")
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { petDetailPage(navParams) }
    })
    get("/pets", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { petsPage(navParams) }
    })
    get("/my-pets", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { myPetsPage(navParams) }
    })
    get("/profile", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { profilePage(navParams) }
    })
    get("/admin", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { adminPage(navParams) }
    })
    get("/admin/shelters", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { adminSheltersPage(navParams) }
    })
    get("/privacy", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { privacyPage(navParams) }
    })
    get("/terms", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { termsPage(navParams) }
    })
    get("/temporal-home", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { temporalHomeProfilePage(navParams) }
    })
    get("/temporal-homes", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { temporalHomesSearchPage(navParams) }
    })
    get("/temporal-home/{id}", Handler { req, res ->
        val id = req.pathParam("id").toIntOrNull()
        if (id == null) return@Handler res.respondRedirect("/temporal-homes")
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { temporalHomeDetailPage(navParams) }
    })
    get("/shelters", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { sheltersPage(navParams) }
    })
    get("/sterilization-locations", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { sterilizationLocationsPage(navParams) }
    })
    get("/admin/sterilization-locations", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { adminSterilizationLocationsPage(navParams) }
    })
    get("/verify", Handler { req, res ->
        val token = req.queryParam("token")
        if (token.isNullOrBlank()) {
            val navParams = getNavParams(req.getSession())
            res.respondHtml(Status.OK_200) { emailVerificationPage(false, "en", navParams) }
            return@Handler
        }

        runBlocking {
            // Get userId from token before verification (which deletes the token)
            val userId = userRepository.getUserIdByToken(token)
            val result = webAuthnService.verifyTokenAndGetLanguage(token)

            if (result.first && userId != null) {
                // Log the user in after successful verification
                val user = userRepository.getById(userId)
                if (user != null) {
                    res.setSession(SessionUser(user.id, user.username, user.displayName))
                }
            }
            val emailVerificationNavParams = getNavParams(req.getSession())
            res.respondHtml(Status.OK_200) { emailVerificationPage(result.first, result.second, emailVerificationNavParams) }
        }
    })
    get("/verify-email", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        val token = req.queryParam("token")
        val success: Boolean
        val language: String
        if (token.isNullOrBlank()) {
            success = false
            language = "en"
        } else {
            val result = runBlocking { webAuthnService.verifyTokenAndGetLanguage(token) }
            success = result.first
            language = result.second
        }
        res.respondHtml(Status.OK_200) { emailVerificationPage(success, language, navParams) }
    })
    get("/forgot-password", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { forgotPasswordPage(navParams) }
    })
    get("/reset-password", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { resetPasswordPage(navParams) }
    })
    get("/magic-link-login", Handler { req, res ->
        val token = req.queryParam("token")
        if (token.isNullOrBlank()) {
            res.respondRedirect("/login?error=invalid_token")
        } else {
            res.respondRedirect("/api/auth/magic-link-login?token=$token")
        }
    })
    get("/verify-email-change", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { emailChangeVerificationPage(navParams) }
    })
    get("/verify-profile-email", Handler { req, res ->
        val navParams = getNavParams(req.getSession())
        res.respondHtml(Status.OK_200) { profileEmailVerificationPage(navParams) }
    })
    get("/temporal-home/block/{temporalHomeId}", Handler { req, res ->
        val temporalHomeId = req.pathParam("temporalHomeId").toIntOrNull()
        val rescuerId = req.queryParam("rescuer")?.toIntOrNull()
        if (temporalHomeId != null && rescuerId != null) {
            res.respondHtml(Status.OK_200) {
                head { title { +"Block Rescuer" } }
                body {
                    h1 { +"Report as Spam & Block Rescuer" }
                    p { +"Are you sure you want to block this rescuer from sending you more requests?" }
                    button(type = ButtonType.button) {
                        onClick = "blockRescuerAndRedirect($temporalHomeId, $rescuerId)"
                        +"Block Rescuer"
                    }
                    script(src = "/static/js/common.js") {}
                }
            }
        } else {
            res.respondRedirect("/temporal-home")
        }
    })
}

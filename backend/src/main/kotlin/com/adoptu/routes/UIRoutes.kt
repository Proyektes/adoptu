package com.adoptu.routes

import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.UserRole
import com.adoptu.pages.*
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.services.auth.SessionUser
import com.adoptu.services.auth.WebAuthnService
import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.html.*
import org.koin.ktor.ext.inject

data class NavParams(
    val isLoggedIn: Boolean = false,
    val isAdmin: Boolean = false,
    val isRescuerOrAdmin: Boolean = false,
    val isTemporalHomeOrAdmin: Boolean = false
)

fun Route.uiRoutes() {
    val webAuthnService by inject<WebAuthnService>()
    val userRepository by inject<UserRepositoryPort>()
    staticResources("/static", "static")
    
    suspend fun getNavParams(session: SessionUser?): NavParams {
        return if (session != null) {
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
    
    head("/") { call.respond(HttpStatusCode.OK) }
    get("/") { 
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { indexPage(navParams) } 
    }
    get("/login") { 
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { loginPage(navParams) } 
    }
    get("/register") { 
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { registerPage(navParams) } 
    }
    get("/photographers") { 
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { photographersPage(navParams) } 
    }
    get("/pet-food") { 
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { petFoodPage(navParams) } 
    }
    get("/pet/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null) return@get call.respondRedirect("/pets")
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { petDetailPage(navParams) }
    }
    get("/pets") { 
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { petsPage(navParams) } 
    }
    get("/my-pets") { 
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { myPetsPage(navParams) } 
    }
    get("/profile") { 
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { profilePage(navParams) } 
    }
    get("/admin") { 
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { adminPage(navParams) } 
    }
    get("/admin/shelters") { 
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { adminSheltersPage(navParams) } 
    }
    get("/privacy") { 
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { privacyPage(navParams) } 
    }
    get("/terms") { 
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { termsPage(navParams) } 
    }
    get("/temporal-home") { 
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { temporalHomeProfilePage(navParams) } 
    }
    get("/temporal-homes") {
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { temporalHomesSearchPage(navParams) }
    }
    get("/temporal-home/{id}") {
        val id = call.parameters["id"]?.toIntOrNull()
        if (id == null) return@get call.respondRedirect("/temporal-homes")
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { temporalHomeDetailPage(navParams) }
    }
    get("/shelters") {
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { sheltersPage(navParams) } 
    }
    get("/sterilization-locations") { 
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { sterilizationLocationsPage(navParams) } 
    }
    get("/admin/sterilization-locations") { 
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { adminSterilizationLocationsPage(navParams) } 
    }
    get("/verify") {
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        val token = call.request.queryParameters["token"]
        if (token.isNullOrBlank()) {
            call.respondHtml(HttpStatusCode.OK) { emailVerificationPage(false, "en", navParams) }
            return@get
        }
        
        // Get userId from token before verification (which deletes the token)
        val userId = userRepository.getUserIdByToken(token)
        val result = webAuthnService.verifyTokenAndGetLanguage(token)
        
        if (result.first && userId != null) {
            // Log the user in after successful verification
            val user = userRepository.getById(userId)
            if (user != null) {
                call.sessions.set(SessionUser(user.id, user.username, user.displayName))
            }
        }
        val emailVerificationNavParams = getNavParams(call.sessions.get())
        call.respondHtml(HttpStatusCode.OK) { emailVerificationPage(result.first, result.second, emailVerificationNavParams) }
    }
    get("/verify-email") {
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        val token = call.request.queryParameters["token"]
        val success: Boolean
        val language: String
        if (token.isNullOrBlank()) {
            success = false
            language = "en"
        } else {
            val result = webAuthnService.verifyTokenAndGetLanguage(token)
            success = result.first
            language = result.second
        }
        call.respondHtml(HttpStatusCode.OK) { emailVerificationPage(success, language, navParams) }
    }
    get("/forgot-password") { 
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { forgotPasswordPage(navParams) } 
    }
    get("/reset-password") { 
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { resetPasswordPage(navParams) } 
    }
    get("/magic-link-login") { 
        val token = call.request.queryParameters["token"]
        if (token.isNullOrBlank()) {
            call.respondRedirect("/login?error=invalid_token")
        } else {
            call.respondRedirect("/api/auth/magic-link-login?token=$token")
        }
    }
    get("/verify-email-change") { 
        val session: SessionUser? = call.sessions.get()
        val navParams = getNavParams(session)
        call.respondHtml(HttpStatusCode.OK) { emailChangeVerificationPage(navParams) } 
    }
    get("/temporal-home/block/{temporalHomeId}") {
        val temporalHomeId = call.parameters["temporalHomeId"]?.toIntOrNull()
        val rescuerId = call.request.queryParameters["rescuer"]?.toIntOrNull()
        if (temporalHomeId != null && rescuerId != null) {
            call.respondHtml(HttpStatusCode.OK) {
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
            call.respondRedirect("/temporal-home")
        }
    }
}

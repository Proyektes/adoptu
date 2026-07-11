package com.adoptu.routes

import com.adoptu.dto.input.AcceptTermsRequest
import com.adoptu.dto.input.BanUserRequest
import com.adoptu.dto.input.PhotographerSettingsRequest
import com.adoptu.dto.input.RoleActivationRequest
import com.adoptu.dto.input.UserRole
import com.adoptu.dto.output.SuccessWithErrorResponse
import com.adoptu.dto.output.VerificationResponse
import com.adoptu.services.EmailChangeService
import com.adoptu.services.PasswordService
import com.adoptu.services.PhotographerService
import com.adoptu.services.ProfileEmailVerificationService
import com.adoptu.services.UserService
import com.adoptu.services.VerifiableProfileType
import com.adoptu.services.auth.WebAuthnService
import com.adoptu.services.validation.ValidationConstants
import com.adoptu.web.Deps
import com.adoptu.web.SuccessResponse
import com.adoptu.web.getSession
import com.adoptu.web.pathParam
import com.adoptu.web.queryParam
import com.adoptu.web.receiveJson
import com.adoptu.web.respondError
import com.adoptu.web.respondForbidden
import com.adoptu.web.respondInvalidId
import com.adoptu.web.respondNotFound
import com.adoptu.web.respondUnauthorized
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRules
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject

data class UpdateProfileRequest(val displayName: String, val country: String? = null)

data class UpdateLanguageRequest(val language: String)

data class SetPasswordRequest(val encryptedPassword: String)

data class ChangePasswordRequest(val encryptedCurrentPassword: String, val encryptedNewPassword: String)

data class RequestEmailChangeRequest(val newEmail: String)

fun HttpRules.usersRoutes() {
    val userService by Deps.inject<UserService>()
    val photographerService by Deps.inject<PhotographerService>()
    val passwordService by Deps.inject<PasswordService>()
    val emailChangeService by Deps.inject<EmailChangeService>()
    val profileEmailVerificationService by Deps.inject<ProfileEmailVerificationService>()

    post("/api/users/accept-terms", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val body = req.receiveJson<AcceptTermsRequest>()
            val user = userService.acceptTerms(session.userId, body)
                ?: return@runBlocking res.respondNotFound()

            res.send(user)
        }
    })

    put("/api/users/profile", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val body = req.receiveJson<UpdateProfileRequest>()
            val language = req.queryParam("language")
            try {
                val user = userService.updateProfile(session.userId, body.displayName, language, body.country)
                    ?: return@runBlocking res.respondNotFound(ValidationConstants.USER_NOT_FOUND)
                res.send(user)
            } catch (e: IllegalArgumentException) {
                res.respondError(e.message ?: "Invalid request", 400)
            }
        }
    })

    put("/api/users/language", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val body = req.receiveJson<UpdateLanguageRequest>()
            try {
                val user = userService.updateLanguage(session.userId, body.language)
                    ?: return@runBlocking res.respondNotFound(ValidationConstants.USER_NOT_FOUND)
                res.send(user)
            } catch (e: IllegalArgumentException) {
                res.respondError(e.message ?: "Invalid request", 400)
            }
        }
    })

    get("/api/users/rescuers", Handler { _, res ->
        runBlocking {
            val rescuers = userService.getRescuers()
            res.send(rescuers)
        }
    })

    post("/api/users/rescuer-profile", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val body = req.receiveJson<RoleActivationRequest>()
            if (body.activate) {
                val existing = userService.getById(session.userId) ?: return@runBlocking res.respondNotFound()
                if (!existing.isEmailVerified) {
                    return@runBlocking res.respondError("Please verify your account email before publishing this profile", 403)
                }
            }
            val user = if (body.activate) {
                userService.activateRescuerProfile(session.userId)
            } else {
                userService.deactivateRescuerProfile(session.userId)
            } ?: return@runBlocking res.respondNotFound()

            res.send(user)
        }
    })

    post("/api/users/temporal-home-profile", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val body = req.receiveJson<RoleActivationRequest>()
            if (body.activate) {
                val existing = userService.getById(session.userId) ?: return@runBlocking res.respondNotFound()
                if (!existing.isEmailVerified) {
                    return@runBlocking res.respondError("Please verify your account email before publishing this profile", 403)
                }
            }
            val user = if (body.activate) {
                userService.activateTemporalHomeProfile(session.userId)
            } else {
                userService.deactivateTemporalHomeProfile(session.userId)
            } ?: return@runBlocking res.respondNotFound()

            res.send(user)
        }
    })

    put("/api/users/photographer-settings", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val body = req.receiveJson<PhotographerSettingsRequest>()
            val photographer = photographerService.updatePhotographerSettings(session.userId, body)
                ?: return@runBlocking res.respondNotFound()

            res.send(photographer)
        }
    })

    post("/api/users/photographer-profile", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val body = req.receiveJson<RoleActivationRequest>()
            if (body.activate) {
                val existing = userService.getById(session.userId) ?: return@runBlocking res.respondNotFound()
                if (!existing.isEmailVerified) {
                    return@runBlocking res.respondError("Please verify your account email before publishing this profile", 403)
                }
            }
            val user = if (body.activate) {
                photographerService.activatePhotographerProfile(session.userId)
            } else {
                photographerService.deactivatePhotographerProfile(session.userId)
            } ?: return@runBlocking res.respondNotFound()

            res.send(user)
        }
    })

    post("/api/users/shelter-profile", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val body = req.receiveJson<RoleActivationRequest>()
            if (body.activate) {
                val existing = userService.getById(session.userId) ?: return@runBlocking res.respondNotFound()
                if (!existing.isEmailVerified) {
                    return@runBlocking res.respondError("Please verify your account email before publishing this profile", 403)
                }
                if (!profileEmailVerificationService.isProfileEmailVerified(VerifiableProfileType.SHELTER, session.userId)) {
                    return@runBlocking res.respondError("Please verify your shelter's contact email before publishing this profile", 403)
                }
            }
            val user = if (body.activate) {
                userService.activateShelterProfile(session.userId)
            } else {
                userService.deactivateShelterProfile(session.userId)
            } ?: return@runBlocking res.respondNotFound()

            res.send(user)
        }
    })

    post("/api/users/sterilization-profile", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val body = req.receiveJson<RoleActivationRequest>()
            if (body.activate) {
                val existing = userService.getById(session.userId) ?: return@runBlocking res.respondNotFound()
                if (!existing.isEmailVerified) {
                    return@runBlocking res.respondError("Please verify your account email before publishing this profile", 403)
                }
                if (!profileEmailVerificationService.isProfileEmailVerified(VerifiableProfileType.STERILIZATION, session.userId)) {
                    return@runBlocking res.respondError("Please verify your contact email before publishing this profile", 403)
                }
            }
            val user = if (body.activate) {
                userService.activateSterilizationProfile(session.userId)
            } else {
                userService.deactivateSterilizationProfile(session.userId)
            } ?: return@runBlocking res.respondNotFound()

            res.send(user)
        }
    })

    get("/api/users/has-password", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        val hasPassword = runBlocking { passwordService.hasPassword(session.userId) }
        res.send(mapOf("hasPassword" to hasPassword))
    })

    post("/api/users/password", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val body = req.receiveJson<SetPasswordRequest>()
            val success = passwordService.setPassword(session.userId, body.encryptedPassword)
            if (success) {
                res.send(SuccessResponse(success = true))
            } else {
                res.send(SuccessWithErrorResponse(success = false, error = "Password must be at least 8 characters with uppercase, lowercase, number, and symbol."))
            }
        }
    })

    put("/api/users/password", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val body = req.receiveJson<ChangePasswordRequest>()
            val success = passwordService.changePassword(
                session.userId,
                body.encryptedCurrentPassword,
                body.encryptedNewPassword
            )
            if (success) {
                res.send(SuccessResponse(success = true))
            } else {
                res.send(SuccessWithErrorResponse(success = false, error = "Failed to change password. Current password may be incorrect or new password is invalid."))
            }
        }
    })

    post("/api/users/request-email-change", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val body = req.receiveJson<RequestEmailChangeRequest>()
            val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
            if (!emailRegex.matches(body.newEmail)) {
                return@runBlocking res.respondError("Invalid email format", 400)
            }

            val user = userService.getById(session.userId)
                ?: return@runBlocking res.respondNotFound()

            val result = emailChangeService.requestEmailChange(session.userId, body.newEmail, user.language)
            if (result.isFailure) {
                res.send(SuccessWithErrorResponse(success = false, error = result.exceptionOrNull()?.message ?: "Failed to request email change"))
            } else {
                res.send(SuccessResponse(success = true))
            }
        }
    })

    get("/api/users/verify-email-change", Handler { req, res ->
        val token = req.queryParam("token")
        if (token.isNullOrBlank()) {
            res.respondError("Token is required", 400)
            return@Handler
        }

        val success = runBlocking { emailChangeService.verifyEmailChange(token) }
        if (success) {
            res.send(VerificationResponse(success = true, message = "Email changed successfully"))
        } else {
            res.send(VerificationResponse(success = false, message = "Failed to change email. Token may be invalid or expired."))
        }
    })

    get("/api/users/verify-profile-email", Handler { req, res ->
        val token = req.queryParam("token")
        if (token.isNullOrBlank()) {
            res.respondError("Token is required", 400)
            return@Handler
        }

        val success = runBlocking { profileEmailVerificationService.verifyToken(token) }
        if (success) {
            res.send(VerificationResponse(success = true, message = "Contact email verified successfully"))
        } else {
            res.send(VerificationResponse(success = false, message = "Failed to verify email. The link may be invalid or expired."))
        }
    })
}

fun HttpRules.adminUsersRoutes() {
    val userService by Deps.inject<UserService>()
    val webAuthnService by Deps.inject<WebAuthnService>()

    get("/api/admin/users", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val user = userService.getById(session.userId)
            if (user == null || !user.activeRoles.contains(UserRole.ADMIN)) {
                return@runBlocking res.respondForbidden()
            }

            val page = req.queryParam("page")?.toIntOrNull() ?: 1
            val pageSize = req.queryParam("pageSize")?.toIntOrNull() ?: 20
            val role = req.queryParam("role")?.let { roleParam ->
                try { UserRole.valueOf(roleParam) } catch (e: Exception) { null }
            }
            val search = req.queryParam("search")?.takeIf { it.isNotBlank() }
            val includeInactive = req.queryParam("includeInactive")?.toBoolean() ?: false
            val includeBanned = req.queryParam("includeBanned")?.toBoolean() ?: false

            val result = userService.getAllUsers(page, pageSize, role, search, includeInactive, includeBanned)
            res.send(result)
        }
    })

    get("/api/admin/users/{id}", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val admin = userService.getById(session.userId)
            if (admin == null || !admin.activeRoles.contains(UserRole.ADMIN)) {
                return@runBlocking res.respondForbidden()
            }

            val id = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondInvalidId(ValidationConstants.INVALID_ID)
            val user = userService.getById(id) ?: return@runBlocking res.respondNotFound()
            res.send(user)
        }
    })

    post("/api/admin/users/{id}/ban", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val admin = userService.getById(session.userId)
            if (admin == null || !admin.activeRoles.contains(UserRole.ADMIN)) {
                return@runBlocking res.respondForbidden()
            }

            val id = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondInvalidId(ValidationConstants.INVALID_ID)
            val body = req.receiveJson<BanUserRequest>()

            if (id == session.userId) {
                return@runBlocking res.respondError("Cannot ban yourself", 400)
            }

            val targetUser = userService.getById(id)
            if (targetUser == null) {
                return@runBlocking res.respondNotFound()
            }
            if (targetUser.activeRoles.contains(UserRole.ADMIN)) {
                return@runBlocking res.respondError("Cannot ban an admin", 400)
            }

            val banned = userService.banUser(id, body.reason)
            if (banned) {
                res.send(SuccessResponse(success = true))
            } else {
                res.respondError("Failed to ban user", 500)
            }
        }
    })

    post("/api/admin/users/{id}/unban", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val admin = userService.getById(session.userId)
            if (admin == null || !admin.activeRoles.contains(UserRole.ADMIN)) {
                return@runBlocking res.respondForbidden()
            }

            val id = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondInvalidId(ValidationConstants.INVALID_ID)

            val unbanned = userService.unbanUser(id)
            if (unbanned) {
                res.send(SuccessResponse(success = true))
            } else {
                res.respondError("Failed to unban user", 500)
            }
        }
    })

    // Deactivate/reactivate: independent of Ban/Unban (isBanned) - an auditable, non-punitive
    // active/inactive state (deactivatedAt/deactivatedBy on Users), separate axis with its own
    // "Show inactive" filter in the admin UI. Self-targeting is blocked, same as ban.
    post("/api/admin/users/{id}/deactivate", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val admin = userService.getById(session.userId)
            if (admin == null || !admin.activeRoles.contains(UserRole.ADMIN)) {
                return@runBlocking res.respondForbidden()
            }

            val id = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondInvalidId(ValidationConstants.INVALID_ID)

            if (id == session.userId) {
                return@runBlocking res.respondError("Cannot deactivate yourself", 400)
            }

            if (userService.getById(id) == null) {
                return@runBlocking res.respondNotFound()
            }

            val deactivated = userService.deactivateUser(id, session.userId)
            if (deactivated) {
                res.send(SuccessResponse(success = true))
            } else {
                res.respondError("Failed to deactivate user", 500)
            }
        }
    })

    post("/api/admin/users/{id}/reactivate", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val admin = userService.getById(session.userId)
            if (admin == null || !admin.activeRoles.contains(UserRole.ADMIN)) {
                return@runBlocking res.respondForbidden()
            }

            val id = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondInvalidId(ValidationConstants.INVALID_ID)

            val reactivated = userService.reactivateUser(id)
            if (reactivated) {
                res.send(SuccessResponse(success = true))
            } else {
                res.respondError("Failed to reactivate user", 500)
            }
        }
    })

    // Admin-triggered account recovery: invalidates the target's password/passkeys and
    // re-sends the forgot-password email (see WebAuthnService.forcePasswordReset) - no
    // account/profile/pet data is touched. Self-targeting is blocked so an admin can't
    // accidentally lock themselves out via this endpoint.
    post("/api/admin/users/{id}/reset-password", Handler { req, res ->
        val session = req.getSession() ?: return@Handler res.respondUnauthorized()

        runBlocking {
            val admin = userService.getById(session.userId)
            if (admin == null || !admin.activeRoles.contains(UserRole.ADMIN)) {
                return@runBlocking res.respondForbidden()
            }

            val id = req.pathParam("id").toIntOrNull() ?: return@runBlocking res.respondInvalidId(ValidationConstants.INVALID_ID)

            if (id == session.userId) {
                return@runBlocking res.respondError("Cannot reset your own password this way", 400)
            }

            if (userService.getById(id) == null) {
                return@runBlocking res.respondNotFound()
            }

            val ok = webAuthnService.forcePasswordReset(id)
            if (ok) {
                res.send(SuccessResponse(success = true))
            } else {
                res.respondError("Failed to reset password", 500)
            }
        }
    })
}

package com.adoptu.routes

import com.adoptu.dto.input.UserDto
import com.adoptu.dto.input.UserRole
import com.adoptu.dto.output.AuthMeResponse
import com.adoptu.dto.output.RegistrationResponse
import com.adoptu.dto.output.SuccessWithErrorResponse
import com.adoptu.dto.output.VerificationResponse
import com.adoptu.plugins.SuccessResponse
import com.adoptu.plugins.respondError
import com.adoptu.services.ServiceResult
import com.adoptu.services.auth.SessionUser
import com.adoptu.services.auth.WebAuthnService
import com.adoptu.services.validation.AuthValidationService
import com.adoptu.services.crypto.CryptoService
import com.adoptu.adapters.db.repositories.UserRepository
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
data class EncryptedLoginRequest(
    val encryptedData: String
)

@Serializable
data class PasswordLoginRequest(
    val email: String,
    val encryptedPassword: String
)

fun Route.authRoutes() {
    val webAuthnService by inject<WebAuthnService>()
    val validationService by inject<AuthValidationService>()
    val config by inject<ApplicationConfig>()
    val adminEmail = config.propertyOrNull("admin.email")?.getString() ?: "admin@adopt-u.com"
    val userRepository = UserRepository(clock = kotlin.time.Clock.System)

    route("/api/auth") {
        post("/registration-options") {
            val params = call.receiveParameters()
            val email = params["email"] ?: return@post call.respondError("email required")
            val displayName = params["displayName"] ?: return@post call.respondError("displayName required")
            val language = params["language"] ?: "en"
            val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
            if (!emailRegex.matches(email)) return@post call.respondError(getLocalizedError("invalid email format", language))
            
            val existingUser = validationService.getUserByEmail(email)
            if (existingUser != null) {
                val isVerified = webAuthnService.isUserVerified(existingUser.id)
                if (isVerified) {
                    return@post call.respondError(getLocalizedError("email already registered", language))
                }
                webAuthnService.resendVerificationEmail(existingUser.id)
                return@post call.respondError(getLocalizedError("verification email sent", language))
            }
            
            val options = webAuthnService.generateRegistrationOptions(email, displayName)
            call.respond(options)
        }

        post("/register") {
            val params = call.receiveParameters()
            val email = params["email"] ?: return@post call.respondError("email required")
            val displayName = params["displayName"] ?: return@post call.respondError("displayName required")
            val language = params["language"] ?: "en"
            val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
            if (!emailRegex.matches(email)) return@post call.respondError("invalid email format")

            val registrationResponse = params["registrationResponse"]
                ?: return@post call.respondError("registrationResponse required")
            
            val roles = params["roles"] ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.map { it.trim() }
                ?.map { UserRole.valueOf(it) }
                ?.toSet()
                ?: setOf(UserRole.ADOPTER)

            val effectiveRoles = if (email.equals(adminEmail, ignoreCase = true)) {
                roles + UserRole.ADMIN
            } else {
                roles
            }

            val result = webAuthnService.verifyAndRegister(email, displayName, effectiveRoles, registrationResponse, language)
            processResult(result)
        }

        post("/register-password") {
            val body = call.receiveText()
            val json = kotlinx.serialization.json.Json.decodeFromString<kotlinx.serialization.json.JsonObject>(body)
            val email = json["email"]?.toString()?.removeSurrounding("\"") ?: return@post call.respondError("email required")
            val displayName = json["displayName"]?.toString()?.removeSurrounding("\"") ?: return@post call.respondError("displayName required")
            val encryptedPassword = json["encryptedPassword"]?.toString()?.removeSurrounding("\"") ?: return@post call.respondError("password required")
            val rolesStr = json["roles"]?.toString()?.removeSurrounding("\"") ?: "ADOPTER"
            
            val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
            if (!emailRegex.matches(email)) return@post call.respondError("invalid email format")

            val roles = rolesStr.split(",")
                .filter { it.isNotBlank() }
                .map { it.trim() }
                .map { UserRole.valueOf(it) }
                .toSet()

            val effectiveRoles = if (email.equals(adminEmail, ignoreCase = true)) {
                roles + UserRole.ADMIN
            } else {
                roles
            }

            val result = webAuthnService.registerWithPassword(email, displayName, effectiveRoles, encryptedPassword)
            if (result != null) {
                call.respond(RegistrationResponse(success = true, message = "Registration successful", emailVerificationSent = result.emailSent))
            } else {
                call.respondError("Registration failed")
            }
        }

        get("/has-passkey") {
            val session = call.sessions.get<SessionUser>()
            if (session == null) {
                call.respond(SuccessWithErrorResponse(success = false, error = "Not authenticated"))
                return@get
            }
            val hasPasskey = webAuthnService.hasPasskey(session.userId)
            call.respond(SuccessWithErrorResponse(success = hasPasskey, error = null))
        }

        post("/registration-options-for-user") {
            val session = call.sessions.get<SessionUser>()
            if (session == null) {
                call.respondError("Not authenticated", 401)
                return@post
            }
            val body = call.receiveText()
            val json = kotlinx.serialization.json.Json.decodeFromString<kotlinx.serialization.json.JsonObject>(body)
            val email = json["email"]?.toString()?.removeSurrounding("\"") ?: session.email
            val displayName = json["displayName"]?.toString()?.removeSurrounding("\"") ?: session.displayName
            
            val options = webAuthnService.generateRegistrationOptionsForUser(session.userId, email, displayName)
            call.respond(options)
        }

        post("/register-passkey") {
            val session = call.sessions.get<SessionUser>()
            if (session == null) {
                call.respondError("Not authenticated", 401)
                return@post
            }
            
            val body = call.receiveText()
            val json = kotlinx.serialization.json.Json.decodeFromString<kotlinx.serialization.json.JsonObject>(body)
            val registrationResponseJson = json["registrationResponse"]?.toString()?.removeSurrounding("\"")
                ?: return@post call.respondError("registrationResponse required")
            
            val result = webAuthnService.registerAdditionalPasskey(session.userId, registrationResponseJson)
            if (result) {
                call.respond(SuccessWithErrorResponse(success = true, error = null))
            } else {
                call.respondError("Failed to register passkey")
            }
        }

        get("/verify-email") {
            val token = call.request.queryParameters["token"]
            if (token.isNullOrBlank()) {
                return@get call.respond(VerificationResponse(success = false, message = "Token is required"))
            }

            if (webAuthnService.verifyToken(token)) {
                call.respond(VerificationResponse(success = true, message = "Email verified successfully. You can now login."))
            } else {
                call.respond(VerificationResponse(success = false, message = "Invalid or expired token"))
            }
        }

        post("/resend-verification") {
            val session = call.sessions.get<SessionUser>()
            if (session == null) {
                val contentType = call.request.contentType().toString()
                if (contentType.contains("application/x-www-form-urlencoded")) {
                    val params = call.receiveParameters()
                    val email = params["email"]
                    if (!email.isNullOrBlank()) {
                        val sent = webAuthnService.resendVerificationEmailByEmail(email)
                        if (sent) {
                            call.respond(VerificationResponse(success = true, message = "Verification email sent"))
                        } else {
                            call.respond(VerificationResponse(success = false, message = "Failed to send verification email"))
                        }
                        return@post
                    }
                }
                return@post call.respondError("Not authenticated", 401)
            }
            
            val sent = webAuthnService.resendVerificationEmail(session.userId)
            if (sent) {
                call.respond(VerificationResponse(success = true, message = "Verification email sent"))
            } else {
                call.respond(VerificationResponse(success = false, message = "Failed to send verification email"))
            }
        }

        get("/assertion-options") {
            val options = webAuthnService.generateAssertionOptions()
            call.respond(options)
        }

        post("/authenticate") {
            val params = call.receiveParameters()
            val credential = params["credential"]
            if (credential.isNullOrBlank()) return@post call.respond(SuccessWithErrorResponse(success = false, error = "No credential"))

            val result = webAuthnService.verifyAndAuthenticate(credential)
            if (result != null) {
                val verifiedResult = validationService.validateVerified(result.userId, result.user.username)
                if (verifiedResult is ServiceResult.Error) {
                    call.respond(SuccessWithErrorResponse(success = false, error = "Please verify your email before logging in", email = verifiedResult.message))
                    return@post
                }

                val bannedResult = validationService.validateNotBanned(result.userId)
                if (bannedResult is ServiceResult.Error) {
                    call.respond(SuccessWithErrorResponse(success = false, error = bannedResult.message, email = result.user.username))
                    return@post
                }

                val user = result.user
                application.log.info("Passkey auth success: userId=${result.userId} username=${user.username}")
                call.sessions.set(
                    SessionUser(result.userId, user.username, user.displayName)
                )
                call.respond(SuccessResponse(success = true))
            } else {
                application.log.warn("Passkey auth failed: invalid credential")
                call.respond(SuccessWithErrorResponse(success = false, error = "Authentication failed"))
            }
        }

        post("/logout") {
            call.sessions.clear<SessionUser>()
            call.respond(SuccessResponse(success = true))
        }

        get("/me") {
            val session = call.sessions.get<SessionUser>()
            application.log.debug("Session = ${session?.userId}, ${session?.email}")
            if (session != null) {
                try {
                    val userResult = validationService.validateUserById(session.userId)
                    when (userResult) {
                        is ServiceResult.Success -> {
                            userAuthenticationSuccess(userResult, this@route, this@get, session, webAuthnService)
                        }
                        is ServiceResult.NotFound -> {
                            application.log.warn("Session exists but user not found for userId: ${session.userId}")
                            call.respond(AuthMeResponse(authenticated = false))
                        }
                        else -> {
                            call.respond(AuthMeResponse(authenticated = false))
                        }
                    }
                } catch (e: Exception) {
                    application.log.error("Exception in /me endpoint: ${e.message}", e)
                    call.respond(AuthMeResponse(authenticated = false))
                }
            } else {
                call.respond(AuthMeResponse(authenticated = false))
            }
        }

        post("/request-magic-link") {
            application.log.info("Received magic link request")

            val body = try {
                call.receive<EncryptedLoginRequest>()
            } catch (e: Exception) {
                application.log.error("Failed to parse request body: ${e.message}")
                return@post call.respondError("Invalid request body", 400)
            }
            
            application.log.info("Processing magic link request")
            val emailResult = validationService.validateAndDecryptEmail(body.encryptedData)
            if (emailResult is ServiceResult.Error) {
                application.log.warn("Email validation/decryption failed: ${emailResult.message}")
                return@post call.respondError(emailResult.message, 400)
            }
            val email = (emailResult as ServiceResult.Success).data
            application.log.info("Processing magic link request for: $email")
            
            val result = webAuthnService.requestMagicLink(email)
            if (result.isFailure) {
                application.log.error("Magic link request failed: ${result.exceptionOrNull()?.message}")
                // Return the specific error message (e.g., "Email not verified. A new verification email has been sent.")
                val errorMessage = result.exceptionOrNull()?.message ?: "Failed to send magic link"
                call.respond(SuccessWithErrorResponse(success = false, error = errorMessage, email = email))
            } else {
                val sent = result.getOrNull() ?: false
                if (sent) {
                    application.log.info("Magic link sent successfully to: $email")
                } else {
                    application.log.warn("Magic link email NOT sent to: $email (SMTP not configured or failed)")
                }
                call.respond(SuccessResponse(success = sent))
            }
        }

        get("/magic-link-login") {
            val token = call.request.queryParameters["token"]
            if (token.isNullOrBlank()) {
                call.respondRedirect("/login?error=invalid_token")
                return@get
            }
            
            val magicLinkResult = webAuthnService.verifyMagicLink(token)
            if (magicLinkResult == null) {
                call.respondRedirect("/login?error=invalid_or_expired")
                return@get
            }
            
            val verifiedResult = validationService.validateVerified(magicLinkResult.userId, magicLinkResult.username)
            if (verifiedResult is ServiceResult.Error) {
                // Check if verification email needs resend (token expired after 24h)
                val latestToken = userRepository.getLatestVerificationToken(magicLinkResult.userId)
                val now = System.currentTimeMillis()
                
                if (latestToken == null || latestToken.expiresAt <= now) {
                    // Token expired or doesn't exist - resend verification email
                    webAuthnService.resendVerificationEmail(magicLinkResult.userId)
                    call.respondRedirect("/login?error=not_verified&email=${magicLinkResult.username}&resent=true")
                } else {
                    call.respondRedirect("/login?error=not_verified&email=${magicLinkResult.username}")
                }
                return@get
            }
            
            val bannedResult = validationService.validateNotBanned(magicLinkResult.userId)
            if (bannedResult is ServiceResult.Error) {
                call.respondRedirect("/login?error=banned")
                return@get
            }
            
            // Now consume the token and set the session
            webAuthnService.consumeMagicLink(token)
            
            application.log.info("Magic link login success: userId=${magicLinkResult.userId} username=${magicLinkResult.username}")
            call.sessions.set(SessionUser(magicLinkResult.userId, magicLinkResult.username, magicLinkResult.displayName))
            
            // Redirect to profile or home page
            call.respondRedirect("/profile")
        }

        post("/login-with-password") {
            val body = try {
                call.receive<PasswordLoginRequest>()
            } catch (e: Exception) {
                return@post call.respondError("Invalid request body", 400)
            }
            
            val userResult = validationService.validateEmailAndUser(body.email)
            if (userResult is ServiceResult.Error) {
                call.respond(SuccessWithErrorResponse(success = false, error = "Invalid credentials"))
                return@post
            }
            val user = (userResult as ServiceResult.Success).data
            
            if (!webAuthnService.verifyPassword(user.id, body.encryptedPassword)) {
                call.respond(SuccessWithErrorResponse(success = false, error = "Invalid credentials"))
                return@post
            }
            
            val verifiedResult = validationService.validateVerified(user.id, body.email)
            if (verifiedResult is ServiceResult.Error) {
                // Check if verification email needs resend
                val latestToken = userRepository.getLatestVerificationToken(user.id)
                val now = System.currentTimeMillis()
                
                val email = body.email
                if (latestToken == null || latestToken.expiresAt <= now) {
                    val resent = webAuthnService.resendVerificationEmail(user.id)
                    if (resent) {
                        call.respond(SuccessWithErrorResponse(
                            success = false,
                            error = "Verification email was expired. A new verification email has been sent.",
                            email = email
                        ))
                    } else {
                        call.respond(SuccessWithErrorResponse(
                            success = false,
                            error = "Unable to send verification email. You may have reached the daily limit (3 emails). Please try again tomorrow.",
                            email = email
                        ))
                    }
                } else {
                    call.respond(SuccessWithErrorResponse(
                        success = false,
                        error = "Please verify your email before logging in",
                        email = email
                    ))
                }
                return@post
            }
            
            val bannedResult = validationService.validateNotBanned(user.id)
            if (bannedResult is ServiceResult.Error) {
                call.respond(SuccessWithErrorResponse(success = false, error = bannedResult.message, email = body.email))
                return@post
            }
            
            application.log.info("Password login success: userId=${user.id} username=${body.email}")
            call.sessions.set(SessionUser(user.id, body.email, user.displayName))
            call.respond(SuccessResponse(success = true))
        }

        post("/forgot-password") {
            val body = try {
                call.receive<EncryptedLoginRequest>()
            } catch (e: Exception) {
                return@post call.respondError("Invalid request body", 400)
            }
            
            val emailResult = validationService.validateAndDecryptEmail(body.encryptedData)
            if (emailResult is ServiceResult.Error) {
                return@post call.respondError(emailResult.message, 400)
            }
            val result = webAuthnService.requestPasswordReset((emailResult as ServiceResult.Success).data)
            if (result.isFailure) {
                call.respond(SuccessWithErrorResponse(success = false, error = result.exceptionOrNull()?.message ?: "Failed to send reset email"))
            } else {
                call.respond(SuccessResponse(success = true))
            }
        }

        post("/reset-password") {
            val token = call.request.queryParameters["token"]
            if (token.isNullOrBlank()) {
                return@post call.respondError("Token is required", 400)
            }
            
            val body = try {
                call.receive<EncryptedLoginRequest>()
            } catch (e: Exception) {
                return@post call.respondError("Invalid request body", 400)
            }
            
            val success = webAuthnService.resetPassword(token, body.encryptedData)
            if (success) {
                call.respond(SuccessResponse(success = true))
            } else {
                call.respond(SuccessWithErrorResponse(success = false, error = "Failed to reset password. Token may be invalid/expired or password doesn't meet requirements (min 8 chars with uppercase, lowercase, number, symbol)."))
            }
        }

        get("/encryption-key") {
            val publicKey = CryptoService.getPublicKey()
            call.respond(mapOf("publicKey" to publicKey))
        }
    }
}

private suspend fun userAuthenticationSuccess(
    userResult: ServiceResult.Success<UserDto>,
    route: Route,
    context: RoutingContext,
    session: SessionUser,
    webAuthnService: WebAuthnService,
) {
    val user = userResult.data
    route.application.log.debug("User = ${user.id}")
    val activeRolesList = user.activeRoles.map { it.name }

    context.call.respond(
        AuthMeResponse(
            authenticated = true,
            id = session.userId,
            email = session.email,
            displayName = session.displayName,
            language = user.language,
            country = user.country,
            activeRoles = activeRolesList,
            lastAcceptedPrivacyPolicy = user.lastAcceptedPrivacyPolicy,
            lastAcceptedTermsAndConditions = user.lastAcceptedTermsAndConditions,
            emailVerified = webAuthnService.isUserVerified(session.userId),
            isBanned = user.isBanned,
            banReason = user.banReason,
            photographerFee = user.photographerFee,
            photographerCurrency = user.photographerCurrency,
            photographerCountry = user.photographerCountry,
            photographerState = user.photographerState
        )
    )
}

private suspend fun RoutingContext.processResult(result: WebAuthnService.RegistrationResult?) {
    if (result != null) {
        if (result.emailSent) {
            call.respond(
                RegistrationResponse(
                    success = true,
                    message = "Registration successful. Please check your email to verify your account.",
                    emailVerificationSent = true
                )
            )
        } else {
            call.respond(
                RegistrationResponse(
                    success = false,
                    message = "Registration successful but failed to send verification email. Please request a new verification link.",
                    emailVerificationSent = false
                )
            )
        }
    } else {
        call.respond(RegistrationResponse(success = false, message = "Registration failed"))
    }
}

private fun getLocalizedError(key: String, language: String): String {
    return when (language.lowercase()) {
        "es" -> when (key) {
            "invalid email format" -> "formato de correo electrónico inválido"
            "email already registered" -> "correo electrónico ya registrado"
            "verification email sent" -> "correo de verificación enviado. Revisa tu bandeja de entrada."
            else -> key
        }
        "fr" -> when (key) {
            "invalid email format" -> "format d'email invalide"
            "email already registered" -> "email déjà enregistré"
            "verification email sent" -> "email de vérification envoyé. Vérifiez votre boîte de réception."
            else -> key
        }
        "pt" -> when (key) {
            "invalid email format" -> "formato de email inválido"
            "email already registered" -> "email já registrado"
            "verification email sent" -> "e-mail de verificação enviado. Verifique sua caixa de entrada."
            else -> key
        }
        "zh" -> when (key) {
            "invalid email format" -> "邮箱格式无效"
            "email already registered" -> "邮箱已被注册"
            "verification email sent" -> "验证邮件已发送。请检查您的收件箱。"
            else -> key
        }
        else -> when (key) {
            "invalid email format" -> "invalid email format"
            "email already registered" -> "email already registered"
            "verification email sent" -> "verification email sent. Check your inbox."
            else -> key
        }
    }
}

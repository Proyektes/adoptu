package com.adoptu.routes

import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.config.AppConfig
import com.adoptu.dto.input.UserDto
import com.adoptu.dto.input.UserRole
import com.adoptu.dto.output.AuthMeResponse
import com.adoptu.dto.output.RegistrationResponse
import com.adoptu.dto.output.SuccessWithErrorResponse
import com.adoptu.dto.output.VerificationResponse
import com.adoptu.services.PasswordService
import com.adoptu.services.ServiceResult
import com.adoptu.services.auth.SessionUser
import com.adoptu.services.auth.VerificationResendOutcome
import com.adoptu.services.auth.WebAuthnService
import com.adoptu.services.crypto.CryptoService
import com.adoptu.services.validation.AuthValidationService
import com.adoptu.web.Deps
import com.adoptu.web.JsonSupport
import com.adoptu.web.SuccessResponse
import com.adoptu.web.clearSession
import com.adoptu.web.getSession
import com.adoptu.web.queryParam
import com.adoptu.web.receiveFormParameters
import com.adoptu.web.receiveJson
import com.adoptu.web.receiveText
import com.adoptu.web.respondError
import com.adoptu.web.respondRedirect
import com.adoptu.web.setSession
import com.fasterxml.jackson.databind.node.ObjectNode
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRules
import io.helidon.webserver.http.ServerResponse
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AdoptU-Auth")

data class EncryptedLoginRequest(val encryptedData: String)
data class PasswordLoginRequest(val email: String, val encryptedPassword: String)

// ADMIN is granted only via the admin.email bootstrap match below - never from client input,
// or any authenticated caller could self-register with "roles=ADMIN" and gain full admin access.
private val SELF_REGISTERABLE_ROLES = UserRole.entries.toSet() - UserRole.ADMIN

private fun parseSelfRegisteredRoles(rolesStr: String?): Set<UserRole> =
    rolesStr?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.mapNotNull { name -> UserRole.entries.find { it.name == name } }
        ?.filter { it in SELF_REGISTERABLE_ROLES }
        ?.toSet()
        ?.ifEmpty { null }
        ?: setOf(UserRole.ADOPTER)

fun HttpRules.authRoutes() {
    val webAuthnService by Deps.inject<WebAuthnService>()
    val validationService by Deps.inject<AuthValidationService>()
    val passwordService by Deps.inject<PasswordService>()
    val config by Deps.inject<AppConfig>()
    val adminEmail = config.propertyOrNull("admin.email")?.getString() ?: "admin@adopt-u.com"
    val userRepository = UserRepository(clock = kotlin.time.Clock.System)

    post("/api/auth/registration-options", Handler { req, res ->
        runBlocking {
            val params = req.receiveFormParameters()
            val email = params["email"] ?: return@runBlocking res.respondError("email required")
            val displayName = params["displayName"] ?: return@runBlocking res.respondError("displayName required")
            val language = params["language"] ?: "en"
            val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
            if (!emailRegex.matches(email)) return@runBlocking res.respondError(getLocalizedError("invalid email format", language))

            val existingUser = validationService.getUserByEmail(email)
            if (existingUser != null) {
                val isVerified = webAuthnService.isUserVerified(existingUser.id)
                if (isVerified) {
                    return@runBlocking res.respondError(getLocalizedError("email already registered", language))
                }
                val outcome = webAuthnService.resendVerificationEmailDetailed(existingUser.id)
                val messageKey = when (outcome) {
                    VerificationResendOutcome.SENT -> "verification email sent"
                    VerificationResendOutcome.RATE_LIMITED -> "verification email limit reached"
                    else -> "verification email send failed"
                }
                return@runBlocking res.respondError(getLocalizedError(messageKey, language))
            }

            val options = webAuthnService.generateRegistrationOptions(email, displayName)
            res.send(options)
        }
    })

    post("/api/auth/register", Handler { req, res ->
        runBlocking {
            val params = req.receiveFormParameters()
            val email = params["email"] ?: return@runBlocking res.respondError("email required")
            val displayName = params["displayName"] ?: return@runBlocking res.respondError("displayName required")
            val language = params["language"] ?: "en"
            val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
            if (!emailRegex.matches(email)) return@runBlocking res.respondError("invalid email format")

            val registrationResponse = params["registrationResponse"]
                ?: return@runBlocking res.respondError("registrationResponse required")

            val roles = parseSelfRegisteredRoles(params["roles"])

            val effectiveRoles = if (email.equals(adminEmail, ignoreCase = true)) {
                roles + UserRole.ADMIN
            } else {
                roles
            }

            val result = webAuthnService.verifyAndRegister(email, displayName, effectiveRoles, registrationResponse, language)
            processResult(res, result)
        }
    })

    post("/api/auth/register-password", Handler { req, res ->
        runBlocking {
            val body = req.receiveText()
            val json = JsonSupport.objectMapper.readTree(body) as ObjectNode
            val email = json.get("email")?.asText() ?: return@runBlocking res.respondError("email required")
            val displayName = json.get("displayName")?.asText() ?: return@runBlocking res.respondError("displayName required")
            val encryptedPassword = json.get("encryptedPassword")?.asText() ?: return@runBlocking res.respondError("password required")
            val rolesStr = json.get("roles")?.asText()

            val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
            if (!emailRegex.matches(email)) return@runBlocking res.respondError("invalid email format")

            val roles = parseSelfRegisteredRoles(rolesStr)

            val effectiveRoles = if (email.equals(adminEmail, ignoreCase = true)) {
                roles + UserRole.ADMIN
            } else {
                roles
            }

            val result = webAuthnService.registerWithPassword(email, displayName, effectiveRoles, encryptedPassword)
            if (result != null) {
                res.send(RegistrationResponse(success = true, message = "Registration successful. Please check your email to verify your account.", emailVerificationSent = result.emailSent))
            } else {
                res.respondError("Registration failed")
            }
        }
    })

    get("/api/auth/has-passkey", Handler { req, res ->
        val session = req.getSession()
        if (session == null) {
            res.send(SuccessWithErrorResponse(success = false, error = "Not authenticated"))
            return@Handler
        }
        val hasPasskey = runBlocking { webAuthnService.hasPasskey(session.userId) }
        res.send(SuccessWithErrorResponse(success = hasPasskey, error = null))
    })

    post("/api/auth/registration-options-for-user", Handler { req, res ->
        val session = req.getSession()
        if (session == null) {
            res.respondError("Not authenticated", 401)
            return@Handler
        }
        runBlocking {
            val body = req.receiveText()
            val json = JsonSupport.objectMapper.readTree(body) as ObjectNode
            val email = json.get("email")?.asText() ?: session.email
            val displayName = json.get("displayName")?.asText() ?: session.displayName

            val options = webAuthnService.generateRegistrationOptionsForUser(session.userId, email, displayName)
            res.send(options)
        }
    })

    post("/api/auth/register-passkey", Handler { req, res ->
        val session = req.getSession()
        if (session == null) {
            res.respondError("Not authenticated", 401)
            return@Handler
        }
        runBlocking {
            val body = req.receiveText()
            val json = JsonSupport.objectMapper.readTree(body) as ObjectNode
            val registrationResponseJson = json.get("registrationResponse")?.asText()
                ?: return@runBlocking res.respondError("registrationResponse required")

            val result = webAuthnService.registerAdditionalPasskey(session.userId, registrationResponseJson)
            if (result) {
                res.send(SuccessWithErrorResponse(success = true, error = null))
            } else {
                res.respondError("Failed to register passkey")
            }
        }
    })

    get("/api/auth/verify-email", Handler { req, res ->
        val token = req.queryParam("token")
        if (token.isNullOrBlank()) {
            res.send(VerificationResponse(success = false, message = "Token is required"))
            return@Handler
        }

        if (runBlocking { webAuthnService.verifyToken(token) }) {
            res.send(VerificationResponse(success = true, message = "Email verified successfully. You can now login."))
        } else {
            res.send(VerificationResponse(success = false, message = "Invalid or expired token"))
        }
    })

    post("/api/auth/resend-verification", Handler { req, res ->
        val session = req.getSession()
        if (session == null) {
            val contentType = req.headers().contentType().map { it.text() }.orElse("")
            if (contentType.contains("application/x-www-form-urlencoded")) {
                val params = req.receiveFormParameters()
                val email = params["email"]
                if (!email.isNullOrBlank()) {
                    val sent = runBlocking { webAuthnService.resendVerificationEmailByEmail(email) }
                    if (sent) {
                        res.send(VerificationResponse(success = true, message = "Verification email sent"))
                    } else {
                        res.send(VerificationResponse(success = false, message = "Failed to send verification email"))
                    }
                    return@Handler
                }
            }
            res.respondError("Not authenticated", 401)
            return@Handler
        }

        val sent = runBlocking { webAuthnService.resendVerificationEmail(session.userId) }
        if (sent) {
            res.send(VerificationResponse(success = true, message = "Verification email sent"))
        } else {
            res.send(VerificationResponse(success = false, message = "Failed to send verification email"))
        }
    })

    get("/api/auth/assertion-options", Handler { _, res ->
        val options = runBlocking { webAuthnService.generateAssertionOptions() }
        res.send(options)
    })

    post("/api/auth/authenticate", Handler { req, res ->
        runBlocking {
            val params = req.receiveFormParameters()
            val credential = params["credential"]
            if (credential.isNullOrBlank()) {
                res.send(SuccessWithErrorResponse(success = false, error = "No credential"))
                return@runBlocking
            }

            val result = webAuthnService.verifyAndAuthenticate(credential)
            if (result != null) {
                val verifiedResult = validationService.validateVerified(result.userId, result.user.username)
                if (verifiedResult is ServiceResult.Error) {
                    res.send(SuccessWithErrorResponse(success = false, error = "Please verify your email before logging in", email = verifiedResult.message))
                    return@runBlocking
                }

                val bannedResult = validationService.validateNotBanned(result.userId)
                if (bannedResult is ServiceResult.Error) {
                    res.send(SuccessWithErrorResponse(success = false, error = bannedResult.message, email = result.user.username))
                    return@runBlocking
                }

                val user = result.user
                logger.info("Passkey auth success: userId=${result.userId} username=${user.username}")
                res.setSession(SessionUser(result.userId, user.username, user.displayName))
                res.send(SuccessResponse(success = true))
            } else {
                logger.warn("Passkey auth failed: invalid credential")
                res.send(SuccessWithErrorResponse(success = false, error = "Authentication failed"))
            }
        }
    })

    post("/api/auth/logout", Handler { _, res ->
        res.clearSession()
        res.send(SuccessResponse(success = true))
    })

    get("/api/auth/me", Handler { req, res ->
        val session = req.getSession()
        logger.debug("Session = ${session?.userId}, ${session?.email}")
        if (session != null) {
            try {
                val userResult = runBlocking { validationService.validateUserById(session.userId) }
                when (userResult) {
                    is ServiceResult.Success -> userAuthenticationSuccess(userResult, res, session, webAuthnService)
                    is ServiceResult.NotFound -> {
                        logger.warn("Session exists but user not found for userId: ${session.userId}")
                        res.send(AuthMeResponse(authenticated = false))
                    }
                    else -> res.send(AuthMeResponse(authenticated = false))
                }
            } catch (e: Exception) {
                logger.error("Exception in /me endpoint: ${e.message}", e)
                res.send(AuthMeResponse(authenticated = false))
            }
        } else {
            res.send(AuthMeResponse(authenticated = false))
        }
    })

    post("/api/auth/request-magic-link", Handler { req, res ->
        logger.info("Received magic link request")
        runBlocking {
            val body = try {
                req.receiveJson<EncryptedLoginRequest>()
            } catch (e: Exception) {
                logger.error("Failed to parse request body: ${e.message}")
                return@runBlocking res.respondError("Invalid request body", 400)
            }

            logger.info("Processing magic link request")
            val emailResult = validationService.validateAndDecryptEmail(body.encryptedData)
            if (emailResult is ServiceResult.Error) {
                logger.warn("Email validation/decryption failed: ${emailResult.message}")
                return@runBlocking res.respondError(emailResult.message, 400)
            }
            val email = (emailResult as ServiceResult.Success).data
            logger.info("Processing magic link request for: $email")

            val result = webAuthnService.requestMagicLink(email)
            if (result.isFailure) {
                logger.error("Magic link request failed: ${result.exceptionOrNull()?.message}")
                val errorMessage = result.exceptionOrNull()?.message ?: "Failed to send magic link"
                res.send(SuccessWithErrorResponse(success = false, error = errorMessage, email = email))
            } else {
                val sent = result.getOrNull() ?: false
                if (sent) {
                    logger.info("Magic link sent successfully to: $email")
                } else {
                    logger.warn("Magic link email NOT sent to: $email (SMTP not configured or failed)")
                }
                res.send(SuccessResponse(success = sent))
            }
        }
    })

    get("/api/auth/magic-link-login", Handler { req, res ->
        val token = req.queryParam("token")
        if (token.isNullOrBlank()) {
            res.respondRedirect("/login?error=invalid_token")
            return@Handler
        }

        runBlocking {
            val magicLinkResult = webAuthnService.verifyMagicLink(token)
            if (magicLinkResult == null) {
                res.respondRedirect("/login?error=invalid_or_expired")
                return@runBlocking
            }

            val verifiedResult = validationService.validateVerified(magicLinkResult.userId, magicLinkResult.username)
            if (verifiedResult is ServiceResult.Error) {
                val latestToken = userRepository.getLatestVerificationToken(magicLinkResult.userId)
                val now = System.currentTimeMillis()

                if (latestToken == null || latestToken.expiresAt <= now) {
                    val resent = webAuthnService.resendVerificationEmail(magicLinkResult.userId)
                    res.respondRedirect("/login?error=not_verified&email=${magicLinkResult.username}&resent=$resent")
                } else {
                    res.respondRedirect("/login?error=not_verified&email=${magicLinkResult.username}")
                }
                return@runBlocking
            }

            val bannedResult = validationService.validateNotBanned(magicLinkResult.userId)
            if (bannedResult is ServiceResult.Error) {
                res.respondRedirect("/login?error=banned")
                return@runBlocking
            }

            webAuthnService.consumeMagicLink(token)

            logger.info("Magic link login success: userId=${magicLinkResult.userId} username=${magicLinkResult.username}")
            res.setSession(SessionUser(magicLinkResult.userId, magicLinkResult.username, magicLinkResult.displayName))
            res.respondRedirect("/profile")
        }
    })

    post("/api/auth/login-with-password", Handler { req, res ->
        runBlocking {
            val body = try {
                req.receiveJson<PasswordLoginRequest>()
            } catch (e: Exception) {
                return@runBlocking res.respondError("Invalid request body", 400)
            }

            if (passwordService.isLoginRateLimited(body.email)) {
                res.send(SuccessWithErrorResponse(success = false, error = "Too many failed login attempts. Please try again in 15 minutes."))
                return@runBlocking
            }

            val userResult = validationService.validateEmailAndUser(body.email)
            if (userResult is ServiceResult.Error) {
                passwordService.recordLoginAttempt(body.email, successful = false)
                res.send(SuccessWithErrorResponse(success = false, error = "Invalid credentials"))
                return@runBlocking
            }
            val user = (userResult as ServiceResult.Success).data

            if (!webAuthnService.verifyPassword(user.id, body.encryptedPassword)) {
                passwordService.recordLoginAttempt(body.email, successful = false)
                res.send(SuccessWithErrorResponse(success = false, error = "Invalid credentials"))
                return@runBlocking
            }
            passwordService.recordLoginAttempt(body.email, successful = true)

            val verifiedResult = validationService.validateVerified(user.id, body.email)
            if (verifiedResult is ServiceResult.Error) {
                val latestToken = userRepository.getLatestVerificationToken(user.id)
                val now = System.currentTimeMillis()

                val email = body.email
                if (latestToken == null || latestToken.expiresAt <= now) {
                    val resent = webAuthnService.resendVerificationEmail(user.id)
                    if (resent) {
                        res.send(SuccessWithErrorResponse(success = false, error = "Verification email was expired. A new verification email has been sent.", email = email))
                    } else {
                        res.send(SuccessWithErrorResponse(success = false, error = "Unable to send verification email. You may have reached the daily limit (3 emails). Please try again tomorrow.", email = email))
                    }
                } else {
                    res.send(SuccessWithErrorResponse(success = false, error = "Please verify your email before logging in", email = email))
                }
                return@runBlocking
            }

            val bannedResult = validationService.validateNotBanned(user.id)
            if (bannedResult is ServiceResult.Error) {
                res.send(SuccessWithErrorResponse(success = false, error = bannedResult.message, email = body.email))
                return@runBlocking
            }

            logger.info("Password login success: userId=${user.id} username=${body.email}")
            res.setSession(SessionUser(user.id, body.email, user.displayName))
            res.send(SuccessResponse(success = true))
        }
    })

    post("/api/auth/forgot-password", Handler { req, res ->
        runBlocking {
            val body = try {
                req.receiveJson<EncryptedLoginRequest>()
            } catch (e: Exception) {
                return@runBlocking res.respondError("Invalid request body", 400)
            }

            val emailResult = validationService.validateAndDecryptEmail(body.encryptedData)
            if (emailResult is ServiceResult.Error) {
                return@runBlocking res.respondError(emailResult.message, 400)
            }
            val result = webAuthnService.requestPasswordReset((emailResult as ServiceResult.Success).data)
            if (result.isFailure) {
                res.send(SuccessWithErrorResponse(success = false, error = result.exceptionOrNull()?.message ?: "Failed to send reset email"))
            } else {
                res.send(SuccessResponse(success = true))
            }
        }
    })

    post("/api/auth/reset-password", Handler { req, res ->
        val token = req.queryParam("token")
        if (token.isNullOrBlank()) {
            res.respondError("Token is required", 400)
            return@Handler
        }

        runBlocking {
            val body = try {
                req.receiveJson<EncryptedLoginRequest>()
            } catch (e: Exception) {
                return@runBlocking res.respondError("Invalid request body", 400)
            }

            val success = webAuthnService.resetPassword(token, body.encryptedData)
            if (success) {
                res.send(SuccessResponse(success = true))
            } else {
                res.send(SuccessWithErrorResponse(success = false, error = "Failed to reset password. Token may be invalid/expired or password doesn't meet requirements (min 8 chars with uppercase, lowercase, number, symbol)."))
            }
        }
    })

    get("/api/auth/encryption-key", Handler { _, res ->
        val publicKey = CryptoService.getPublicKey()
        res.send(mapOf("publicKey" to publicKey))
    })
}

private fun userAuthenticationSuccess(
    userResult: ServiceResult.Success<UserDto>,
    res: ServerResponse,
    session: SessionUser,
    webAuthnService: WebAuthnService,
) {
    val user = userResult.data
    logger.debug("User = ${user.id}")
    val activeRolesList = user.activeRoles.map { it.name }

    res.send(
        AuthMeResponse(
            authenticated = true,
            id = session.userId,
            email = user.email ?: user.username,
            displayName = user.displayName,
            language = user.language,
            country = user.country,
            activeRoles = activeRolesList,
            lastAcceptedPrivacyPolicy = user.lastAcceptedPrivacyPolicy,
            lastAcceptedTermsAndConditions = user.lastAcceptedTermsAndConditions,
            emailVerified = runBlocking { webAuthnService.isUserVerified(session.userId) },
            isBanned = user.isBanned,
            banReason = user.banReason,
            photographerFee = user.photographerFee,
            photographerCurrency = user.photographerCurrency,
            photographerCountry = user.photographerCountry,
            photographerState = user.photographerState
        )
    )
}

private fun processResult(res: ServerResponse, result: WebAuthnService.RegistrationResult?) {
    if (result != null) {
        if (result.emailSent) {
            res.send(
                RegistrationResponse(
                    success = true,
                    message = "Registration successful. Please check your email to verify your account.",
                    emailVerificationSent = true
                )
            )
        } else {
            res.send(
                RegistrationResponse(
                    success = false,
                    message = "Registration successful but failed to send verification email. Please request a new verification link.",
                    emailVerificationSent = false
                )
            )
        }
    } else {
        res.send(RegistrationResponse(success = false, message = "Registration failed"))
    }
}

private fun getLocalizedError(key: String, language: String): String {
    return when (language.lowercase()) {
        "es" -> when (key) {
            "invalid email format" -> "formato de correo electrónico inválido"
            "email already registered" -> "correo electrónico ya registrado"
            "verification email sent" -> "correo de verificación enviado. Revisa tu bandeja de entrada."
            "verification email limit reached" -> "No se pudo enviar el correo de verificación. Es posible que hayas alcanzado el límite diario (3 correos). Inténtalo de nuevo mañana."
            "verification email send failed" -> "No se pudo enviar el correo de verificación debido a un error del servidor. Por favor, inténtalo de nuevo en unos minutos."
            else -> key
        }
        "fr" -> when (key) {
            "invalid email format" -> "format d'email invalide"
            "email already registered" -> "email déjà enregistré"
            "verification email sent" -> "email de vérification envoyé. Vérifiez votre boîte de réception."
            "verification email limit reached" -> "Impossible d'envoyer l'email de vérification. Vous avez peut-être atteint la limite quotidienne (3 emails). Veuillez réessayer demain."
            "verification email send failed" -> "Impossible d'envoyer l'email de vérification en raison d'une erreur du serveur. Veuillez réessayer dans quelques minutes."
            else -> key
        }
        "pt" -> when (key) {
            "invalid email format" -> "formato de email inválido"
            "email already registered" -> "email já registrado"
            "verification email sent" -> "e-mail de verificação enviado. Verifique sua caixa de entrada."
            "verification email limit reached" -> "Não foi possível enviar o e-mail de verificação. Você pode ter atingido o limite diário (3 e-mails). Tente novamente amanhã."
            "verification email send failed" -> "Não foi possível enviar o e-mail de verificação devido a um erro do servidor. Tente novamente em alguns minutos."
            else -> key
        }
        "zh" -> when (key) {
            "invalid email format" -> "邮箱格式无效"
            "email already registered" -> "邮箱已被注册"
            "verification email sent" -> "验证邮件已发送。请检查您的收件箱。"
            "verification email limit reached" -> "无法发送验证邮件。您可能已达到每日限额(3封邮件)。请明天再试。"
            "verification email send failed" -> "由于服务器错误,验证邮件发送失败。请几分钟后重试。"
            else -> key
        }
        else -> when (key) {
            "invalid email format" -> "invalid email format"
            "email already registered" -> "email already registered"
            "verification email sent" -> "verification email sent. Check your inbox."
            "verification email limit reached" -> "Unable to send verification email. You may have reached the daily limit (3 emails). Please try again tomorrow."
            "verification email send failed" -> "Unable to send verification email due to a server error. Please try again in a few minutes."
            else -> key
        }
    }
}

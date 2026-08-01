package com.adoptu.routes

import com.adoptu.adapters.authkit.AdoptuPasskeyCredentialRepositoryAdapter
import com.adoptu.adapters.authkit.AdoptuUserRepositoryAdapter
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.config.AppConfig
import com.adoptu.dto.input.UserDto
import com.adoptu.dto.input.UserRole
import com.adoptu.dto.output.AuthMeResponse
import com.adoptu.dto.output.RegistrationResponse
import com.adoptu.dto.output.SuccessWithErrorResponse
import com.adoptu.dto.output.VerificationResponse
import com.adoptu.services.EmailVerificationService
import com.adoptu.services.PasswordService
import com.adoptu.services.ServiceResult
import com.adoptu.services.UserService
import com.adoptu.services.crypto.CryptoService
import com.adoptu.web.Deps
import com.adoptu.web.SuccessResponse
import com.adoptu.web.queryParam
import com.adoptu.web.receiveFormParameters
import com.adoptu.web.receiveJson
import com.adoptu.web.receiveText
import com.adoptu.web.respondError
import com.adoptu.web.respondRedirect
import com.universaliun.auth.backend.domain.exception.EmailAlreadyRegisteredException
import com.universaliun.auth.backend.domain.exception.InvalidCredentialsException
import com.universaliun.auth.backend.domain.exception.InvalidMagicLinkTokenException
import com.universaliun.auth.backend.domain.exception.InvalidPasskeyCeremonyException
import com.universaliun.auth.backend.domain.exception.InvalidPasswordResetTokenException
import com.universaliun.auth.backend.domain.exception.PasskeyLoginFailedException
import com.universaliun.auth.backend.domain.exception.PasskeyRegistrationFailedException
import com.universaliun.auth.backend.domain.exception.WeakPasswordException
import com.universaliun.auth.backend.domain.port.`in`.FinishPasskeyLoginUseCase
import com.universaliun.auth.backend.domain.port.`in`.FinishPasskeyRegistrationUseCase
import com.universaliun.auth.backend.domain.port.`in`.FinishPasskeySignupUseCase
import com.universaliun.auth.backend.domain.port.`in`.ForgotPasswordUseCase
import com.universaliun.auth.backend.domain.port.`in`.LoginUseCase
import com.universaliun.auth.backend.domain.port.`in`.LogoutUseCase
import com.universaliun.auth.backend.domain.port.`in`.ResetPasswordUseCase
import com.universaliun.auth.backend.domain.port.`in`.StartPasskeyLoginUseCase
import com.universaliun.auth.backend.domain.port.`in`.StartPasskeyRegistrationUseCase
import com.universaliun.auth.backend.domain.port.`in`.StartPasskeySignupUseCase
import com.universaliun.auth.backend.infrastructure.currentPrincipal
import com.universaliun.auth.common.identity.AuthUserId
import io.helidon.http.HeaderNames
import io.helidon.http.SetCookie
import io.helidon.webserver.http.Handler
import io.helidon.webserver.http.HttpRules
import io.helidon.webserver.http.ServerRequest
import io.helidon.webserver.http.ServerResponse
import kotlinx.coroutines.runBlocking
import org.koin.core.component.get
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

private val logger = LoggerFactory.getLogger("AdoptU-Auth")

// Cookie names for the tokens AuthKit issues -- distinct from the retired native "user_session"
// cookie (still cleared on logout below, as hygiene for anyone with a pre-cutover session).
private const val ACCESS_COOKIE = "adoptu_access_token"
private const val REFRESH_COOKIE = "adoptu_refresh_token"

data class EncryptedLoginRequest(val encryptedData: String)
data class PasswordLoginRequest(val email: String, val encryptedPassword: String)
private data class PasskeyStartRequest(val requestId: String, val optionsJson: String)
private data class PasskeyFinishRequest(val requestId: String, val credentialJson: String)

// ADMIN is granted only via the admin.email bootstrap match below - never from client input,
// or any authenticated caller could self-register with "roles=ADMIN" and gain full admin access.
private val SELF_REGISTERABLE_ROLES = UserRole.entries.toSet() - UserRole.ADMIN

// Mirrors WebAuthnService.ROLES_REQUIRING_VERIFICATION_BEFORE_ACTIVATION -- a brand new
// registration is never verified yet, so none of these can be granted at signup time. Selecting
// one of these roles at registration just records intent; the user activates it from /profile
// (through the already-gated POST /api/users/{role}-profile) after verifying.
private val ROLES_REQUIRING_VERIFICATION_BEFORE_ACTIVATION = setOf(
    UserRole.PHOTOGRAPHER, UserRole.TEMPORAL_HOME, UserRole.SHELTER, UserRole.STERILIZATION_SERVICE, UserRole.RESCUER
)

private fun parseSelfRegisteredRoles(rolesStr: String?): Set<UserRole> =
    rolesStr?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.mapNotNull { name -> UserRole.entries.find { it.name == name } }
        ?.filter { it in SELF_REGISTERABLE_ROLES }
        ?.toSet()
        ?.ifEmpty { null }
        ?: setOf(UserRole.ADOPTER)

private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

fun HttpRules.authRoutes() {
    val validationService by Deps.inject<com.adoptu.services.validation.AuthValidationService>()
    val passwordService by Deps.inject<PasswordService>()
    val emailVerificationService by Deps.inject<EmailVerificationService>()
    val userService by Deps.inject<UserService>()
    val config by Deps.inject<AppConfig>()
    val kitUserRepository by Deps.inject<AdoptuUserRepositoryAdapter>()
    val kitPasskeyRepository by Deps.inject<AdoptuPasskeyCredentialRepositoryAdapter>()
    val startPasskeySignup by Deps.inject<StartPasskeySignupUseCase>()
    val finishPasskeySignup by Deps.inject<FinishPasskeySignupUseCase>()
    val startPasskeyRegistration by Deps.inject<StartPasskeyRegistrationUseCase>()
    val finishPasskeyRegistration by Deps.inject<FinishPasskeyRegistrationUseCase>()
    val startPasskeyLogin by Deps.inject<StartPasskeyLoginUseCase>()
    val finishPasskeyLogin by Deps.inject<FinishPasskeyLoginUseCase>()
    val loginUseCase by Deps.inject<LoginUseCase>()
    val logoutUseCase by Deps.inject<LogoutUseCase>()
    val forgotPasswordUseCase by Deps.inject<ForgotPasswordUseCase>()
    val resetPasswordUseCase by Deps.inject<ResetPasswordUseCase>()
    val adminEmail = config.propertyOrNull("admin.email")?.getString() ?: "admin@adopt-u.com"
    val cookieSecure = config.propertyOrNull("session.cookieSecure")?.getString()?.toBoolean() ?: true
    val userRepository = UserRepository(clock = kotlin.time.Clock.System)

    fun ServerResponse.setAuthCookies(accessToken: String, refreshToken: String) {
        headers().addCookie(
            SetCookie.builder(ACCESS_COOKIE, accessToken).secure(cookieSecure).httpOnly(true)
                .sameSite(SetCookie.SameSite.LAX).path("/").maxAge(Duration.ofMinutes(15)).build()
        )
        headers().addCookie(
            SetCookie.builder(REFRESH_COOKIE, refreshToken).secure(cookieSecure).httpOnly(true)
                .sameSite(SetCookie.SameSite.LAX).path("/").maxAge(Duration.ofDays(30)).build()
        )
    }

    fun ServerResponse.clearAuthCookies() {
        headers().clearCookie(ACCESS_COOKIE)
        headers().clearCookie(REFRESH_COOKIE)
        headers().clearCookie("user_session")
    }

    fun ServerRequest.cookieValue(name: String): String? {
        val cookieHeader = headers().first(HeaderNames.COOKIE).orElse(null) ?: return null
        return cookieHeader.split(";").map { it.trim() }
            .firstOrNull { it.startsWith("$name=") }?.substringAfter("=")
    }

    // Applies a freshly-registered user's role selection -- the same native logic
    // WebAuthnService.registerWithPassword/verifyAndRegister used, now driven by AuthKit's
    // created-user id instead of the retired native registration path's own. Roles requiring
    // verification are recorded as pending (activated later via the already-gated
    // POST /api/users/{role}-profile); the rest are granted immediately.
    fun applyRoleSelection(userId: Int, effectiveRoles: Set<UserRole>) {
        runBlocking {
            val immediate = effectiveRoles - ROLES_REQUIRING_VERIFICATION_BEFORE_ACTIVATION
            val pending = effectiveRoles intersect ROLES_REQUIRING_VERIFICATION_BEFORE_ACTIVATION
            if (immediate.isNotEmpty()) userRepository.addActiveRoles(userId, immediate)
            if (pending.isNotEmpty()) userRepository.addPendingRoleActivations(userId, pending)
        }
    }

    // Generates+persists a fresh activation token on Users.resetTokenHash (AuthKit's shared
    // reset-token slot) and sends the same localized template EmailVerificationService already
    // owns -- for a signup created through AuthKit's gated flow, whose token doesn't live in the
    // old EmailVerificationTokens table. Rate-limited via the same policy as the native resend.
    fun resendActivationEmail(userId: Int, email: String, displayName: String, language: String): Result<Boolean> = runBlocking {
        emailVerificationService.generateAndSendActivationEmail(userId, email, displayName, language) { rawToken, expiresAtEpochMs ->
            kitUserRepository.updateResetToken(AuthUserId(userId.toString()), sha256Hex(rawToken), Instant.ofEpochMilli(expiresAtEpochMs))
        }
    }

    // AuthKit's RegisterUseCase/FinishPasskeySignupUseCase already generate and persist (hashed)
    // the activation token themselves -- see their Result.activationToken doc comments -- the host
    // just builds the link and sends it, same "library never emails" contract already used for
    // forgot-password/request-magic-link below. Unlike resendActivationEmail above, this must NOT
    // mint a fresh token (that would orphan the one AuthKit's own use case just persisted).
    fun sendActivationEmail(activationToken: String?, email: String?, user: com.adoptu.dto.input.UserDto?): Boolean {
        if (activationToken == null || email == null || user == null) return false
        val verificationUrl = "${config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:8080"}/verify?token=$activationToken"
        val (subject, bodyText) = emailVerificationService.getLocalizedContent(user.language, user.displayName, verificationUrl)
        return runBlocking { Deps.get<com.adoptu.ports.NotificationPort>().sendEmail(email, subject, bodyText) }
    }

    post("/api/auth/registration-options", Handler { req, res ->
        val params = req.receiveFormParameters()
        val email = params["email"] ?: return@Handler res.respondError("email required")
        val displayName = params["displayName"] ?: return@Handler res.respondError("displayName required")
        val language = params["language"] ?: "en"
        val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        if (!emailRegex.matches(email)) return@Handler res.respondError(getLocalizedError("invalid email format", language))

        val existingUser = runBlocking { validationService.getUserByEmail(email) }
        if (existingUser != null) {
            val isVerified = runBlocking { userService.isUserVerified(existingUser.id) }
            if (isVerified) {
                return@Handler res.respondError(getLocalizedError("email already registered", language))
            }
            val outcome = resendActivationEmail(existingUser.id, email, existingUser.displayName, language)
            val messageKey = when {
                outcome.isSuccess && outcome.getOrDefault(false) -> "verification email sent"
                outcome.isFailure -> "verification email limit reached"
                else -> "verification email send failed"
            }
            return@Handler res.respondError(getLocalizedError(messageKey, language))
        }

        try {
            val result = startPasskeySignup.start(StartPasskeySignupUseCase.Command(email, displayName))
            res.send(PasskeyStartRequest(result.requestId, result.optionsJson))
        } catch (e: EmailAlreadyRegisteredException) {
            res.respondError(getLocalizedError("email already registered", language))
        }
    })

    post("/api/auth/register", Handler { req, res ->
        val body = req.receiveJson<PasskeyFinishRequestWithProfile>()

        val roles = parseSelfRegisteredRoles(null) // roles selection isn't sent on this legacy path today; defaults to ADOPTER
        try {
            val result = finishPasskeySignup.finish(FinishPasskeySignupUseCase.Command(body.requestId, body.credentialJson))
            if (result.requiresEmailVerification) {
                val created = runBlocking { validationService.getUserByEmail(result.email!!) }
                if (created != null) {
                    val effectiveRoles = if (result.email.equals(adminEmail, ignoreCase = true)) roles + UserRole.ADMIN else roles
                    applyRoleSelection(created.id, effectiveRoles)
                }
                val sent = sendActivationEmail(result.activationToken, result.email, created)
                if (sent) {
                    res.send(RegistrationResponse(success = true, message = "Registration successful. Please check your email to verify your account.", emailVerificationSent = true))
                } else {
                    res.send(RegistrationResponse(success = false, message = "Registration successful but failed to send verification email. Please request a new verification link.", emailVerificationSent = false))
                }
            } else {
                res.send(RegistrationResponse(success = false, message = "Registration successful but failed to send verification email. Please request a new verification link.", emailVerificationSent = false))
            }
        } catch (e: InvalidPasskeyCeremonyException) {
            res.respondError("Registration failed: invalid or expired request")
        } catch (e: PasskeyRegistrationFailedException) {
            res.respondError("Registration failed: ${e.message}")
        } catch (e: EmailAlreadyRegisteredException) {
            res.respondError("Registration failed: email already registered")
        }
    })

    post("/api/auth/register-password", Handler { req, res ->
        val body = req.receiveText()
        val json = com.adoptu.web.JsonSupport.objectMapper.readTree(body) as com.fasterxml.jackson.databind.node.ObjectNode
        val email = json.get("email")?.asText() ?: return@Handler res.respondError("email required")
        val displayName = json.get("displayName")?.asText() ?: return@Handler res.respondError("displayName required")
        val encryptedPassword = json.get("encryptedPassword")?.asText() ?: return@Handler res.respondError("password required")
        val rolesStr = json.get("roles")?.asText()

        val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        if (!emailRegex.matches(email)) return@Handler res.respondError("invalid email format")

        val decryptedPassword = CryptoService.decrypt(encryptedPassword)
        if (decryptedPassword == null) return@Handler res.respondError("Registration failed")

        val roles = parseSelfRegisteredRoles(rolesStr)
        val effectiveRoles = if (email.equals(adminEmail, ignoreCase = true)) roles + UserRole.ADMIN else roles

        try {
            val result = com.universaliun.auth.backend.domain.port.`in`.RegisterUseCase.Command(email, decryptedPassword, displayName)
                .let { Deps.get<com.universaliun.auth.backend.domain.port.`in`.RegisterUseCase>().register(it) }
            if (result.requiresEmailVerification) {
                val created = runBlocking { validationService.getUserByEmail(email) }
                if (created != null) applyRoleSelection(created.id, effectiveRoles)
                val sent = sendActivationEmail(result.activationToken, email, created)
                if (sent) {
                    res.send(RegistrationResponse(success = true, message = "Registration successful. Please check your email to verify your account.", emailVerificationSent = true))
                } else {
                    res.send(RegistrationResponse(success = false, message = "Registration successful but failed to send verification email. Please request a new verification link.", emailVerificationSent = false))
                }
            } else {
                res.send(RegistrationResponse(success = false, message = "Registration successful but failed to send verification email. Please request a new verification link.", emailVerificationSent = false))
            }
        } catch (e: EmailAlreadyRegisteredException) {
            res.respondError("Registration failed")
        } catch (e: WeakPasswordException) {
            res.respondError(e.message ?: "Password does not meet requirements")
        }
    })

    get("/api/auth/has-passkey", Handler { req, res ->
        val principal = req.currentPrincipal()
        if (principal == null) {
            res.send(SuccessWithErrorResponse(success = false, error = "Not authenticated"))
            return@Handler
        }
        val hasPasskey = kitPasskeyRepository.findByUserId(principal.userId).isNotEmpty()
        res.send(SuccessWithErrorResponse(success = hasPasskey, error = null))
    })

    post("/api/auth/registration-options-for-user", Handler { req, res ->
        val principal = req.currentPrincipal()
        if (principal == null) {
            res.respondError("Not authenticated", 401)
            return@Handler
        }
        val result = startPasskeyRegistration.start(StartPasskeyRegistrationUseCase.Command(principal.userId))
        res.send(PasskeyStartRequest(result.requestId, result.optionsJson))
    })

    post("/api/auth/register-passkey", Handler { req, res ->
        val principal = req.currentPrincipal()
        if (principal == null) {
            res.respondError("Not authenticated", 401)
            return@Handler
        }
        val body = req.receiveJson<PasskeyFinishRequest>()
        try {
            finishPasskeyRegistration.finish(FinishPasskeyRegistrationUseCase.Command(principal.userId, body.requestId, body.credentialJson))
            res.send(SuccessWithErrorResponse(success = true, error = null))
        } catch (e: InvalidPasskeyCeremonyException) {
            res.respondError("Failed to register passkey: invalid or expired request")
        } catch (e: PasskeyRegistrationFailedException) {
            res.respondError("Failed to register passkey: ${e.message}")
        }
    })

    get("/api/auth/verify-email", Handler { req, res ->
        val token = req.queryParam("token")
        if (token.isNullOrBlank()) {
            res.send(VerificationResponse(success = false, message = "Token is required"))
            return@Handler
        }

        // AuthKit-gated signups (passkey and password) store their activation token on
        // Users.resetTokenHash, not the old EmailVerificationTokens table -- verify against that
        // shared slot directly via the bridge repository rather than EmailVerificationService's
        // own verifyToken(), which only ever looks in the old table.
        val user = kitUserRepository.findByResetTokenHash(sha256Hex(token))
        if (user == null) {
            res.send(VerificationResponse(success = false, message = "Invalid or expired token"))
            return@Handler
        }
        val activated = kitUserRepository.save(user.copy(enabled = true, emailVerified = true))
        kitUserRepository.updateResetToken(user.id, null, null)
        if (activated.emailVerified) runBlocking { userService.activatePendingRoles(user.id.value.toInt()) }
        res.send(VerificationResponse(success = activated.emailVerified, message = "Email verified successfully. You can now login."))
    })

    post("/api/auth/resend-verification", Handler { req, res ->
        val principal = req.currentPrincipal()
        if (principal == null) {
            val contentType = req.headers().contentType().map { it.text() }.orElse("")
            if (contentType.contains("application/x-www-form-urlencoded")) {
                val params = req.receiveFormParameters()
                val email = params["email"]
                if (!email.isNullOrBlank()) {
                    val user = runBlocking { validationService.getUserByEmail(email) }
                    if (user == null) {
                        res.send(VerificationResponse(success = false, message = "Failed to send verification email"))
                        return@Handler
                    }
                    if (runBlocking { userService.isUserVerified(user.id) }) {
                        res.send(VerificationResponse(success = false, message = "Failed to send verification email"))
                        return@Handler
                    }
                    val outcome = resendActivationEmail(user.id, email, user.displayName, user.language)
                    val sent = outcome.isSuccess && outcome.getOrDefault(false)
                    res.send(VerificationResponse(success = sent, message = if (sent) "Verification email sent" else "Failed to send verification email"))
                    return@Handler
                }
            }
            res.respondError("Not authenticated", 401)
            return@Handler
        }

        val userId = principal.userId.value.toInt()
        val user = runBlocking { userService.getById(userId) }
        if (user == null) {
            res.send(VerificationResponse(success = false, message = "Failed to send verification email"))
            return@Handler
        }
        if (runBlocking { userService.isUserVerified(userId) }) {
            res.send(VerificationResponse(success = false, message = "Failed to send verification email"))
            return@Handler
        }
        val outcome = resendActivationEmail(userId, principal.email, user.displayName, user.language)
        val sent = outcome.isSuccess && outcome.getOrDefault(false)
        res.send(VerificationResponse(success = sent, message = if (sent) "Verification email sent" else "Failed to send verification email"))
    })

    get("/api/auth/assertion-options", Handler { _, res ->
        // Adopt-u never prompted for a username before showing the passkey prompt (fully
        // discoverable/usernameless login) -- email = null now produces a real usernameless
        // ceremony instead of AuthKit wrapping "" in its Email value class and throwing.
        val result = startPasskeyLogin.start(StartPasskeyLoginUseCase.Command(email = null))
        res.send(PasskeyStartRequest(result.requestId, result.optionsJson))
    })

    post("/api/auth/authenticate", Handler { req, res ->
        val body = req.receiveJson<PasskeyFinishRequest>()
        try {
            val result = finishPasskeyLogin.finish(FinishPasskeyLoginUseCase.Command(body.requestId, body.credentialJson))
            val userId = extractUserId(result.tokens.accessToken).toIntOrNull()
            if (userId == null) {
                res.send(SuccessWithErrorResponse(success = false, error = "Authentication failed"))
                return@Handler
            }
            // AuthKit's FinishPasskeyLoginService issues tokens for any credential that passes the
            // Yubico ceremony check, with no verified/banned gating (unlike LoginUseCase, which
            // AuthKit gates on AuthUser.enabled) -- restore the native pre-session checks here so a
            // still-unverified or banned user can't get a live passkey session. The refresh token
            // AuthKit already persisted for this attempt is simply left unredeemed; it's never
            // handed to the caller, so it expires unused per the normal refresh-token TTL.
            val user = runBlocking { userService.getById(userId) }
            val verified = runBlocking { userService.isUserVerified(userId) }
            if (!verified) {
                res.send(SuccessWithErrorResponse(success = false, error = "Please verify your email before logging in", email = user?.email ?: user?.username))
                return@Handler
            }
            val banned = runBlocking { userService.isBanned(userId) }
            if (banned) {
                res.send(SuccessWithErrorResponse(success = false, error = "Your account has been suspended. Reason: ${user?.banReason ?: "Contact administrator"}", email = user?.email ?: user?.username))
                return@Handler
            }
            logger.info("Passkey auth success: userId=$userId")
            res.setAuthCookies(result.tokens.accessToken, result.tokens.refreshToken)
            res.send(SuccessResponse(success = true))
        } catch (e: InvalidPasskeyCeremonyException) {
            res.send(SuccessWithErrorResponse(success = false, error = "Authentication failed"))
        } catch (e: PasskeyLoginFailedException) {
            res.send(SuccessWithErrorResponse(success = false, error = "Authentication failed"))
        } catch (e: InvalidCredentialsException) {
            res.send(SuccessWithErrorResponse(success = false, error = "Authentication failed"))
        }
    })

    post("/api/auth/logout", Handler { req, res ->
        val accessToken = req.cookieValue(ACCESS_COOKIE) ?: ""
        val refreshToken = req.cookieValue(REFRESH_COOKIE) ?: ""
        if (accessToken.isNotBlank() || refreshToken.isNotBlank()) {
            runCatching { logoutUseCase.logout(LogoutUseCase.Command(accessToken, refreshToken)) }
        }
        res.clearAuthCookies()
        res.send(SuccessResponse(success = true))
    })

    get("/api/auth/me", Handler { req, res ->
        val principal = req.currentPrincipal()
        logger.debug("Principal = ${principal?.userId}")
        if (principal != null) {
            try {
                val userId = principal.userId.value.toInt()
                val userResult = runBlocking { validationService.validateUserById(userId) }
                when (userResult) {
                    is ServiceResult.Success -> userAuthenticationSuccess(userResult, res, userId, userService)
                    is ServiceResult.NotFound -> {
                        logger.warn("Principal exists but user not found for userId: $userId")
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
        val body = try {
            req.receiveJson<EncryptedLoginRequest>()
        } catch (e: Exception) {
            logger.error("Failed to parse request body: ${e.message}")
            return@Handler res.respondError("Invalid request body", 400)
        }

        val emailResult = validationService.validateAndDecryptEmail(body.encryptedData)
        if (emailResult is ServiceResult.Error) {
            logger.warn("Email validation/decryption failed: ${emailResult.message}")
            return@Handler res.respondError(emailResult.message, 400)
        }
        val email = (emailResult as ServiceResult.Success).data
        logger.info("Processing magic link request for: $email")

        // Preserves the native "auto-resend activation instead of a magic link, for an
        // unverified account" nuance -- AuthKit's own RequestMagicLinkUseCase just treats
        // unverified (enabled=false) the same as unknown-email (silent no-op), which would lose
        // this UX without this pre-check.
        val existingUser = runBlocking { validationService.getUserByEmail(email) }
        if (existingUser != null && !runBlocking { userService.isUserVerified(existingUser.id) }) {
            val language = existingUser.language
            val outcome = resendActivationEmail(existingUser.id, email, existingUser.displayName, language)
            return@Handler if (outcome.isSuccess && outcome.getOrDefault(false)) {
                res.send(SuccessWithErrorResponse(success = false, error = "Email not verified. A new verification email has been sent to your inbox.", email = email))
            } else {
                res.send(SuccessWithErrorResponse(success = false, error = "Failed to send verification email. Please try again.", email = email))
            }
        }

        val result = Deps.get<com.universaliun.auth.backend.domain.port.`in`.RequestMagicLinkUseCase>()
            .request(com.universaliun.auth.backend.domain.port.`in`.RequestMagicLinkUseCase.Command(email))
        if (result != null) {
            val loginUrl = "${config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:8080"}/api/auth/magic-link-login?token=${result.rawToken}"
            val language = existingUser?.language ?: "en"
            val (subject, bodyText) = magicLinkEmailContent(language, existingUser?.displayName ?: "", loginUrl)
            val sent = runBlocking { Deps.get<com.adoptu.ports.NotificationPort>().sendEmail(email, subject, bodyText) }
            res.send(SuccessResponse(success = sent))
        } else {
            // Unknown/disabled account -- report success regardless, to avoid email enumeration
            // (same anti-enumeration shape the native implementation already had).
            res.send(SuccessResponse(success = true))
        }
    })

    get("/api/auth/magic-link-login", Handler { req, res ->
        val token = req.queryParam("token")
        if (token.isNullOrBlank()) {
            res.respondRedirect("/login?error=invalid_token")
            return@Handler
        }

        // AuthKit's ConsumeMagicLinkUseCase only looks up a token by hash among *enabled* users
        // (see ConsumeMagicLinkService: `.takeIf { it.enabled }`) and throws the same
        // InvalidMagicLinkTokenException whether the token is genuinely invalid/expired, or valid
        // but belongs to a not-yet-verified/banned user -- collapsing three native error states
        // into one. Peek the token's owner directly via the shared resetTokenHash slot first (same
        // lookup /verify-email already uses) so unverified/banned users still get their original,
        // distinct redirects instead of a generic "invalid or expired" -- and so the token isn't
        // burned by AuthKit's own gate before we've had a chance to decide.
        val tokenUser = kitUserRepository.findByResetTokenHash(sha256Hex(token))
        if (tokenUser == null) {
            res.respondRedirect("/login?error=invalid_or_expired")
            return@Handler
        }

        val tokenUserId = tokenUser.id.value.toInt()
        if (!tokenUser.emailVerified) {
            val user = runBlocking { userService.getById(tokenUserId) }
            val resent = user?.let {
                val outcome = resendActivationEmail(tokenUserId, tokenUser.email.value, it.displayName, it.language)
                outcome.isSuccess && outcome.getOrDefault(false)
            } ?: false
            res.respondRedirect("/login?error=not_verified&email=${tokenUser.email.value}&resent=$resent")
            return@Handler
        }

        val banned = runBlocking { userService.isBanned(tokenUserId) }
        if (banned) {
            res.respondRedirect("/login?error=banned")
            return@Handler
        }

        try {
            val result = Deps.get<com.universaliun.auth.backend.domain.port.`in`.ConsumeMagicLinkUseCase>()
                .consume(com.universaliun.auth.backend.domain.port.`in`.ConsumeMagicLinkUseCase.Command(token))
            logger.info("Magic link login success: userId=$tokenUserId")
            res.setAuthCookies(result.tokens.accessToken, result.tokens.refreshToken)
            res.respondRedirect("/profile")
        } catch (e: InvalidMagicLinkTokenException) {
            res.respondRedirect("/login?error=invalid_or_expired")
        }
    })

    post("/api/auth/login-with-password", Handler { req, res ->
        val body = try {
            req.receiveJson<PasswordLoginRequest>()
        } catch (e: Exception) {
            return@Handler res.respondError("Invalid request body", 400)
        }

        if (runBlocking { passwordService.isLoginRateLimited(body.email) }) {
            res.send(SuccessWithErrorResponse(success = false, error = "Too many failed login attempts. Please try again in 15 minutes."))
            return@Handler
        }

        val decryptedPassword = CryptoService.decrypt(body.encryptedPassword)
        // Same "email:password" unwrapping the frontend's encryption scheme uses (see
        // PasswordService.extractPassword) -- the frontend encrypts "email:password" together.
        val plainPassword = decryptedPassword?.let { it.substringAfter(':', it) }

        if (plainPassword == null) {
            runBlocking { passwordService.recordLoginAttempt(body.email, successful = false) }
            res.send(SuccessWithErrorResponse(success = false, error = "Invalid credentials"))
            return@Handler
        }

        try {
            val result = loginUseCase.login(LoginUseCase.Command(body.email, plainPassword))
            runBlocking { passwordService.recordLoginAttempt(body.email, successful = true) }
            val userId = extractUserId(result.tokens.accessToken)
            logger.info("Password login success: userId=$userId username=${body.email}")
            res.setAuthCookies(result.tokens.accessToken, result.tokens.refreshToken)
            res.send(SuccessResponse(success = true))
        } catch (e: InvalidCredentialsException) {
            runBlocking { passwordService.recordLoginAttempt(body.email, successful = false) }
            // Preserve the native "tell the user why" behavior for the two most common
            // InvalidCredentialsException causes (unverified / banned) by re-checking directly --
            // AuthKit's own exception carries no structured reason, only a message.
            val user = runBlocking { validationService.getUserByEmail(body.email) }
            if (user != null) {
                val verified = runBlocking { userService.isUserVerified(user.id) }
                if (!verified) {
                    val resent = resendActivationEmail(user.id, body.email, user.displayName, user.language)
                    val message = if (resent.isSuccess && resent.getOrDefault(false))
                        "Verification email was expired. A new verification email has been sent."
                    else "Please verify your email before logging in"
                    res.send(SuccessWithErrorResponse(success = false, error = message, email = body.email))
                    return@Handler
                }
                val banned = runBlocking { userService.isBanned(user.id) }
                if (banned) {
                    res.send(SuccessWithErrorResponse(success = false, error = "Your account has been suspended. Reason: ${user.banReason ?: "Contact administrator"}", email = body.email))
                    return@Handler
                }
            }
            res.send(SuccessWithErrorResponse(success = false, error = "Invalid credentials"))
        }
    })

    post("/api/auth/forgot-password", Handler { req, res ->
        val body = try {
            req.receiveJson<EncryptedLoginRequest>()
        } catch (e: Exception) {
            return@Handler res.respondError("Invalid request body", 400)
        }

        val emailResult = validationService.validateAndDecryptEmail(body.encryptedData)
        if (emailResult is ServiceResult.Error) {
            return@Handler res.respondError(emailResult.message, 400)
        }
        val email = (emailResult as ServiceResult.Success).data
        val result = forgotPasswordUseCase.request(ForgotPasswordUseCase.Command(email))
        if (result != null) {
            val resetUrl = "${config.propertyOrNull("baseUrl")?.getString() ?: "http://localhost:8080"}/reset-password?token=${result.rawToken}"
            val user = runBlocking { validationService.getUserByEmail(email) }
            val (subject, bodyText) = passwordResetEmailContent(user?.language ?: "en", user?.displayName ?: "", resetUrl)
            val sent = runBlocking { Deps.get<com.adoptu.ports.NotificationPort>().sendEmail(email, subject, bodyText) }
            res.send(SuccessResponse(success = sent))
        } else {
            // Unknown/disabled account -- report success regardless (anti-enumeration).
            res.send(SuccessResponse(success = true))
        }
    })

    post("/api/auth/reset-password", Handler { req, res ->
        val token = req.queryParam("token")
        if (token.isNullOrBlank()) {
            res.respondError("Token is required", 400)
            return@Handler
        }

        val body = try {
            req.receiveJson<EncryptedLoginRequest>()
        } catch (e: Exception) {
            return@Handler res.respondError("Invalid request body", 400)
        }

        val newPassword = CryptoService.decrypt(body.encryptedData)
        if (newPassword == null) {
            res.send(SuccessWithErrorResponse(success = false, error = "Failed to reset password. Token may be invalid/expired or password doesn't meet requirements (min 8 chars with uppercase, lowercase, number, symbol)."))
            return@Handler
        }

        try {
            val result = resetPasswordUseCase.reset(ResetPasswordUseCase.Command(token, newPassword))
            // Native behavior didn't auto-login after reset -- discard the issued tokens and
            // require a fresh login, matching that.
            logger.info("Password reset success")
            res.send(SuccessResponse(success = true))
        } catch (e: InvalidPasswordResetTokenException) {
            res.send(SuccessWithErrorResponse(success = false, error = "Failed to reset password. Token may be invalid/expired or password doesn't meet requirements (min 8 chars with uppercase, lowercase, number, symbol)."))
        } catch (e: WeakPasswordException) {
            res.send(SuccessWithErrorResponse(success = false, error = e.message ?: "Password does not meet requirements"))
        }
    })

    get("/api/auth/encryption-key", Handler { _, res ->
        val publicKey = CryptoService.getPublicKey()
        res.send(mapOf("publicKey" to publicKey))
    })
}

/** Decodes the userId (JWT "sub" claim) out of an issued access token, for logging only -- avoids
 * a second round-trip through the token service just to log who logged in. */
private fun extractUserId(accessToken: String): String {
    return try {
        val payload = accessToken.split(".")[1]
        val decoded = String(Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '=')))
        Regex("\"sub\":\"([^\"]+)\"").find(decoded)?.groupValues?.get(1) ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }
}

private data class PasskeyFinishRequestWithProfile(val requestId: String, val credentialJson: String, val email: String? = null, val displayName: String? = null)

private fun userAuthenticationSuccess(
    userResult: ServiceResult.Success<UserDto>,
    res: ServerResponse,
    userId: Int,
    userService: UserService,
) {
    val user = userResult.data
    logger.debug("User = ${user.id}")
    val activeRolesList = user.activeRoles.map { it.name }

    res.send(
        AuthMeResponse(
            authenticated = true,
            id = userId,
            email = user.email ?: user.username,
            displayName = user.displayName,
            language = user.language,
            country = user.country,
            activeRoles = activeRolesList,
            lastAcceptedPrivacyPolicy = user.lastAcceptedPrivacyPolicy,
            lastAcceptedTermsAndConditions = user.lastAcceptedTermsAndConditions,
            emailVerified = runBlocking { userService.isUserVerified(userId) },
            isBanned = user.isBanned,
            banReason = user.banReason,
            photographerFee = user.photographerFee,
            photographerCurrency = user.photographerCurrency,
            photographerCountry = user.photographerCountry,
            photographerState = user.photographerState
        )
    )
}

private fun magicLinkEmailContent(language: String, displayName: String, loginUrl: String): Pair<String, String> = when (language.lowercase()) {
    "es" -> "Enlace de inicio de sesión - Adopt-U" to "Hola $displayName,\n\nHaz clic en el siguiente enlace para iniciar sesión en tu cuenta de Adopt-U:\n$loginUrl\n\nEste enlace expirará en 5 minutos.\n\nSi no solicitaste este enlace, puedes ignorarlo de manera segura."
    "fr" -> "Lien de connexion - Adopt-U" to "Bonjour $displayName,\n\nCliquez sur le lien suivant pour vous connecter à votre compte Adopt-U:\n$loginUrl\n\nCe lien expirera dans 5 minutes.\n\nSi vous n'avez pas demandé ce lien, vous pouvez l'ignorer en toute sécurité."
    "pt" -> "Link de login - Adopt-U" to "Olá $displayName,\n\nClique no link abaixo para fazer login na sua conta do Adopt-U:\n$loginUrl\n\nEste link expirará em 5 minutos.\n\nSe você não solicitou este link, pode ignorá-lo com segurança."
    "zh" -> "登录链接 - Adopt-U" to "您好 $displayName,\n\n点击以下链接登录您的Adopt-U账户:\n$loginUrl\n\n此链接将在5分钟后过期。\n\n如果您没有请求此链接，可以安全地忽略它。"
    else -> "Login link - Adopt-U" to "Hello $displayName,\n\nClick the link below to sign in to your Adopt-U account:\n$loginUrl\n\nThis link will expire in 5 minutes.\n\nIf you didn't request this link, you can safely ignore it."
}

private fun passwordResetEmailContent(language: String, displayName: String, resetUrl: String): Pair<String, String> = when (language.lowercase()) {
    "es" -> "Restablecer contraseña - Adopt-U" to "Hola $displayName,\n\nHemos recibido una solicitud para restablecer la contraseña de tu cuenta en Adopt-U.\n\nHaz clic en el siguiente enlace para restablecer tu contraseña:\n$resetUrl\n\nEste enlace expirará en 15 minutos.\n\nSi no solicitaste este cambio, puedes ignorar este correo de manera segura."
    "fr" -> "Réinitialiser le mot de passe - Adopt-U" to "Bonjour $displayName,\n\nNous avons reçu une demande de réinitialisation du mot de passe de votre compte Adopt-U.\n\nCliquez sur le lien suivant pour réinitialiser votre mot de passe:\n$resetUrl\n\nCe lien expirera dans 15 minutes.\n\nSi vous n'avez pas demandé cette modification, vous pouvez ignorer cet email en toute sécurité."
    "pt" -> "Redefinir senha - Adopt-U" to "Olá $displayName,\n\nRecebemos uma solicitação para redefinir a senha da sua conta no Adopt-U.\n\nClique no link abaixo para redefinir sua senha:\n$resetUrl\n\nEste link expirará em 15 minutos.\n\nSe você não solicitou esta alteração, pode ignorar este e-mail com segurança."
    "zh" -> "重置密码 - Adopt-U" to "您好 $displayName,\n\n我们收到了您Adopt-U账户的密码重置请求。\n\n点击以下链接重置您的密码:\n$resetUrl\n\n此链接将在15分钟后过期。\n\n如果您没有请求此更改，可以安全地忽略此电子邮件。"
    else -> "Reset your password - Adopt-U" to "Hello $displayName,\n\nWe received a request to reset the password for your Adopt-U account.\n\nClick the link below to reset your password:\n$resetUrl\n\nThis link will expire in 15 minutes.\n\nIf you didn't request this change, you can safely ignore this email."
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

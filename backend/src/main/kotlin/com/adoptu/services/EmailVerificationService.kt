package com.adoptu.services

import com.adoptu.ports.NotificationPort
import com.adoptu.ports.UserRepositoryPort
import com.universaliun.ratelimit.common.RateLimitPolicy
import com.universaliun.ratelimit.common.RateLimiter
import java.security.SecureRandom
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class EmailVerificationService(
    private val userRepository: UserRepositoryPort,
    private val notificationPort: NotificationPort,
    private val clock: Clock,
    private val baseUrl: String = "http://localhost:8080",
    private val rateLimiter: RateLimiter
) {
    private val secureRandom = SecureRandom()
    private val tokenExpirationMs = 24 * 60 * 60 * 1000L
    private val maxVerificationEmailsPerDay = 3

    // RateLimitKit consolidation: this is now the single source of truth for the daily count
    // (previously a separate EmailVerificationAttempts row-count query, kept in sync by hand).
    private val resendPolicy = RateLimitPolicy(window = 24.hours, maxEventsPerWindow = maxVerificationEmailsPerDay)
    private fun resendLimitKey(userId: Int) = userId.toString()

    companion object {
        const val ERROR_RATE_LIMIT_EXCEEDED = "Maximum verification emails (3) reached for today. Please try again tomorrow."
        // Non-private so tests can seed RateLimitStateTable directly with the exact same key this
        // service uses, instead of duplicating the raw string (see AuthRoutesE2ETest).
        const val LIMIT_KIND = "email_verification_resend"
    }

    // internal, not private: reused by AuthRoutes.kt's resendActivationEmail() for AuthKit-gated
    // signups, which store their activation token on Users.resetTokenHash (via
    // AdoptuUserRepositoryAdapter) instead of the EmailVerificationTokens table this class's own
    // generateAndSendVerificationEmail()/verifyToken() use -- same subject/body templates either
    // way, only the token persistence mechanism differs.
    internal fun getLocalizedContent(language: String, displayName: String, verificationUrl: String): Pair<String, String> {
        return when (language.lowercase()) {
            "es" -> "Verifica tu correo electrónico - Adopt-U" to """
                Hola,
                
                ¡Gracias por registrarte en Adopt-U!
                
                Por favor verifica tu dirección de correo electrónico haciendo clic en el enlace de abajo:
                $verificationUrl
                
                Este enlace expirará en 24 horas.
                
                Si no creaste una cuenta, por favor ignora este correo.
            """.trimIndent()
            
            "fr" -> "Vérifiez votre email - Adopt-U" to """
                Bonjour,
                
                Merci de vous être inscrit sur Adopt-U !
                
                Veuillez vérifier votre adresse email en cliquant sur le lien ci-dessous:
                $verificationUrl
                
                Ce lien expirera dans 24 heures.
                
                Si vous n'avez pas créé de compte, veuillez ignorer cet email.
            """.trimIndent()
            
            "de" -> "Bestätigen Sie Ihre E-Mail - Adopt-U" to """
                Hallo,
                
                Vielen Dank für Ihre Registrierung bei Adopt-U!
                
                Bitte bestätigen Sie Ihre E-Mail-Adresse, indem Sie auf den untenstehenden Link klicken:
                $verificationUrl
                
                Dieser Link läuft in 24 Stunden ab.
                
                Wenn Sie kein Konto erstellt haben, ignorieren Sie bitte diese E-Mail.
            """.trimIndent()
            
            "it" -> "Verifica la tua email - Adopt-U" to """
                Ciao,
                
                Grazie per esserti registrato su Adopt-U!
                
                Per favore verifica il tuo indirizzo email cliccando sul link qui sotto:
                $verificationUrl
                
                Questo link scadrà tra 24 ore.
                
                Se non hai creato un account, per favore ignora questa email.
            """.trimIndent()
            
            "pt" -> "Verifique seu email - Adopt-U" to """
                Olá,
                
                Obrigado por se registrar no Adopt-U!
                
                Por favor, verifique seu endereço de email clicando no link abaixo:
                $verificationUrl
                
                Este link expirará em 24 horas.
                
                Se você não criou uma conta, por favor ignore este email.
            """.trimIndent()
            
            else -> "Verify your email - Adopt-U" to """
                Hello $displayName,
                
                Thank you for registering with Adopt-U!
                
                Please verify your email address by clicking the link below:
                $verificationUrl
                
                This link will expire in 24 hours.
                
                If you did not create an account, please ignore this email.
            """.trimIndent()
        }
    }

    suspend fun generateAndSendVerificationEmail(
        userId: Int,
        email: String,
        displayName: String,
        language: String = "en"
    ): Result<Boolean> {
        if (!rateLimiter.verify(resendLimitKey(userId), LIMIT_KIND, resendPolicy)) {
            return Result.failure(RateLimitExceededException(ERROR_RATE_LIMIT_EXCEEDED))
        }

        val token = generateToken()
        val expiresAt = clock.now().toEpochMilliseconds() + tokenExpirationMs

        val tokenCreated = userRepository.createEmailVerificationToken(userId, token, expiresAt)
        if (!tokenCreated) return Result.failure(Exception("Failed to create verification token"))

        val verificationUrl = "$baseUrl/verify?token=$token"

        val (subject, body) = getLocalizedContent(language, displayName, verificationUrl)

        // Slot is already consumed by the rateLimiter.verify() call above, even if the send below
        // fails -- a behavior change from the previous "only count it if the email actually sent"
        // logic, accepted as part of consolidating onto one shared counter (see this class's own
        // git history for the previous per-table implementation if this ever needs revisiting).
        val sent = notificationPort.sendEmail(email, subject, body)

        return Result.success(sent)
    }

    /**
     * Same rate limit + localized template as [generateAndSendVerificationEmail], for a signup
     * whose activation token lives on `Users.resetTokenHash` (AuthKit's gated-register/passkey-
     * signup flows, via [com.adoptu.adapters.authkit.AdoptuUserRepositoryAdapter]) instead of the
     * EmailVerificationTokens table this class otherwise owns. [persistToken] does the actual
     * hash+store — kept as a caller-supplied step rather than a hard AuthKit dependency here, so
     * this service doesn't need to know about AuthKit's port types.
     */
    suspend fun generateAndSendActivationEmail(
        userId: Int,
        email: String,
        displayName: String,
        language: String = "en",
        persistToken: (rawToken: String, expiresAtEpochMs: Long) -> Unit,
    ): Result<Boolean> {
        if (!rateLimiter.verify(resendLimitKey(userId), LIMIT_KIND, resendPolicy)) {
            return Result.failure(RateLimitExceededException(ERROR_RATE_LIMIT_EXCEEDED))
        }

        val token = generateToken()
        val expiresAt = clock.now().toEpochMilliseconds() + tokenExpirationMs
        persistToken(token, expiresAt)

        val verificationUrl = "$baseUrl/verify?token=$token"
        val (subject, body) = getLocalizedContent(language, displayName, verificationUrl)
        val sent = notificationPort.sendEmail(email, subject, body)
        return Result.success(sent)
    }

    suspend fun verifyToken(token: String): Boolean {
        val userId = userRepository.verifyToken(token) ?: return false
        val updated = userRepository.setEmailVerified(userId, true)
        if (updated) {
            userRepository.deleteVerificationTokens(userId)
        }
        return updated
    }

    suspend fun verifyTokenAndGetLanguage(token: String): Pair<Boolean, String> {
        val userId = userRepository.verifyToken(token) ?: return false to "en"
        val user = userRepository.getById(userId)
        val language = user?.language ?: "en"
        val updated = userRepository.setEmailVerified(userId, true)
        if (updated) {
            userRepository.deleteVerificationTokens(userId)
        }
        return updated to language
    }

    suspend fun isUserVerified(userId: Int): Boolean {
        return userRepository.isEmailVerified(userId)
    }

    suspend fun resendVerificationEmail(userId: Int, email: String, displayName: String, language: String = "en"): Result<Boolean> {
        userRepository.deleteVerificationTokens(userId)
        return generateAndSendVerificationEmail(userId, email, displayName, language)
    }

    suspend fun canSendVerificationEmail(userId: Int): Boolean {
        return rateLimiter.wouldAllow(resendLimitKey(userId), LIMIT_KIND, resendPolicy)
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

class RateLimitExceededException(message: String) : Exception(message)

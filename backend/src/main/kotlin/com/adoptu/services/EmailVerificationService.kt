package com.adoptu.services

import com.adoptu.ports.NotificationPort
import com.adoptu.ports.UserRepositoryPort
import java.security.SecureRandom
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class EmailVerificationService(
    private val userRepository: UserRepositoryPort,
    private val notificationPort: NotificationPort,
    private val clock: Clock,
    private val baseUrl: String = "http://localhost:8080"
) {
    private val secureRandom = SecureRandom()
    private val tokenExpirationMs = 24 * 60 * 60 * 1000L
    private val maxVerificationEmailsPerDay = 3

    companion object {
        const val ERROR_RATE_LIMIT_EXCEEDED = "Maximum verification emails (3) reached for today. Please try again tomorrow."
    }

    private fun getLocalizedContent(language: String, displayName: String, verificationUrl: String): Pair<String, String> {
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
        if (userRepository.getVerificationAttemptsToday(userId) >= maxVerificationEmailsPerDay) {
            return Result.failure(RateLimitExceededException(ERROR_RATE_LIMIT_EXCEEDED))
        }

        val token = generateToken()
        val expiresAt = clock.now().toEpochMilliseconds() + tokenExpirationMs
        
        val tokenCreated = userRepository.createEmailVerificationToken(userId, token, expiresAt)
        if (!tokenCreated) return Result.failure(Exception("Failed to create verification token"))
        
        val verificationUrl = "$baseUrl/verify?token=$token"
        
        val (subject, body) = getLocalizedContent(language, displayName, verificationUrl)

        val sent = notificationPort.sendEmail(email, subject, body)
        if (sent) {
            userRepository.recordVerificationAttempt(userId)
        }

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
        return userRepository.getVerificationAttemptsToday(userId) < maxVerificationEmailsPerDay
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

class RateLimitExceededException(message: String) : Exception(message)

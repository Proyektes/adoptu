package com.adoptu.services

import com.adoptu.adapters.db.EmailChangeTokens
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.dbDispatcher
import com.adoptu.ports.NotificationPort
import com.adoptu.ports.UserRepositoryPort
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class EmailChangeService(
    private val userRepository: UserRepositoryPort,
    private val notificationPort: NotificationPort,
    private val clock: Clock,
    private val baseUrl: String = "http://localhost:8080"
) {
    private val secureRandom = SecureRandom()
    private val emailChangeExpirationMs = 60 * 60 * 1000L

    suspend fun requestEmailChange(userId: Int, newEmail: String, language: String = "en"): Result<Boolean> {
        val user = userRepository.getById(userId) ?: return Result.failure(Exception("User not found"))
        
        val existingWithNewEmail = userRepository.getByEmail(newEmail)
        if (existingWithNewEmail != null && existingWithNewEmail.id != userId) {
            return Result.failure(Exception("Email is already in use"))
        }

        if (newEmail.equals(user.username, ignoreCase = true)) {
            return Result.failure(Exception("New email is the same as current email"))
        }

        withContext(dbDispatcher) {
            transaction {
                EmailChangeTokens.deleteWhere { EmailChangeTokens.userId eq userId }
            }
        }

        val token = generateToken()
        val expiresAt = clock.now().toEpochMilliseconds() + emailChangeExpirationMs

        withContext(dbDispatcher) {
            transaction {
                try {
                    EmailChangeTokens.insert {
                        it[EmailChangeTokens.userId] = userId
                        it[EmailChangeTokens.newEmail] = newEmail
                        it[EmailChangeTokens.token] = token
                        it[EmailChangeTokens.expiresAt] = expiresAt
                        it[EmailChangeTokens.createdAt] = clock.now().toEpochMilliseconds()
                    }
                } catch (e: Exception) {
                    return@transaction
                }
            }
        }

        val changeUrl = "$baseUrl/verify-email-change?token=$token"
        val (subject, body) = getLocalizedEmailChangeContent(language, user.displayName, changeUrl, newEmail)

        val sent = notificationPort.sendEmail(newEmail, subject, body)

        val oldEmailSubject = "Email change requested - Adopt-U"
        val oldEmailBody = """
            Hello ${user.displayName},
            
            A request was made to change your email address to: $newEmail
            
            If this was you, please verify the new email address by clicking the link in the email sent to $newEmail.
            
            If you did not request this change, please contact support immediately.
            
            This change will not take effect until the new email is verified.
        """.trimIndent()
        notificationPort.sendEmail(user.username, oldEmailSubject, oldEmailBody)

        return Result.success(sent)
    }

    suspend fun verifyEmailChange(token: String): Boolean = withContext(dbDispatcher) {
        transaction {
            val now = clock.now().toEpochMilliseconds()
            val tokenRow = EmailChangeTokens
                .selectAll()
                .where { EmailChangeTokens.token eq token }
                .firstOrNull()

            if (tokenRow == null || tokenRow[EmailChangeTokens.expiresAt] <= now) {
                return@transaction false
            }

            val userId = tokenRow[EmailChangeTokens.userId]
            val newEmail = tokenRow[EmailChangeTokens.newEmail]

            val updated = Users.update({ Users.id eq userId }) {
                it[Users.username] = newEmail
            }

            if (updated > 0) {
                EmailChangeTokens.deleteWhere { EmailChangeTokens.userId eq userId }
                true
            } else {
                false
            }
        }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun getLocalizedEmailChangeContent(language: String, displayName: String, changeUrl: String, newEmail: String): Pair<String, String> {
        return when (language.lowercase()) {
            "es" -> "Verificar nuevo correo electrónico - Adopt-U" to """
                Hola $displayName,
                
                Por favor verifica tu nueva dirección de correo electrónico haciendo clic en el siguiente enlace:
                $changeUrl
                
                Este enlace expirará en 1 hora.
                
                Si no solicitaste este cambio, puedes ignorar este correo de manera segura.
            """.trimIndent()
            
            "fr" -> "Vérifier la nouvelle adresse email - Adopt-U" to """
                Bonjour $displayName,
                
                Veuillez vérifier votre nouvelle adresse email en cliquant sur le lien suivant:
                $changeUrl
                
                Ce lien expirera dans 1 heure.
                
                Si vous n'avez pas demandé ce changement, vous pouvez ignorer cet email en toute sécurité.
            """.trimIndent()
            
            "pt" -> "Verificar novo email - Adopt-U" to """
                Olá $displayName,
                
                Por favor, verifique seu novo endereço de email clicando no link abaixo:
                $changeUrl
                
                Este link expirará em 1 hora.
                
                Se você não solicitou esta alteração, pode ignorar este e-mail com segurança.
            """.trimIndent()
            
            "zh" -> "验证新电子邮件 - Adopt-U" to """
                您好 $displayName,
                
                请点击以下链接验证您的新电子邮件地址:
                $changeUrl
                
                此链接将在1小时后过期。
                
                如果您没有请求此更改，可以安全地忽略此电子邮件。
            """.trimIndent()
            
            else -> "Verify your new email - Adopt-U" to """
                Hello $displayName,
                
                Please verify your new email address by clicking the link below:
                $changeUrl
                
                This link will expire in 1 hour.
                
                If you didn't request this change, you can safely ignore this email.
            """.trimIndent()
        }
    }
}

package com.adoptu.services

import com.adoptu.adapters.db.LoginAttempts
import com.adoptu.adapters.db.PasswordResetTokens
import com.adoptu.adapters.db.UserPasswords
import com.adoptu.adapters.db.dbDispatcher
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.services.crypto.CryptoService
import com.password4j.Argon2Function
import com.password4j.Password
import com.password4j.types.Argon2
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
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
class PasswordService(
    private val userRepository: UserRepositoryPort,
    private val notificationPort: com.adoptu.ports.NotificationPort,
    private val clock: Clock,
    private val baseUrl: String
) {
    private val secureRandom = SecureRandom()
    private val passwordResetExpirationMs = 15 * 60 * 1000L
    private val maxResetEmailsPerDay = 3
    private val maxFailedLoginAttempts = 5
    private val loginLockoutWindowMs = 15 * 60 * 1000L

    // DB-backed (not in-memory) so the lockout is shared across every ECS task, not just
    // whichever instance happened to handle a given request.
    suspend fun isLoginRateLimited(email: String): Boolean = withContext(dbDispatcher) {
        transaction {
            val since = clock.now().toEpochMilliseconds() - loginLockoutWindowMs
            LoginAttempts.selectAll()
                .where {
                    (LoginAttempts.email eq email.lowercase()) and
                        (LoginAttempts.successful eq false) and
                        (LoginAttempts.attemptedAt greaterEq since)
                }
                .count() >= maxFailedLoginAttempts
        }
    }

    suspend fun recordLoginAttempt(email: String, successful: Boolean) {
        withContext(dbDispatcher) {
            transaction {
                LoginAttempts.insert {
                    it[LoginAttempts.email] = email.lowercase()
                    it[LoginAttempts.successful] = successful
                    it[LoginAttempts.attemptedAt] = clock.now().toEpochMilliseconds()
                }
            }
        }
    }

    suspend fun hasPassword(userId: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            UserPasswords.selectAll()
                .where { UserPasswords.userId eq userId }
                .firstOrNull() != null
        }
    }

    // The frontend encrypts "email:password" — extract just the password part
    private fun extractPassword(decrypted: String): String {
        val colonIdx = decrypted.indexOf(':')
        return if (colonIdx >= 0) decrypted.substring(colonIdx + 1) else decrypted
    }

    suspend fun setPassword(userId: Int, encryptedPassword: String): Boolean {
        val decrypted = CryptoService.decrypt(encryptedPassword)
        if (decrypted == null) return false
        val password = extractPassword(decrypted)
        if (!isPasswordValid(password)) return false
        return setPasswordHash(userId, hashPassword(password))
    }

    suspend fun changePassword(userId: Int, currentEncryptedPassword: String, newEncryptedPassword: String): Boolean {
        val currentDecrypted = CryptoService.decrypt(currentEncryptedPassword) ?: return false
        val newDecrypted = CryptoService.decrypt(newEncryptedPassword) ?: return false
        val currentPassword = extractPassword(currentDecrypted)
        val newPassword = extractPassword(newDecrypted)

        if (!isPasswordValid(newPassword)) {
            return false
        }

        val storedHash = getPasswordHash(userId)
        if (storedHash != null) {
            if (!verifyPasswordString(currentPassword, storedHash)) {
                return false
            }
        }

        return setPasswordHash(userId, hashPassword(newPassword))
    }

    suspend fun verifyPassword(userId: Int, encryptedPassword: String): Boolean {
        val decrypted = CryptoService.decrypt(encryptedPassword) ?: return false
        val password = extractPassword(decrypted)
        val storedHash = getPasswordHash(userId) ?: return false
        return verifyPasswordString(password, storedHash)
    }

    private val argon2 = Argon2Function.getInstance(65536, 3, 4, 64, Argon2.ID, 19)

    private fun hashPassword(password: String): String {
        return Password.hash(password).with(argon2).getResult()
    }

    private fun verifyPasswordString(password: String, hash: String): Boolean {
        val argon2 = Argon2Function.getInstanceFromHash(hash)
        return Password.check(password, hash).with(argon2)
    }

    private suspend fun setPasswordHash(userId: Int, hash: String): Boolean {
        val now = clock.now().toEpochMilliseconds()
        return withContext(dbDispatcher) {
            transaction {
                val existing = UserPasswords.selectAll().where { UserPasswords.userId eq userId }.firstOrNull()
                if (existing != null) {
                    val updated = UserPasswords.update({ UserPasswords.userId eq userId }) {
                        it[UserPasswords.passwordHash] = hash
                        it[UserPasswords.updatedAt] = now
                    }
                    updated > 0
                } else {
                    try {
                        UserPasswords.insert {
                            it[UserPasswords.userId] = userId
                            it[UserPasswords.passwordHash] = hash
                            it[UserPasswords.createdAt] = now
                            it[UserPasswords.updatedAt] = now
                        }
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
            }
        }
    }

    private suspend fun getPasswordHash(userId: Int): String? = withContext(dbDispatcher) {
        transaction {
            UserPasswords.selectAll()
                .where { UserPasswords.userId eq userId }
                .firstOrNull()
                ?.get(UserPasswords.passwordHash)
        }
    }

    private fun isPasswordValid(password: String): Boolean {
        if (password.length < 8 || password.length > 128) return false
        if (!password.any { it.isUpperCase() }) return false
        if (!password.any { it.isLowerCase() }) return false
        if (!password.any { it.isDigit() }) return false
        val symbolPattern = Regex("[!@#\$%^&*(),.?\":{}|<>\\-_+=()\\[\\]\\\\|°º«»¿]")
        if (!symbolPattern.containsMatchIn(password)) return false
        return true
    }

    suspend fun requestPasswordReset(email: String, language: String): Result<Boolean> {
        val user = userRepository.getByEmail(email) ?: return Result.success(true)
        
        val resetAttemptsToday = withContext(dbDispatcher) {
            transaction {
                val startOfDay = getStartOfDayMillis()
                PasswordResetTokens.selectAll()
                    .where { (PasswordResetTokens.userId eq user.id) and (PasswordResetTokens.createdAt greaterEq startOfDay) }
                    .count()
            }
        }
        
        if (resetAttemptsToday >= maxResetEmailsPerDay) {
            return Result.failure(Exception("Maximum password reset requests (3) reached for today. Please try again tomorrow."))
        }

        val token = generateToken()
        val expiresAt = clock.now().toEpochMilliseconds() + passwordResetExpirationMs

        withContext(dbDispatcher) {
            transaction {
                try {
                    PasswordResetTokens.insert {
                        it[PasswordResetTokens.userId] = user.id
                        it[PasswordResetTokens.token] = token
                        it[PasswordResetTokens.expiresAt] = expiresAt
                        it[PasswordResetTokens.createdAt] = clock.now().toEpochMilliseconds()
                    }
                } catch (e: Exception) {
                    return@transaction
                }
            }
        }

        val resetUrl = "$baseUrl/reset-password?token=$token"
        val (subject, body) = getLocalizedResetContent(language, user.displayName, resetUrl)

        val sent = notificationPort.sendEmail(email, subject, body)
        return Result.success(sent)
    }

    suspend fun resetPassword(token: String, encryptedNewPassword: String): Boolean {
        val userId = verifyResetToken(token) ?: return false
        val newPassword = CryptoService.decrypt(encryptedNewPassword) ?: return false

        if (!isPasswordValid(newPassword)) {
            return false
        }

        val hash = hashPassword(newPassword)
        if (!setPasswordHash(userId, hash)) {
            return false
        }

        withContext(dbDispatcher) {
            transaction {
                PasswordResetTokens.deleteWhere { PasswordResetTokens.userId eq userId }
            }
        }

        return true
    }

    private suspend fun verifyResetToken(token: String): Int? = withContext(dbDispatcher) {
        transaction {
            val now = clock.now().toEpochMilliseconds()
            val tokenRow = PasswordResetTokens
                .selectAll()
                .where { PasswordResetTokens.token eq token }
                .firstOrNull()

            if (tokenRow != null && tokenRow[PasswordResetTokens.expiresAt] > now) {
                tokenRow[PasswordResetTokens.userId]
            } else {
                null
            }
        }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun getStartOfDayMillis(): Long {
        val now = clock.now().toEpochMilliseconds()
        val dayMillis = 24 * 60 * 60 * 1000L
        return now - (now % dayMillis)
    }

    private fun getLocalizedResetContent(language: String, displayName: String, resetUrl: String): Pair<String, String> {
        return when (language.lowercase()) {
            "es" -> "Restablecer contraseña - Adopt-U" to """
                Hola $displayName,
                
                Hemos recibido una solicitud para restablecer la contraseña de tu cuenta en Adopt-U.
                
                Haz clic en el siguiente enlace para restablecer tu contraseña:
                $resetUrl
                
                Este enlace expirará en 15 minutos.
                
                Si no solicitaste este cambio, puedes ignorar este correo de manera segura.
            """.trimIndent()
            
            "fr" -> "Réinitialiser le mot de passe - Adopt-U" to """
                Bonjour $displayName,
                
                Nous avons reçu une demande de réinitialisation du mot de passe de votre compte Adopt-U.
                
                Cliquez sur le lien suivant pour réinitialiser votre mot de passe:
                $resetUrl
                
                Ce lien expirera dans 15 minutes.
                
                Si vous n'avez pas demandé cette modification, vous pouvez ignorer cet email en toute sécurité.
            """.trimIndent()
            
            "pt" -> "Redefinir senha - Adopt-U" to """
                Olá $displayName,
                
                Recebemos uma solicitação para redefinir a senha da sua conta no Adopt-U.
                
                Clique no link abaixo para redefinir sua senha:
                $resetUrl
                
                Este link expirará em 15 minutos.
                
                Se você não solicitou esta alteração, pode ignorar este e-mail com segurança.
            """.trimIndent()
            
            "zh" -> "重置密码 - Adopt-U" to """
                您好 $displayName,
                
                我们收到了您Adopt-U账户的密码重置请求。
                
                点击以下链接重置您的密码:
                $resetUrl
                
                此链接将在15分钟后过期。
                
                如果您没有请求此更改，可以安全地忽略此电子邮件。
            """.trimIndent()
            
            else -> "Reset your password - Adopt-U" to """
                Hello $displayName,
                
                We received a request to reset the password for your Adopt-U account.
                
                Click the link below to reset your password:
                $resetUrl
                
                This link will expire in 15 minutes.
                
                If you didn't request this change, you can safely ignore this email.
            """.trimIndent()
        }
    }
}

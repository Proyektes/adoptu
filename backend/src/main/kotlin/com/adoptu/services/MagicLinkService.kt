package com.adoptu.services

import com.adoptu.adapters.db.MagicLinkTokens
import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.dbDispatcher
import com.adoptu.ports.NotificationPort
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.services.EmailVerificationService
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val logger = LoggerFactory.getLogger(MagicLinkService::class.java)

@OptIn(ExperimentalTime::class)
class MagicLinkService(
    private val userRepository: UserRepositoryPort,
    private val notificationPort: NotificationPort,
    private val clock: Clock,
    private val baseUrl: String,
    private val emailVerificationService: EmailVerificationService
) {
    private val secureRandom = SecureRandom()
    private val magicLinkExpirationMs = 5 * 60 * 1000L
    private val maxMagicLinksPerDay = 5

    suspend fun requestMagicLink(email: String, language: String): Result<Boolean> {
        val user = userRepository.getByEmail(email) ?: run {
            logger.info("Magic link requested for unknown email (returning success to avoid enumeration)")
            return Result.success(true)
        }

        // Check if user's email is verified
        if (!userRepository.isEmailVerified(user.id)) {
            logger.warn("Magic link requested for unverified email userId=${user.id}")
            if (!emailVerificationService.canSendVerificationEmail(user.id)) {
                return Result.failure(Exception("Maximum verification emails (3) reached for today. Please try again tomorrow."))
            }

            val latestToken = userRepository.getLatestVerificationToken(user.id)
            val now = clock.now().toEpochMilliseconds()

            if (latestToken != null && latestToken.expiresAt > now) {
                return Result.failure(Exception("Verification email already sent. Please check your inbox or wait for the link to expire."))
            }

            val result = emailVerificationService.resendVerificationEmail(user.id, email, user.displayName, language)
            return if (result.isSuccess && result.getOrDefault(false)) {
                Result.failure(Exception("Email not verified. A new verification email has been sent to your inbox."))
            } else {
                Result.failure(Exception("Failed to send verification email. Please try again."))
            }
        }

        val requestsToday = withContext(dbDispatcher) {
            transaction {
                val startOfDay = getStartOfDayMillis()
                MagicLinkTokens.deleteWhere { MagicLinkTokens.expiresAt lessEq clock.now().toEpochMilliseconds() }
                MagicLinkTokens.selectAll()
                    .where { (MagicLinkTokens.userId eq user.id) and (MagicLinkTokens.createdAt greaterEq startOfDay) }
                    .count()
            }
        }

        if (requestsToday >= maxMagicLinksPerDay) {
            logger.warn("Magic link rate limit reached for userId=${user.id} (${requestsToday}/$maxMagicLinksPerDay today)")
            return Result.failure(Exception("Maximum magic link requests (5) reached for today. Please try again tomorrow."))
        }

        val token = generateToken()
        val expiresAt = clock.now().toEpochMilliseconds() + magicLinkExpirationMs

        val tokenSaved = withContext(dbDispatcher) {
            transaction {
                try {
                    MagicLinkTokens.insert {
                        it[MagicLinkTokens.userId] = user.id
                        it[MagicLinkTokens.token] = token
                        it[MagicLinkTokens.expiresAt] = expiresAt
                        it[MagicLinkTokens.createdAt] = clock.now().toEpochMilliseconds()
                        it[MagicLinkTokens.usedAt] = null
                    }
                    true
                } catch (e: Exception) {
                    logger.error("Failed to insert magic link token for userId=${user.id}: ${e.message}")
                    false
                }
            }
        }

        if (!tokenSaved) {
            return Result.failure(Exception("Failed to create magic link. Please try again."))
        }

        val loginUrl = "$baseUrl/api/auth/magic-link-login?token=$token"
        val (subject, body) = getLocalizedMagicLinkContent(language, user.displayName, loginUrl)

        val sent = notificationPort.sendEmail(email, subject, body)
        if (sent) {
            logger.info("Magic link sent to userId=${user.id}")
        } else {
            logger.warn("Magic link email delivery failed for userId=${user.id}")
        }
        return Result.success(sent)
    }

    suspend fun verifyMagicLink(token: String): MagicLinkResult? = withContext(dbDispatcher) {
        transaction {
            val now = clock.now().toEpochMilliseconds()
            val tokenRow = MagicLinkTokens
                .selectAll()
                .where { (MagicLinkTokens.token eq token) and (MagicLinkTokens.usedAt.isNull()) }
                .firstOrNull()

            if (tokenRow == null) {
                logger.warn("Magic link verification failed: token not found or already used")
                return@transaction null
            }

            if (tokenRow[MagicLinkTokens.expiresAt] <= now) {
                logger.warn("Magic link verification failed: token expired for userId=${tokenRow[MagicLinkTokens.userId]}")
                MagicLinkTokens.deleteWhere { MagicLinkTokens.id eq tokenRow[MagicLinkTokens.id] }
                return@transaction null
            }

            val userId = tokenRow[MagicLinkTokens.userId]
            val userRow = Users.selectAll().where { Users.id eq userId }.firstOrNull()
                ?: return@transaction null

            val roles = UserActiveRoles.selectAll()
                .where { UserActiveRoles.userId eq userId }
                .map { it[UserActiveRoles.role] }
            val primaryRole = if (roles.contains("ADMIN")) "ADMIN" else roles.firstOrNull() ?: "ADOPTER"

            MagicLinkResult(
                userId = userId,
                username = userRow[Users.username],
                displayName = userRow[Users.displayName],
                primaryRole = primaryRole
            )
        }
    }

    suspend fun consumeMagicLink(token: String) {
        withContext(dbDispatcher) {
            transaction {
                MagicLinkTokens.update({ MagicLinkTokens.token eq token }) {
                    it[MagicLinkTokens.usedAt] = clock.now().toEpochMilliseconds()
                }
            }
        }
    }

    fun releaseMagicLinkToken(token: String) {
        // Token is not consumed - no action needed, it remains valid
    }

    suspend fun verifyAndConsumeMagicLink(token: String): MagicLinkResult? {
        val result = verifyMagicLink(token)
        if (result != null) {
            consumeMagicLink(token)
        }
        return result
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

    private fun getLocalizedMagicLinkContent(language: String, displayName: String, loginUrl: String): Pair<String, String> {
        return when (language.lowercase()) {
            "es" -> "Enlace de inicio de sesión - Adopt-U" to """
                Hola $displayName,
                
                Haz clic en el siguiente enlace para iniciar sesión en tu cuenta de Adopt-U:
                $loginUrl
                
                Este enlace expirará en 5 minutos.
                
                Si no solicitaste este enlace, puedes ignorarlo de manera segura.
            """.trimIndent()
            
            "fr" -> "Lien de connexion - Adopt-U" to """
                Bonjour $displayName,
                
                Cliquez sur le lien suivant pour vous connecter à votre compte Adopt-U:
                $loginUrl
                
                Ce lien expirera dans 5 minutes.
                
                Si vous n'avez pas demandé ce lien, vous pouvez l'ignorer en toute sécurité.
            """.trimIndent()
            
            "pt" -> "Link de login - Adopt-U" to """
                Olá $displayName,
                
                Clique no link abaixo para fazer login na sua conta do Adopt-U:
                $loginUrl
                
                Este link expirará em 5 minutos.
                
                Se você não solicitou este link, pode ignorá-lo com segurança.
            """.trimIndent()
            
            "zh" -> "登录链接 - Adopt-U" to """
                您好 $displayName,
                
                点击以下链接登录您的Adopt-U账户:
                $loginUrl
                
                此链接将在5分钟后过期。
                
                如果您没有请求此链接，可以安全地忽略它。
            """.trimIndent()
            
            else -> "Login link - Adopt-U" to """
                Hello $displayName,
                
                Click the link below to sign in to your Adopt-U account:
                $loginUrl
                
                This link will expire in 5 minutes.
                
                If you didn't request this link, you can safely ignore it.
            """.trimIndent()
        }
    }

    data class MagicLinkResult(
        val userId: Int,
        val username: String,
        val displayName: String,
        val primaryRole: String
    )
}

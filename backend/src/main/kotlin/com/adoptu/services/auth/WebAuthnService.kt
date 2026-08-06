package com.adoptu.services.auth

import com.adoptu.adapters.db.WebAuthnCredentials
import com.adoptu.adapters.db.dbDispatcher
import com.adoptu.services.PasswordService
import com.adoptu.services.UserService
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * What's left of the pre-AuthKit-cutover auth engine (see commit d84a9402, "migrate auth routes
 * onto AuthKit") -- passkey registration/login, password registration, and magic-link handling all
 * moved onto AuthKit's own use cases (StartPasskeyLoginUseCase/FinishPasskeyLoginUseCase/
 * RegisterUseCase/RequestMagicLinkUseCase/etc., wired in AuthRoutes.kt). The two methods below are
 * the only call sites that survived the cutover -- they don't do WebAuthn ceremony verification
 * themselves, just delegate to [userService]/[passwordService] and touch the shared
 * `webauthn_credentials` table AuthKit's own [com.adoptu.adapters.authkit.AdoptuPasskeyCredentialRepositoryAdapter]
 * now owns.
 */
class WebAuthnService(
    private val userService: UserService,
    private val passwordService: PasswordService,
) {
    /** Legacy fallback for email-verification tokens minted before the AuthKit cutover, which live
     *  in the old EmailVerificationTokens table rather than AuthKit's shared resetTokenHash slot --
     *  see AuthRoutes.kt's `/api/auth/verify-email` handler. */
    suspend fun verifyTokenAndGetLanguage(token: String): Pair<Boolean, String> {
        return userService.verifyTokenAndGetLanguage(token)
    }

    // Admin-triggered account recovery: invalidates the user's current password and
    // passkeys (no data deletion) and re-sends the same forgot-password email, so the
    // emailed link is the re-proof of email ownership before any new credential can be set.
    suspend fun forcePasswordReset(userId: Int): Boolean {
        val user = userService.getById(userId) ?: return false
        val email = user.email ?: return false

        withContext(dbDispatcher) {
            transaction {
                WebAuthnCredentials.deleteWhere { WebAuthnCredentials.userId eq userId }
            }
        }
        passwordService.invalidatePassword(userId)

        return passwordService.requestPasswordReset(email, user.language).getOrDefault(false)
    }
}

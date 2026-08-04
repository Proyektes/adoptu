package com.adoptu.services.auth

import com.adoptu.adapters.db.PendingRoleActivations
import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.WebAuthnChallenges
import com.adoptu.adapters.db.WebAuthnCredentials
import com.adoptu.adapters.db.dbDispatcher
import com.adoptu.dto.input.UserRole
import com.adoptu.services.EmailVerificationService
import com.adoptu.services.MagicLinkService
import com.adoptu.services.PasswordService
import com.adoptu.services.UserService
import com.adoptu.services.crypto.CryptoService
import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.RegistrationData
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.security.SecureRandom
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// Mirrors the isEmailVerified gate that POST /api/users/{role}-profile (activate=true)
// already enforces for these role types (UsersRoutes.kt/PhotographerRoutes.kt) - a brand
// new registration is never verified yet, so none of these can be granted at signup time.
// Selecting one of these roles at registration just records intent on the form; the user
// must activate it from /profile (through that already-gated endpoint) after verifying.
//
// Derived, not hand-listed: every role requires verification except ADOPTER (the default,
// no-trust-implications role every signup gets) and ADMIN (never self-registered - only ever
// added synthetically below when the email matches the configured admin.email, and that path
// stays immediate on purpose, not gated on verification). This used to be a hand-maintained
// literal that had to be kept in sync with an identical copy in AuthRoutes.kt on every new role -
// easy to update one and forget the other. Deriving it means a new UserRole entry is safely
// gated by default with zero extra code, in exactly one place.
private val ROLES_REQUIRING_VERIFICATION_BEFORE_ACTIVATION = UserRole.entries.toSet() - UserRole.ADMIN - UserRole.ADOPTER

enum class VerificationResendOutcome { SENT, ALREADY_VERIFIED, RATE_LIMITED, SEND_FAILED, USER_NOT_FOUND }

// These are serialized directly as HTTP response bodies (res.send(...) in AuthRoutes.kt) and
// MUST stay top-level, not nested inside WebAuthnService: jackson-module-kotlin's kotlin-reflect
// based introspection cannot resolve a nested data class's Kotlin @Metadata under GraalVM
// native-image (kotlin.reflect.jvm.internal.KotlinReflectionInternalError: Unresolved class),
// which made every passkey registration/login entry point 500 (or silently serialize as `{}`)
// in production - see bug-205/bug-206 in .wolf/buglog.json. Nested data classes that are never
// serialized directly (AuthenticatedUser, AuthResult, RegistrationResult below) don't hit this.
data class RelyingParty(
    val id: String,
    val name: String
)

data class PublicKeyUser(
    val id: String,
    val name: String,
    val displayName: String
)

data class PubKeyCredParam(
    val type: String,
    val alg: Int
)

data class PublicKeyOptions(
    val rp: RelyingParty,
    val user: PublicKeyUser,
    val challenge: String,
    val pubKeyCredParams: List<PubKeyCredParam>
)

data class RegistrationOptionsResponse(
    val publicKey: PublicKeyOptions
)

data class AssertionOptionsResponse(
    val challenge: String,
    val rpId: String,
    val userVerification: String
)

@OptIn(ExperimentalTime::class)
class WebAuthnService(
    private val clock: Clock,
    private val emailVerificationService: EmailVerificationService,
    private val userService: UserService,
    private val passwordService: PasswordService,
    private val magicLinkService: MagicLinkService,
    private val adminEmail: String,
    private val rpId: String,
    private val rpName: String,
    private val origins: List<String>
) {
    private val objectConverter = ObjectConverter()
    private val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager(objectConverter)
    private val attestedCredentialDataConverter = AttestedCredentialDataConverter(objectConverter)
    private val secureRandom = SecureRandom()

    data class AuthenticatedUser(
        val id: Int,
        val username: String,
        val displayName: String,
        val role: String
    )

    data class AuthResult(
        val userId: Int,
        val user: AuthenticatedUser
    )

    data class RegistrationResult(
        val userId: Int,
        val emailSent: Boolean
    )

    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun generateRegistrationOptions(email: String, displayName: String): RegistrationOptionsResponse {
        val challenge = ByteArray(32).also { secureRandom.nextBytes(it) }
        ChallengeStore.store(email, challenge)

        val userId = ByteArray(32).also { secureRandom.nextBytes(it) }

        return RegistrationOptionsResponse(
            publicKey = PublicKeyOptions(
                rp = RelyingParty(id = rpId, name = rpName),
                user = PublicKeyUser(
                    id = base64UrlEncode(userId),
                    name = email,
                    displayName = displayName
                ),
                challenge = base64UrlEncode(challenge),
                pubKeyCredParams = listOf(
                    PubKeyCredParam(type = "public-key", alg = -7),
                    PubKeyCredParam(type = "public-key", alg = -257)
                )
            )
        )
    }

    fun generateRegistrationOptionsForUser(userId: Int, email: String, displayName: String): RegistrationOptionsResponse {
        val challenge = ByteArray(32).also { secureRandom.nextBytes(it) }
        ChallengeStore.storeForUser(userId, challenge)

        val userIdBytes = ByteArray(32).also { secureRandom.nextBytes(it) }

        return RegistrationOptionsResponse(
            publicKey = PublicKeyOptions(
                rp = RelyingParty(id = rpId, name = rpName),
                user = PublicKeyUser(
                    id = base64UrlEncode(userIdBytes),
                    name = email,
                    displayName = displayName
                ),
                challenge = base64UrlEncode(challenge),
                pubKeyCredParams = listOf(
                    PubKeyCredParam(type = "public-key", alg = -7),
                    PubKeyCredParam(type = "public-key", alg = -257)
                )
            )
        )
    }

    suspend fun registerWithPassword(email: String, displayName: String, roles: Set<UserRole>, encryptedPassword: String): RegistrationResult? {
        val passwordService = this.passwordService
        val decryptedPassword = CryptoService.decrypt(encryptedPassword) ?: return null
        if (!isPasswordValid(decryptedPassword)) return null

        val userId = withContext(dbDispatcher) {
            transaction {
                val existingUser = Users.selectAll().where { Users.username eq email }.firstOrNull()

                val id = if (existingUser != null) {
                    existingUser[Users.id]
                } else {
                    Users.insert {
                        it[Users.username] = email
                        it[Users.displayName] = displayName
                        it[Users.createdAt] = clock.now().toEpochMilliseconds()
                    } get Users.id
                }

                if (existingUser == null) {
                    val effectiveRoles = if (email.equals(adminEmail, ignoreCase = true)) {
                        roles + UserRole.ADMIN
                    } else {
                        roles
                    }
                    (effectiveRoles - ROLES_REQUIRING_VERIFICATION_BEFORE_ACTIVATION).forEach { role ->
                        UserActiveRoles.insert {
                            it[UserActiveRoles.userId] = id
                            it[UserActiveRoles.role] = role.name
                        }
                    }
                    (effectiveRoles intersect ROLES_REQUIRING_VERIFICATION_BEFORE_ACTIVATION).forEach { role ->
                        PendingRoleActivations.insert {
                            it[PendingRoleActivations.userId] = id
                            it[PendingRoleActivations.role] = role.name
                        }
                    }
                }
                id
            }
        }

        passwordService.setPassword(userId, encryptedPassword)

        val emailResult = emailVerificationService.generateAndSendVerificationEmail(userId, email, displayName, "en")
        val emailSent = emailResult.getOrDefault(false)
        return RegistrationResult(userId = userId, emailSent = emailSent)
    }

    suspend fun hasPasskey(userId: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            WebAuthnCredentials.selectAll()
                .where { WebAuthnCredentials.userId eq userId }
                .firstOrNull() != null
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

    suspend fun registerAdditionalPasskey(userId: Int, registrationResponseJson: String): Boolean {
        val storedChallenge = ChallengeStore.retrieveForUser(userId) ?: return false
        ChallengeStore.removeForUser(userId)

        val serverProperty = ServerProperty.builder()
            .origins(origins.map { Origin(it) }.toSet())
            .rpId(rpId)
            .challenge(DefaultChallenge(storedChallenge))
            .build()

        return try {
            val registrationData: RegistrationData =
                webAuthnManager.parseRegistrationResponseJSON(registrationResponseJson)
            val params = RegistrationParameters(serverProperty, null, false, true)
            webAuthnManager.verify(registrationData, params)

            val attestedCredentialData =
                registrationData.attestationObject!!.authenticatorData.attestedCredentialData!!
            val acdBytes = attestedCredentialDataConverter.convert(attestedCredentialData)

            withContext(dbDispatcher) {
                transaction {
                    WebAuthnCredentials.insert {
                        it[WebAuthnCredentials.userId] = userId
                        it[WebAuthnCredentials.credentialId] = base64UrlEncode(attestedCredentialData.credentialId)
                        it[WebAuthnCredentials.attestedCredentialDataBase64] = Base64.getEncoder().encodeToString(acdBytes)
                        it[WebAuthnCredentials.signCount] = registrationData.attestationObject!!.authenticatorData.signCount
                        it[WebAuthnCredentials.transports] = null
                        it[WebAuthnCredentials.createdAt] = clock.now().toEpochMilliseconds()
                    }
                }
            }
            true
        } catch (e: Exception) {
            println("ERROR registerAdditionalPasskey for userId=$userId: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    suspend fun verifyAndRegister(
        email: String,
        displayName: String,
        roles: Set<UserRole>,
        registrationResponseJson: String,
        language: String = "en"
    ): RegistrationResult? {
        val storedChallenge = ChallengeStore.retrieve(email) ?: return null
        ChallengeStore.remove(email)

        val serverProperty = ServerProperty.builder()
            .origins(origins.map { Origin(it) }.toSet())
            .rpId(rpId)
            .challenge(DefaultChallenge(storedChallenge))
            .build()

        return try {
            val registrationData: RegistrationData =
                webAuthnManager.parseRegistrationResponseJSON(registrationResponseJson)
            val params = RegistrationParameters(serverProperty, null, false, true)
            webAuthnManager.verify(registrationData, params)

            val attestedCredentialData =
                registrationData.attestationObject!!.authenticatorData.attestedCredentialData!!
            val acdBytes = attestedCredentialDataConverter.convert(attestedCredentialData)

            val userId = withContext(dbDispatcher) {
                transaction {
                    val existingUser = Users.selectAll().where { Users.username eq email }.firstOrNull()

                    val id = if (existingUser != null) {
                        existingUser[Users.id]
                    } else {
                        Users.insert {
                            it[Users.username] = email
                            it[Users.displayName] = displayName
                            it[Users.createdAt] = clock.now().toEpochMilliseconds()
                        } get Users.id
                    }

                    val existingCredential = WebAuthnCredentials
                        .selectAll()
                        .where { WebAuthnCredentials.userId eq id }
                        .firstOrNull()

                    if (existingCredential == null) {
                        WebAuthnCredentials.insert {
                            it[WebAuthnCredentials.userId] = id
                            it[WebAuthnCredentials.credentialId] = base64UrlEncode(attestedCredentialData.credentialId)
                            it[WebAuthnCredentials.attestedCredentialDataBase64] = Base64.getEncoder().encodeToString(acdBytes)
                            it[WebAuthnCredentials.signCount] = registrationData.attestationObject!!.authenticatorData.signCount
                            it[WebAuthnCredentials.transports] = null
                            it[WebAuthnCredentials.createdAt] = clock.now().toEpochMilliseconds()
                        }
                    }

                    if (existingUser == null) {
                        val effectiveRoles = if (email.equals(adminEmail, ignoreCase = true)) {
                            roles + UserRole.ADMIN
                        } else {
                            roles
                        }
                        (effectiveRoles - ROLES_REQUIRING_VERIFICATION_BEFORE_ACTIVATION).forEach { role ->
                            UserActiveRoles.insert {
                                it[UserActiveRoles.userId] = id
                                it[UserActiveRoles.role] = role.name
                            }
                        }
                        (effectiveRoles intersect ROLES_REQUIRING_VERIFICATION_BEFORE_ACTIVATION).forEach { role ->
                            PendingRoleActivations.insert {
                                it[PendingRoleActivations.userId] = id
                                it[PendingRoleActivations.role] = role.name
                            }
                        }
                    }

                    id
                }
            }

            val emailResult = emailVerificationService.generateAndSendVerificationEmail(userId, email, displayName, language)

            val emailSent = emailResult.getOrDefault(false)
            RegistrationResult(userId = userId, emailSent = emailSent)
        } catch (e: Exception) {
            println("ERROR verifyAndRegister for email=$email: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    fun generateAssertionOptions(): AssertionOptionsResponse {
        val challenge = ByteArray(32).also { secureRandom.nextBytes(it) }
        ChallengeStore.storeAssertion(challenge)

        return AssertionOptionsResponse(
            challenge = base64UrlEncode(challenge),
            rpId = rpId,
            userVerification = "required"
        )
    }

    suspend fun verifyAndAuthenticate(authenticationResponseJson: String): AuthResult? {
        val authenticationData = try {
            webAuthnManager.parseAuthenticationResponseJSON(authenticationResponseJson)
        } catch (_: Exception) {
            return null
        }

        val credentialId = authenticationData.credentialId
        val credentialIdB64 = base64UrlEncode(credentialId)

        val credentialRow = withContext(dbDispatcher) {
            transaction {
                WebAuthnCredentials
                    .selectAll()
                    .where { WebAuthnCredentials.credentialId eq credentialIdB64 }
                    .firstOrNull()
            }
        } ?: return null

        val storedChallenge = ChallengeStore.retrieveAssertion() ?: return null
        ChallengeStore.removeAssertion()

        val serverProperty = ServerProperty.builder()
            .origins(origins.map { Origin(it) }.toSet())
            .rpId(rpId)
            .challenge(DefaultChallenge(storedChallenge))
            .build()

        val acdBytes = Base64.getDecoder().decode(credentialRow[WebAuthnCredentials.attestedCredentialDataBase64])
        val attestedCredentialData = attestedCredentialDataConverter.convert(acdBytes)

        val credentialRecord = CredentialRecordImpl(
            null,
            null,
            null,
            null,
            credentialRow[WebAuthnCredentials.signCount],
            attestedCredentialData,
            null,
            null,
            null,
            null
        )

        return try {
            val params = AuthenticationParameters(serverProperty, credentialRecord, null, true, true)
            webAuthnManager.verify(authenticationData, params)

            withContext(dbDispatcher) {
                transaction {
                    WebAuthnCredentials.update({ WebAuthnCredentials.id eq credentialRow[WebAuthnCredentials.id] }) {
                        it[signCount] = authenticationData.authenticatorData!!.signCount
                    }
                }
            }

            val userId = credentialRow[WebAuthnCredentials.userId]
            val user = withContext(dbDispatcher) {
                transaction {
                    Users.selectAll().where { Users.id eq userId }.firstOrNull()
                }
            } ?: return null

            val primaryRole = withContext(dbDispatcher) {
                transaction {
                    val roles = UserActiveRoles.selectAll()
                        .where { UserActiveRoles.userId eq userId }
                        .map { it[UserActiveRoles.role] }
                    if (roles.contains("ADMIN")) "ADMIN" else roles.firstOrNull() ?: "ADOPTER"
                }
            }

            AuthResult(
                userId = userId,
                user = AuthenticatedUser(
                    id = user[Users.id],
                    username = user[Users.username],
                    displayName = user[Users.displayName],
                    role = primaryRole
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun resendVerificationEmailDetailed(userId: Int): VerificationResendOutcome {
        val user = userService.getById(userId) ?: return VerificationResendOutcome.USER_NOT_FOUND

        if (userService.isUserVerified(userId)) {
            return VerificationResendOutcome.ALREADY_VERIFIED
        }

        if (!emailVerificationService.canSendVerificationEmail(userId)) {
            return VerificationResendOutcome.RATE_LIMITED
        }

        val email = user.email ?: return VerificationResendOutcome.USER_NOT_FOUND
        val result = emailVerificationService.resendVerificationEmail(userId, email, user.displayName, user.language)
        return if (result.getOrDefault(false)) VerificationResendOutcome.SENT else VerificationResendOutcome.SEND_FAILED
    }

    suspend fun resendVerificationEmail(userId: Int): Boolean =
        resendVerificationEmailDetailed(userId) == VerificationResendOutcome.SENT

    suspend fun resendVerificationEmailByEmail(email: String): Boolean {
        val user = userService.getByEmail(email) ?: return false
        
        if (userService.isUserVerified(user.id)) {
            return false
        }

        if (!emailVerificationService.canSendVerificationEmail(user.id)) {
            return false
        }

        val result = emailVerificationService.resendVerificationEmail(user.id, email, user.displayName, user.language)
        return result.getOrDefault(false)
    }

    suspend fun verifyToken(token: String): Boolean {
        return userService.verifyToken(token)
    }

    suspend fun verifyTokenAndGetLanguage(token: String): Pair<Boolean, String> {
        return userService.verifyTokenAndGetLanguage(token)
    }

    suspend fun isUserVerified(userId: Int): Boolean {
        return userService.isUserVerified(userId)
    }

    suspend fun getUserByEmail(email: String): com.adoptu.dto.input.UserDto? {
        return userService.getByEmail(email)
    }

    suspend fun getLanguageByEmail(email: String): String {
        return userService.getByEmail(email)?.language ?: "en"
    }

    suspend fun verifyPassword(userId: Int, encryptedPassword: String): Boolean {
        return passwordService.verifyPassword(userId, encryptedPassword)
    }

    suspend fun requestMagicLink(email: String): Result<Boolean> {
        val language = userService.getByEmail(email)?.language ?: "en"
        return magicLinkService.requestMagicLink(email, language)
    }

    suspend fun requestPasswordReset(email: String): Result<Boolean> {
        val language = userService.getByEmail(email)?.language ?: "en"
        return passwordService.requestPasswordReset(email, language)
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

    suspend fun resetPassword(token: String, encryptedNewPassword: String): Boolean {
        return passwordService.resetPassword(token, encryptedNewPassword)
    }

    suspend fun verifyAndConsumeMagicLink(token: String): MagicLinkService.MagicLinkResult? {
        return magicLinkService.verifyAndConsumeMagicLink(token)
    }

    suspend fun verifyMagicLink(token: String): MagicLinkService.MagicLinkResult? {
        return magicLinkService.verifyMagicLink(token)
    }

    suspend fun consumeMagicLink(token: String) {
        magicLinkService.consumeMagicLink(token)
    }

    // DB-backed (not in-memory) so a challenge created on one ECS task is still found when the
    // browser posts the signed response back to a different task - see WebAuthnChallenges in
    // Models.kt. Rows also expire (CHALLENGE_TTL_MS) and are opportunistically purged on every
    // store, instead of growing unbounded like the old in-memory map did.
    private object ChallengeStore {
        private const val CHALLENGE_TTL_MS = 5 * 60 * 1000L
        private const val ASSERTION_KEY = "assertion"

        private fun registrationKey(username: String) = "registration:${username.lowercase()}"
        private fun registrationUserKey(userId: Int) = "registration-user:$userId"

        private fun put(key: String, challenge: ByteArray) {
            val now = System.currentTimeMillis()
            transaction {
                WebAuthnChallenges.deleteWhere { WebAuthnChallenges.expiresAt lessEq now }
                WebAuthnChallenges.deleteWhere { WebAuthnChallenges.challengeKey eq key }
                WebAuthnChallenges.insert {
                    it[challengeKey] = key
                    it[WebAuthnChallenges.challenge] = Base64.getEncoder().encodeToString(challenge)
                    it[expiresAt] = now + CHALLENGE_TTL_MS
                }
            }
        }

        private fun take(key: String): ByteArray? {
            val now = System.currentTimeMillis()
            return transaction {
                val row = WebAuthnChallenges.selectAll()
                    .where { (WebAuthnChallenges.challengeKey eq key) and (WebAuthnChallenges.expiresAt greater now) }
                    .firstOrNull() ?: return@transaction null

                WebAuthnChallenges.deleteWhere { WebAuthnChallenges.id eq row[WebAuthnChallenges.id] }
                Base64.getDecoder().decode(row[WebAuthnChallenges.challenge])
            }
        }

        private fun delete(key: String) {
            transaction {
                WebAuthnChallenges.deleteWhere { WebAuthnChallenges.challengeKey eq key }
            }
        }

        fun store(username: String, challenge: ByteArray) = put(registrationKey(username), challenge)
        fun retrieve(username: String): ByteArray? = take(registrationKey(username))
        fun remove(username: String) = delete(registrationKey(username))

        fun storeForUser(userId: Int, challenge: ByteArray) = put(registrationUserKey(userId), challenge)
        fun retrieveForUser(userId: Int): ByteArray? = take(registrationUserKey(userId))
        fun removeForUser(userId: Int) = delete(registrationUserKey(userId))

        fun storeAssertion(challenge: ByteArray) = put(ASSERTION_KEY, challenge)
        fun retrieveAssertion(): ByteArray? = take(ASSERTION_KEY)
        fun removeAssertion() = delete(ASSERTION_KEY)
    }
}
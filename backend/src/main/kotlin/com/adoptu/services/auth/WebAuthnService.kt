package com.adoptu.services.auth

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
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
import org.jetbrains.exposed.v1.core.eq
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
private val ROLES_REQUIRING_VERIFICATION_BEFORE_ACTIVATION = setOf(
    UserRole.PHOTOGRAPHER, UserRole.TEMPORAL_HOME, UserRole.SHELTER, UserRole.STERILIZATION_SERVICE, UserRole.RESCUER
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

    data class RegistrationOptionsResponse(
        val publicKey: PublicKeyOptions
    )

    data class PublicKeyOptions(
        val rp: RelyingParty,
        val user: PublicKeyUser,
        val challenge: String,
        val pubKeyCredParams: List<PubKeyCredParam>
    )

    data class AssertionOptionsResponse(
        val challenge: String,
        val rpId: String,
        val userVerification: String
    )

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

    suspend fun resendVerificationEmail(userId: Int): Boolean {
        val user = userService.getById(userId) ?: return false
        
        if (userService.isUserVerified(userId)) {
            return false
        }

        if (!emailVerificationService.canSendVerificationEmail(userId)) {
            return false
        }

        val email = user.email ?: return false
        val result = emailVerificationService.resendVerificationEmail(userId, email, user.displayName, user.language)
        return result.getOrDefault(false)
    }

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

    private object ChallengeStore {
        private val challenges = mutableMapOf<String, ByteArray>()
        private val userChallenges = mutableMapOf<Int, ByteArray>()
        private var assertionChallenge: ByteArray? = null

        fun store(username: String, challenge: ByteArray) {
            challenges[username] = challenge
        }

        fun retrieve(username: String): ByteArray? = challenges.remove(username)

        fun remove(username: String) {
            challenges.remove(username)
        }

        fun storeForUser(userId: Int, challenge: ByteArray) {
            userChallenges[userId] = challenge
        }

        fun retrieveForUser(userId: Int): ByteArray? = userChallenges.remove(userId)

        fun removeForUser(userId: Int) {
            userChallenges.remove(userId)
        }

        fun storeAssertion(challenge: ByteArray) {
            assertionChallenge = challenge
        }

        fun retrieveAssertion(): ByteArray? = assertionChallenge.also { assertionChallenge = null }
        fun removeAssertion() {
            assertionChallenge = null
        }
    }
}
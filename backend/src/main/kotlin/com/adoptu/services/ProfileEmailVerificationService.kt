package com.adoptu.services

import com.adoptu.adapters.db.ProfileEmailVerificationTokens
import com.adoptu.adapters.db.UserShelters
import com.adoptu.adapters.db.UserSterilizationLocations
import com.adoptu.adapters.db.dbDispatcher
import com.adoptu.ports.NotificationPort
import com.adoptu.ports.UserRepositoryPort
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
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

enum class VerifiableProfileType {
    SHELTER, STERILIZATION
}

/**
 * Shelter and sterilization-location profiles carry their own public "contact email",
 * separate from the account's login email. That contact email is what gets shown to
 * the public once the profile is activated, so it needs its own proof of ownership
 * whenever it differs from the already-verified account email.
 */
@OptIn(ExperimentalTime::class)
class ProfileEmailVerificationService(
    private val userRepository: UserRepositoryPort,
    private val notificationPort: NotificationPort,
    private val clock: Clock,
    private val baseUrl: String = "http://localhost:8080"
) {
    private val secureRandom = SecureRandom()
    private val tokenExpirationMs = 24 * 60 * 60 * 1000L

    /**
     * Call whenever a profile's contact email is set or changed. Returns the value the
     * caller should persist into that profile's `emailVerified` column: true when there's
     * nothing to prove (blank, or same address as the already-verified account email),
     * false when a verification link was just sent and the address is pending.
     */
    suspend fun handleProfileEmail(
        userId: Int,
        profileType: VerifiableProfileType,
        accountEmail: String,
        profileEmail: String?,
        displayName: String
    ): Boolean {
        if (profileEmail.isNullOrBlank() || profileEmail.equals(accountEmail, ignoreCase = true)) {
            return true
        }

        val existingOwner = userRepository.getByEmail(profileEmail)
        if (existingOwner != null && existingOwner.id != userId) {
            throw IllegalArgumentException("This email is already associated with another account")
        }

        val token = generateToken()
        val expiresAt = clock.now().toEpochMilliseconds() + tokenExpirationMs

        withContext(dbDispatcher) {
            transaction {
                ProfileEmailVerificationTokens.deleteWhere {
                    (ProfileEmailVerificationTokens.userId eq userId) and
                        (ProfileEmailVerificationTokens.profileType eq profileType.name)
                }
                ProfileEmailVerificationTokens.insert {
                    it[ProfileEmailVerificationTokens.userId] = userId
                    it[ProfileEmailVerificationTokens.profileType] = profileType.name
                    it[ProfileEmailVerificationTokens.email] = profileEmail
                    it[ProfileEmailVerificationTokens.token] = token
                    it[ProfileEmailVerificationTokens.expiresAt] = expiresAt
                    it[ProfileEmailVerificationTokens.createdAt] = clock.now().toEpochMilliseconds()
                }
            }
        }

        val verifyUrl = "$baseUrl/verify-profile-email?token=$token"
        val label = if (profileType == VerifiableProfileType.SHELTER) "shelter" else "sterilization service"
        notificationPort.sendEmail(
            profileEmail,
            "Verify your contact email - Adopt-U",
            """
                Hello $displayName,

                Please verify this email address so it can be published as the public
                contact for your $label listing on Adopt-U:
                $verifyUrl

                This link will expire in 24 hours. The listing can't be made public
                until this address is verified.

                If you didn't request this, you can safely ignore this email.
            """.trimIndent()
        )

        return false
    }

    suspend fun verifyToken(token: String): Boolean = withContext(dbDispatcher) {
        transaction {
            val now = clock.now().toEpochMilliseconds()
            val row = ProfileEmailVerificationTokens.selectAll()
                .where { ProfileEmailVerificationTokens.token eq token }
                .firstOrNull() ?: return@transaction false

            if (row[ProfileEmailVerificationTokens.expiresAt] <= now) return@transaction false

            val userId = row[ProfileEmailVerificationTokens.userId]
            val email = row[ProfileEmailVerificationTokens.email]
            val updated = when (row[ProfileEmailVerificationTokens.profileType]) {
                VerifiableProfileType.SHELTER.name -> UserShelters.update({
                    (UserShelters.userId eq userId) and (UserShelters.email eq email)
                }) { it[UserShelters.emailVerified] = true }
                VerifiableProfileType.STERILIZATION.name -> UserSterilizationLocations.update({
                    (UserSterilizationLocations.userId eq userId) and (UserSterilizationLocations.email eq email)
                }) { it[UserSterilizationLocations.emailVerified] = true }
                else -> 0
            }

            if (updated > 0) {
                ProfileEmailVerificationTokens.deleteWhere { ProfileEmailVerificationTokens.id eq row[ProfileEmailVerificationTokens.id] }
                true
            } else {
                false
            }
        }
    }

    suspend fun isProfileEmailVerified(profileType: VerifiableProfileType, userId: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            when (profileType) {
                VerifiableProfileType.SHELTER -> UserShelters.selectAll()
                    .where { UserShelters.userId eq userId }
                    .firstOrNull()
                    ?.let { it[UserShelters.email].isNullOrBlank() || it[UserShelters.emailVerified] } ?: true
                VerifiableProfileType.STERILIZATION -> UserSterilizationLocations.selectAll()
                    .where { UserSterilizationLocations.userId eq userId }
                    .firstOrNull()
                    ?.let { it[UserSterilizationLocations.email].isNullOrBlank() || it[UserSterilizationLocations.emailVerified] } ?: true
            }
        }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

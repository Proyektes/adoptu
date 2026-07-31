package com.adoptu.adapters.authkit

import com.adoptu.adapters.db.AuthKitPasskeyCeremonies
import com.universaliun.auth.backend.domain.model.passkey.SignupPasskeyChallenge
import com.universaliun.auth.backend.domain.port.out.PasskeyCeremonyStorePort
import com.universaliun.auth.common.identity.AuthUserId
import com.yubico.webauthn.AssertionRequest
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

private const val TYPE_REGISTRATION = "REGISTRATION"
private const val TYPE_LOGIN = "LOGIN"
private const val TYPE_SIGNUP = "SIGNUP"

/**
 * Bridges AuthKit's [PasskeyCeremonyStorePort] onto Postgres via [AuthKitPasskeyCeremonies],
 * instead of accepting AuthKit's own in-memory default -- exactly the class of bug bug-210 already
 * found and fixed natively for [com.adoptu.services.crypto.CryptoService]/the retired
 * WebAuthnService.ChallengeStore: under real ECS horizontal scaling, a registration/login started
 * on one task must still be found by whichever task handles the matching finish request.
 *
 * `payloadJson` is Yubico's own [PublicKeyCredentialCreationOptions.toJson]/[AssertionRequest.toJson]
 * output, round-tripped via their matching `fromJson()` -- this adapter never needs to understand
 * their internal shape, only persist and retrieve the string.
 */
class AdoptuPasskeyCeremonyStoreAdapter(
    private val expiryMs: Long = 5 * 60 * 1000L,
) : PasskeyCeremonyStorePort {

    override fun saveRegistrationChallenge(requestId: String, userId: AuthUserId, options: PublicKeyCredentialCreationOptions) {
        transaction {
            pruneExpired()
            AuthKitPasskeyCeremonies.insert {
                it[AuthKitPasskeyCeremonies.requestId] = requestId
                it[type] = TYPE_REGISTRATION
                it[AuthKitPasskeyCeremonies.userId] = userId.value.toInt()
                it[payloadJson] = options.toJson()
                it[expiresAt] = System.currentTimeMillis() + expiryMs
            }
        }
    }

    override fun consumeRegistrationChallenge(requestId: String): Pair<AuthUserId, PublicKeyCredentialCreationOptions>? = transaction {
        val row = findAndDelete(requestId, TYPE_REGISTRATION) ?: return@transaction null
        val userId = AuthUserId(row[AuthKitPasskeyCeremonies.userId]!!.toString())
        userId to PublicKeyCredentialCreationOptions.fromJson(row[AuthKitPasskeyCeremonies.payloadJson])
    }

    override fun saveLoginChallenge(requestId: String, request: AssertionRequest) {
        transaction {
            pruneExpired()
            AuthKitPasskeyCeremonies.insert {
                it[AuthKitPasskeyCeremonies.requestId] = requestId
                it[type] = TYPE_LOGIN
                it[userId] = null
                it[payloadJson] = request.toJson()
                it[expiresAt] = System.currentTimeMillis() + expiryMs
            }
        }
    }

    override fun consumeLoginChallenge(requestId: String): AssertionRequest? = transaction {
        val row = findAndDelete(requestId, TYPE_LOGIN) ?: return@transaction null
        AssertionRequest.fromJson(row[AuthKitPasskeyCeremonies.payloadJson])
    }

    override fun saveSignupChallenge(requestId: String, email: String, displayName: String, options: PublicKeyCredentialCreationOptions) {
        transaction {
            pruneExpired()
            AuthKitPasskeyCeremonies.insert {
                it[AuthKitPasskeyCeremonies.requestId] = requestId
                it[type] = TYPE_SIGNUP
                it[userId] = null
                it[signupEmail] = email
                it[signupDisplayName] = displayName
                it[payloadJson] = options.toJson()
                it[expiresAt] = System.currentTimeMillis() + expiryMs
            }
        }
    }

    override fun consumeSignupChallenge(requestId: String): SignupPasskeyChallenge? = transaction {
        val row = findAndDelete(requestId, TYPE_SIGNUP) ?: return@transaction null
        SignupPasskeyChallenge(
            email = row[AuthKitPasskeyCeremonies.signupEmail]!!,
            displayName = row[AuthKitPasskeyCeremonies.signupDisplayName]!!,
            options = PublicKeyCredentialCreationOptions.fromJson(row[AuthKitPasskeyCeremonies.payloadJson]),
        )
    }

    private fun findAndDelete(requestId: String, type: String) =
        AuthKitPasskeyCeremonies.selectAll()
            .where {
                (AuthKitPasskeyCeremonies.requestId eq requestId) and
                    (AuthKitPasskeyCeremonies.type eq type) and
                    (AuthKitPasskeyCeremonies.expiresAt greater System.currentTimeMillis())
            }
            .singleOrNull()
            ?.also { row -> AuthKitPasskeyCeremonies.deleteWhere { AuthKitPasskeyCeremonies.requestId eq requestId } }

    /** Expired entries are only ever removed opportunistically on a new "start" call, same pattern
     * as WebAuthnChallenges' own pruning -- no scheduled task needed. */
    private fun pruneExpired() {
        AuthKitPasskeyCeremonies.deleteWhere { AuthKitPasskeyCeremonies.expiresAt lessEq System.currentTimeMillis() }
    }
}

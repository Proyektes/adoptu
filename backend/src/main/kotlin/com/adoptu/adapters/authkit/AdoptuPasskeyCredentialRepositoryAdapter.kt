package com.adoptu.adapters.authkit

import com.adoptu.adapters.db.WebAuthnCredentials
import com.universaliun.auth.backend.domain.model.passkey.PasskeyCredential
import com.universaliun.auth.backend.domain.port.out.PasskeyCredentialRepositoryPort
import com.universaliun.auth.common.identity.AuthUserId
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.attestation.authenticator.AAGUID
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import com.webauthn4j.data.attestation.authenticator.COSEKey
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.Base64

private val objectConverter = ObjectConverter()
private val attestedCredentialDataConverter = AttestedCredentialDataConverter(objectConverter)

private fun ByteArray.toB64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)
private fun String.fromB64Url(): ByteArray = Base64.getUrlDecoder().decode(this)

/**
 * Bridges AuthKit's [PasskeyCredentialRepositoryPort] onto this app's existing `webauthn_credentials`
 * table, instead of AuthKit's own separate `passkey_credentials` table — real credentials already
 * exist there (registered through the native, now-retired webauthn4j-based flow) and must keep
 * authenticating after the cutover, not silently stop working.
 *
 * ## The cross-library encoding boundary
 * This table's `attestedCredentialDataBase64` column stores a **webauthn4j**
 * `AttestedCredentialData` blob (`aaguid + credentialId + COSE public key`, the native format
 * produced by the retired [com.adoptu.services.auth.WebAuthnService]). AuthKit is built on a
 * **different** WebAuthn library — Yubico's `webauthn-server-core` — whose [PasskeyCredential]
 * only carries `credentialId`/`publicKeyCose` as raw byte arrays, not a webauthn4j object.
 *
 * The bridge works because both libraries implement the *same* WebAuthn/CTAP2 spec: the COSE
 * public key is a standardized CBOR-encoded `COSE_Key` map, produced by the authenticator itself
 * at registration time — neither library invents its own format, they just parse/re-serialize the
 * identical wire bytes the browser originally sent. [save] parses AuthKit's raw `publicKeyCose`
 * bytes into a webauthn4j [COSEKey] (`objectConverter`'s CBOR reader) and re-wraps it as an
 * [AttestedCredentialData] to persist in the existing column/format; the read path
 * ([findByCredentialId]/[findByUserId]) does the reverse, so a credential registered through the
 * **old** native flow round-trips correctly through AuthKit's **new** Yubico-based verification
 * too — proven by [AdoptuPasskeyCredentialRepositoryAdapterTest]'s real round-trip test, not just
 * asserted by this comment.
 *
 * `credentialId` has its own indexed column, base64url-encoded (`Base64.getUrlEncoder().withoutPadding()`)
 * — same encoding the retired native code already used, confirmed against its real call sites.
 * `attestedCredentialDataBase64` itself uses plain (non-URL) `Base64.getEncoder()`, also matching
 * the native code exactly.
 */
class AdoptuPasskeyCredentialRepositoryAdapter : PasskeyCredentialRepositoryPort {

    override fun save(credential: PasskeyCredential): PasskeyCredential = transaction {
        val coseKey = objectConverter.cborConverter.readValue(credential.publicKeyCose, COSEKey::class.java)!!
        val attestedCredentialData = AttestedCredentialData(AAGUID.ZERO, credential.credentialId, coseKey)
        val encodedBlob = Base64.getEncoder().encodeToString(attestedCredentialDataConverter.convert(attestedCredentialData))
        val encodedCredentialId = credential.credentialId.toB64Url()
        val encodedUserHandle = credential.userHandle.toB64Url()
        val userId = credential.userId.value.toInt()

        val exists = WebAuthnCredentials.selectAll().where { WebAuthnCredentials.credentialId eq encodedCredentialId }.any()
        if (exists) {
            WebAuthnCredentials.update({ WebAuthnCredentials.credentialId eq encodedCredentialId }) { row ->
                row[attestedCredentialDataBase64] = encodedBlob
                row[signCount] = credential.signatureCount
                row[transports] = credential.transports.takeIf { it.isNotEmpty() }?.joinToString(",")
                row[userHandle] = encodedUserHandle
            }
        } else {
            WebAuthnCredentials.insert { row ->
                row[WebAuthnCredentials.userId] = userId
                row[WebAuthnCredentials.credentialId] = encodedCredentialId
                row[attestedCredentialDataBase64] = encodedBlob
                row[signCount] = credential.signatureCount
                row[transports] = credential.transports.takeIf { it.isNotEmpty() }?.joinToString(",")
                row[createdAt] = credential.createdAt.toEpochMilli()
                row[userHandle] = encodedUserHandle
            }
        }
        credential
    }

    override fun findByCredentialId(credentialId: ByteArray): PasskeyCredential? = transaction {
        WebAuthnCredentials.selectAll()
            .where { WebAuthnCredentials.credentialId eq credentialId.toB64Url() }
            .singleOrNull()
            ?.toPasskeyCredential()
    }

    override fun findByUserId(userId: AuthUserId): List<PasskeyCredential> = transaction {
        val id = userId.value.toIntOrNull() ?: return@transaction emptyList()
        WebAuthnCredentials.selectAll()
            .where { WebAuthnCredentials.userId eq id }
            .map { it.toPasskeyCredential() }
    }

    override fun findByUserHandle(userHandle: ByteArray): PasskeyCredential? = transaction {
        WebAuthnCredentials.selectAll()
            .where { WebAuthnCredentials.userHandle eq userHandle.toB64Url() }
            .singleOrNull()
            ?.toPasskeyCredential()
    }

    override fun updateSignatureCount(credentialId: ByteArray, signatureCount: Long) {
        transaction {
            WebAuthnCredentials.update({ WebAuthnCredentials.credentialId eq credentialId.toB64Url() }) {
                it[signCount] = signatureCount
            }
        }
    }

    private fun ResultRow.toPasskeyCredential(): PasskeyCredential {
        val acdBytes = Base64.getDecoder().decode(this[WebAuthnCredentials.attestedCredentialDataBase64])
        val attestedCredentialData = attestedCredentialDataConverter.convert(acdBytes)
        val publicKeyCoseBytes = objectConverter.cborConverter.writeValueAsBytes(attestedCredentialData.coseKey)

        val userId = this[WebAuthnCredentials.userId]
        return PasskeyCredential(
            credentialId = this[WebAuthnCredentials.credentialId].fromB64Url(),
            userId = AuthUserId(userId.toString()),
            publicKeyCose = publicKeyCoseBytes,
            signatureCount = this[WebAuthnCredentials.signCount],
            transports = this[WebAuthnCredentials.transports]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
            createdAt = Instant.ofEpochMilli(this[WebAuthnCredentials.createdAt]),
            // Rows written before this column existed (including ones bridged from the retired
            // native webauthn4j flow) have no stored handle -- fall back to the deterministic
            // derivation AuthKit used everywhere before this fix.
            userHandle = this[WebAuthnCredentials.userHandle]?.fromB64Url() ?: userId.toString().toByteArray(Charsets.UTF_8),
        )
    }
}

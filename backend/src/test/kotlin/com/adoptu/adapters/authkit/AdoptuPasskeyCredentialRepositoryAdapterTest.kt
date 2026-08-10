package com.adoptu.adapters.authkit

import com.adoptu.adapters.db.WebAuthnCredentials
import com.adoptu.mocks.TestDatabase
import com.universaliun.auth.backend.domain.model.passkey.PasskeyCredential
import com.universaliun.auth.backend.domain.model.user.AuthUser
import com.universaliun.auth.backend.domain.model.user.Email
import com.universaliun.auth.common.identity.AuthUserId
import com.universaliun.auth.common.rbac.PermissionSet
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.attestation.authenticator.AAGUID
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import com.webauthn4j.data.attestation.authenticator.EC2COSEKey
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies the cross-library encoding boundary this adapter exists for: a passkey credential
 * whose COSE public key was produced by ONE WebAuthn library round-trips correctly through the
 * OTHER, using genuine EC P-256 keys (not hand-waved byte arrays) on both ends -- the same
 * "actually build a real key/ceremony, don't just assert on opaque bytes" standard already applied
 * elsewhere in this codebase (see cerebrum.md's hand-built WebAuthn ceremony notes).
 */
class AdoptuPasskeyCredentialRepositoryAdapterTest {

    private lateinit var adapter: AdoptuPasskeyCredentialRepositoryAdapter
    private lateinit var testUserId: AuthUserId
    private val objectConverter = ObjectConverter()
    private val attestedCredentialDataConverter = AttestedCredentialDataConverter(objectConverter)

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        adapter = AdoptuPasskeyCredentialRepositoryAdapter()
        testUserId = AdoptuUserRepositoryAdapter().save(
            AuthUser(
                id = AuthUserId(java.util.UUID.randomUUID().toString()),
                email = Email("passkey-cred-bridge@test.com"),
                displayName = "Passkey Cred Bridge",
                passwordHash = null,
                roles = emptySet(),
                permissions = PermissionSet.empty(0),
                enabled = true,
                emailVerified = true,
                createdAt = Instant.now(),
                lastModifiedAt = Instant.now(),
            )
        ).id
    }

    private fun freshEcPublicKey(): ECPublicKey {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        return keyPairGenerator.generateKeyPair().public as ECPublicKey
    }

    @Test fun `a credential saved through AuthKit's shape is byte-identical on read as an EC public key`() {
        val publicKey = freshEcPublicKey()
        val coseKey = EC2COSEKey.create(publicKey, COSEAlgorithmIdentifier.ES256)
        val publicKeyCoseBytes = objectConverter.cborConverter.writeValueAsBytes(coseKey)
        val credentialId = "test-credential-id".toByteArray()

        adapter.save(
            PasskeyCredential(
                credentialId = credentialId,
                userId = testUserId,
                publicKeyCose = publicKeyCoseBytes,
                signatureCount = 0,
                transports = setOf("internal", "hybrid"),
                createdAt = Instant.now(),
                userHandle = "test-user-handle".toByteArray(),
            )
        )

        val found = adapter.findByCredentialId(credentialId)
        assertNotNull(found)
        assertTrue(credentialId.contentEquals(found.credentialId))
        assertEquals(setOf("internal", "hybrid"), found.transports)

        // Prove it's not just "some bytes" -- re-parse the round-tripped publicKeyCose as a COSE
        // key and confirm it's the SAME EC point (x/y coordinates), the actual cryptographic
        // material a real login verification would use.
        val roundTrippedKey = objectConverter.cborConverter.readValue(found.publicKeyCose, EC2COSEKey::class.java)!!
        assertTrue(coseKey.x.contentEquals(roundTrippedKey.x))
        assertTrue(coseKey.y.contentEquals(roundTrippedKey.y))
    }

    @Test fun `a credential already stored in the native webauthn4j format (pre-cutover) reads correctly through the AuthKit bridge`() {
        // Simulates a row created by the retired native WebAuthnService, before this cutover --
        // proves existing users' already-registered passkeys keep working, not just new ones.
        val publicKey = freshEcPublicKey()
        val coseKey = EC2COSEKey.create(publicKey, COSEAlgorithmIdentifier.ES256)
        val credentialIdBytes = "pre-existing-credential".toByteArray()
        val attestedCredentialData = AttestedCredentialData(AAGUID.ZERO, credentialIdBytes, coseKey)
        val nativeBlob = Base64.getEncoder().encodeToString(attestedCredentialDataConverter.convert(attestedCredentialData))
        val nativeEncodedCredentialId = Base64.getUrlEncoder().withoutPadding().encodeToString(credentialIdBytes)

        val testUserIdInt = testUserId.value.toInt()
        transaction {
            WebAuthnCredentials.insert { row ->
                row[userId] = testUserIdInt
                row[credentialId] = nativeEncodedCredentialId
                row[attestedCredentialDataBase64] = nativeBlob
                row[signCount] = 5L
                row[transports] = null
                row[createdAt] = System.currentTimeMillis()
            }
        }

        val bridged = adapter.findByCredentialId(credentialIdBytes)
        assertNotNull(bridged)
        assertEquals(5L, bridged.signatureCount)
        assertEquals(testUserId, bridged.userId)
        // No userHandle column value was written above -- falls back to the deterministic
        // pre-fix derivation, same as what discoverable login would have used for this row before.
        assertTrue(testUserIdInt.toString().toByteArray().contentEquals(bridged.userHandle))

        val bridgedKey = objectConverter.cborConverter.readValue(bridged.publicKeyCose, EC2COSEKey::class.java)!!
        assertTrue(coseKey.x.contentEquals(bridgedKey.x))
        assertTrue(coseKey.y.contentEquals(bridgedKey.y))
    }

    @Test fun `findByCredentialId returns null for an unknown credential`() {
        assertNull(adapter.findByCredentialId("no-such-credential".toByteArray()))
    }

    @Test fun `findByUserId returns every credential for that user`() {
        val publicKey1 = freshEcPublicKey()
        val publicKey2 = freshEcPublicKey()
        adapter.save(passkeyCredential("cred-a".toByteArray(), publicKey1))
        adapter.save(passkeyCredential("cred-b".toByteArray(), publicKey2))

        val found = adapter.findByUserId(testUserId)

        assertEquals(2, found.size)
    }

    @Test fun `updateSignatureCount persists the new count`() {
        val credentialId = "sign-count-cred".toByteArray()
        adapter.save(passkeyCredential(credentialId, freshEcPublicKey()))

        adapter.updateSignatureCount(credentialId, 42L)

        assertEquals(42L, adapter.findByCredentialId(credentialId)?.signatureCount)
    }

    @Test fun `findByUserHandle finds a passkey-first signup's random handle, not just the deterministic one`() {
        val randomHandle = "random-signup-handle".toByteArray()
        adapter.save(
            PasskeyCredential(
                credentialId = "handle-lookup-cred".toByteArray(),
                userId = testUserId,
                publicKeyCose = objectConverter.cborConverter.writeValueAsBytes(EC2COSEKey.create(freshEcPublicKey(), COSEAlgorithmIdentifier.ES256)),
                signatureCount = 0,
                transports = emptySet(),
                createdAt = Instant.now(),
                userHandle = randomHandle,
            )
        )

        val found = adapter.findByUserHandle(randomHandle)
        assertNotNull(found)
        assertEquals(testUserId, found.userId)
    }

    @Test fun `findByUserHandle returns null for an unknown handle`() {
        assertNull(adapter.findByUserHandle("no-such-handle".toByteArray()))
    }

    private fun passkeyCredential(credentialId: ByteArray, publicKey: ECPublicKey) = PasskeyCredential(
        credentialId = credentialId,
        userId = AuthUserId("1"),
        publicKeyCose = objectConverter.cborConverter.writeValueAsBytes(EC2COSEKey.create(publicKey, COSEAlgorithmIdentifier.ES256)),
        signatureCount = 0,
        transports = emptySet(),
        createdAt = Instant.now(),
        userHandle = "1".toByteArray(),
    )
}

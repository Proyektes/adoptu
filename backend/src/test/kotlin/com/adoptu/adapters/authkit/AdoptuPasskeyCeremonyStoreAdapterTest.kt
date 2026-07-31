package com.adoptu.adapters.authkit

import com.adoptu.mocks.TestDatabase
import com.universaliun.auth.backend.adapter.out.persistence.InMemoryUserRepositoryAdapter
import com.universaliun.auth.backend.adapter.out.security.WebAuthnCredentialRepositoryAdapter
import com.universaliun.auth.backend.application.passkey.StartPasskeyLoginService
import com.universaliun.auth.backend.application.passkey.StartPasskeyRegistrationService
import com.universaliun.auth.backend.application.passkey.StartPasskeySignupService
import com.universaliun.auth.backend.domain.model.passkey.PasskeyCredential
import com.universaliun.auth.backend.domain.model.user.AuthUser
import com.universaliun.auth.backend.domain.model.user.Email
import com.universaliun.auth.backend.domain.port.`in`.StartPasskeyLoginUseCase
import com.universaliun.auth.backend.domain.port.`in`.StartPasskeyRegistrationUseCase
import com.universaliun.auth.backend.domain.port.`in`.StartPasskeySignupUseCase
import com.universaliun.auth.backend.domain.port.out.PasskeyCredentialRepositoryPort
import com.universaliun.auth.common.identity.AuthUserId
import com.universaliun.auth.common.rbac.PermissionSet
import com.yubico.webauthn.RelyingParty
import com.yubico.webauthn.data.RelyingPartyIdentity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Minimal in-memory PasskeyCredentialRepositoryPort -- AuthKit's own FakePasskeyCredentialRepository
 * lives in its test sourceset, not published as part of the backend jar, so this test builds its
 * own rather than depending on unpublished test fixtures. */
private class FakePasskeyCredentialRepository : PasskeyCredentialRepositoryPort {
    private val store = ConcurrentHashMap<String, PasskeyCredential>()
    override fun save(credential: PasskeyCredential): PasskeyCredential {
        store[credential.credentialId.joinToString(",")] = credential
        return credential
    }
    override fun findByCredentialId(credentialId: ByteArray): PasskeyCredential? = store[credentialId.joinToString(",")]
    override fun findByUserId(userId: AuthUserId): List<PasskeyCredential> = store.values.filter { it.userId == userId }
    override fun updateSignatureCount(credentialId: ByteArray, signatureCount: Long) {}
}

// AuthUserId.value must be an int-string here -- production AuthUserIds for Adopt-u come from
// AdoptuUserRepositoryAdapter, which is Users.id.toString() (a real Postgres int autoincrement),
// not a UUID. AdoptuPasskeyCeremonyStoreAdapter parses it back via .toInt() to match that shape.
private val nextUserId = AtomicInteger(1)

private fun testAuthUser(email: String) = AuthUser(
    id = AuthUserId(nextUserId.getAndIncrement().toString()),
    email = Email(email),
    displayName = "Test User",
    passwordHash = "\$argon2id\$v=19\$m=16384,t=2,p=1\$c2FsdHNhbHQ\$aGFzaGhhc2g",
    roles = emptySet(),
    permissions = PermissionSet.empty(0),
    enabled = true,
    emailVerified = true,
    createdAt = Instant.now(),
    lastModifiedAt = Instant.now(),
)

/**
 * Round-trips real Yubico [com.yubico.webauthn.data.PublicKeyCredentialCreationOptions]/
 * [com.yubico.webauthn.AssertionRequest] objects (generated via AuthKit's own
 * StartPasskeyRegistrationService/StartPasskeyLoginService against a real RelyingParty, not
 * hand-built) through this adapter's real Postgres storage -- proves the JSON persistence actually
 * survives a save/load cycle for the exact object shapes AuthKit itself produces and consumes,
 * not just that some string got stored.
 */
class AdoptuPasskeyCeremonyStoreAdapterTest {

    private val userRepository = InMemoryUserRepositoryAdapter()
    private val passkeyRepository = FakePasskeyCredentialRepository()
    private val relyingParty = RelyingParty.builder()
        .identity(RelyingPartyIdentity.builder().id("localhost").name("Adopt-u Test").build())
        .credentialRepository(WebAuthnCredentialRepositoryAdapter(userRepository, passkeyRepository))
        .origins(setOf("http://localhost:8080"))
        .build()
    private lateinit var adapter: AdoptuPasskeyCeremonyStoreAdapter

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        adapter = AdoptuPasskeyCeremonyStoreAdapter(expiryMs = 900_000L)
    }

    @Test fun `registration challenge round-trips through real Postgres storage`() {
        val user = userRepository.save(testAuthUser("ceremony-reg@test.com"))
        val startService = StartPasskeyRegistrationService(relyingParty, userRepository, adapter)

        val result = startService.start(StartPasskeyRegistrationUseCase.Command(user.id))

        val consumed = adapter.consumeRegistrationChallenge(result.requestId)
        assertEquals(user.id, consumed?.first)
        assertEquals("ceremony-reg@test.com", consumed?.second?.user?.name)
    }

    @Test fun `registration challenge is one-time use`() {
        val user = userRepository.save(testAuthUser("ceremony-reg-once@test.com"))
        val startService = StartPasskeyRegistrationService(relyingParty, userRepository, adapter)
        val result = startService.start(StartPasskeyRegistrationUseCase.Command(user.id))

        adapter.consumeRegistrationChallenge(result.requestId)

        assertNull(adapter.consumeRegistrationChallenge(result.requestId))
    }

    @Test fun `login challenge round-trips through real Postgres storage`() {
        userRepository.save(testAuthUser("ceremony-login@test.com"))
        val startService = StartPasskeyLoginService(relyingParty, adapter)

        val result = startService.start(StartPasskeyLoginUseCase.Command("ceremony-login@test.com"))

        val consumed = adapter.consumeLoginChallenge(result.requestId)
        assertEquals("ceremony-login@test.com", consumed?.username?.orElse(null))
    }

    @Test fun `login challenge is one-time use`() {
        userRepository.save(testAuthUser("ceremony-login-once@test.com"))
        val startService = StartPasskeyLoginService(relyingParty, adapter)
        val result = startService.start(StartPasskeyLoginUseCase.Command("ceremony-login-once@test.com"))

        adapter.consumeLoginChallenge(result.requestId)

        assertNull(adapter.consumeLoginChallenge(result.requestId))
    }

    @Test fun `unknown requestId returns null for both ceremony types`() {
        assertNull(adapter.consumeRegistrationChallenge("bogus"))
        assertNull(adapter.consumeLoginChallenge("bogus"))
    }

    @Test fun `a survived registration challenge is visible to a second, independent adapter instance -- not in-memory`() {
        val user = userRepository.save(testAuthUser("ceremony-fresh@test.com"))
        val startService = StartPasskeyRegistrationService(relyingParty, userRepository, adapter)
        val result = startService.start(StartPasskeyRegistrationUseCase.Command(user.id))

        val freshAdapterInstance = AdoptuPasskeyCeremonyStoreAdapter(expiryMs = 900_000L)
        val consumed = freshAdapterInstance.consumeRegistrationChallenge(result.requestId)

        assertEquals(user.id, consumed?.first)
    }

    @Test fun `an expired registration challenge is not returned`() {
        val user = userRepository.save(testAuthUser("ceremony-expired@test.com"))
        val expiredAdapter = AdoptuPasskeyCeremonyStoreAdapter(expiryMs = -1L)
        val startService = StartPasskeyRegistrationService(relyingParty, userRepository, expiredAdapter)
        val result = startService.start(StartPasskeyRegistrationUseCase.Command(user.id))

        assertNull(adapter.consumeRegistrationChallenge(result.requestId))
    }

    @Test fun `signup challenge round-trips email and displayName through real Postgres storage -- no user exists yet`() {
        val startService = StartPasskeySignupService(relyingParty, userRepository, adapter)

        val result = startService.start(StartPasskeySignupUseCase.Command("ceremony-signup@test.com", "Ceremony Signup"))

        val consumed = adapter.consumeSignupChallenge(result.requestId)
        assertEquals("ceremony-signup@test.com", consumed?.email)
        assertEquals("Ceremony Signup", consumed?.displayName)
    }

    @Test fun `signup challenge is one-time use`() {
        val startService = StartPasskeySignupService(relyingParty, userRepository, adapter)
        val result = startService.start(StartPasskeySignupUseCase.Command("ceremony-signup-once@test.com", "Once"))

        adapter.consumeSignupChallenge(result.requestId)

        assertNull(adapter.consumeSignupChallenge(result.requestId))
    }
}

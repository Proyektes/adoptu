package com.adoptu.services

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.WebAuthnCredentials
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.UserRole
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import com.adoptu.services.auth.WebAuthnService
import com.adoptu.services.crypto.CryptoService
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.SecureRandom
import kotlin.test.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import com.webauthn4j.data.PublicKeyCredentialCreationOptions
import com.webauthn4j.data.PublicKeyCredentialRequestOptions
import com.webauthn4j.converter.AttestationObjectConverter
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.AuthenticatorDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.AuthenticatorAssertionResponse
import com.webauthn4j.data.AuthenticatorAttestationResponse
import com.webauthn4j.data.PublicKeyCredential
import com.webauthn4j.data.attestation.AttestationObject
import com.webauthn4j.data.attestation.authenticator.AAGUID
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData
import com.webauthn4j.data.attestation.authenticator.AuthenticatorData
import com.webauthn4j.data.attestation.authenticator.EC2COSEKey
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement
import com.webauthn4j.data.client.ClientDataType
import com.webauthn4j.data.client.CollectedClientData
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionAuthenticatorOutput
import com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput
import com.webauthn4j.data.extension.client.AuthenticationExtensionClientOutput
import com.webauthn4j.data.extension.client.AuthenticationExtensionsClientOutputs
import com.webauthn4j.data.extension.client.RegistrationExtensionClientOutput
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Builds genuine (self-signed, attestation "none") WebAuthn4j registration/authentication
 * ceremony payloads so tests can exercise WebAuthnService's real parse+verify code paths
 * (verifyAndRegister / registerAdditionalPasskey / verifyAndAuthenticate) end-to-end, instead
 * of only their early-return branches. This mirrors exactly what WebAuthnService itself does
 * with the webauthn4j-core APIs (no test-only helper artifact like webauthn4j-test is on the
 * classpath), so it is safe to construct these objects directly with real EC keys.
 */
private object WebAuthnCeremony {
    private val objectConverter = ObjectConverter()
    private val attestationObjectConverter = AttestationObjectConverter(objectConverter)
    private val authenticatorDataConverter = AuthenticatorDataConverter(objectConverter)
    val attestedCredentialDataConverter = AttestedCredentialDataConverter(objectConverter)

    data class KeyMaterial(val keyPair: KeyPair, val coseKey: EC2COSEKey, val credentialId: ByteArray)

    fun generateKeyMaterial(): KeyMaterial {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGenerator.generateKeyPair()
        val coseKey = EC2COSEKey.create(keyPair.public as ECPublicKey, COSEAlgorithmIdentifier.ES256)
        val credentialId = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return KeyMaterial(keyPair, coseKey, credentialId)
    }

    fun credentialIdBase64Url(credentialId: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(credentialId)

    private fun rpIdHash(rpId: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray(Charsets.UTF_8))

    private fun clientDataBytes(type: ClientDataType, origin: String, challenge: ByteArray): ByteArray {
        val clientData = CollectedClientData(type, DefaultChallenge(challenge), Origin(origin), false, null, null)
        return objectConverter.jsonMapper.writeValueAsBytes(clientData)
    }

    /** Builds a registration response JSON identical in shape to what a real browser/authenticator would send. */
    fun buildRegistrationResponseJson(rpId: String, origin: String, challenge: ByteArray, keyMaterial: KeyMaterial): String {
        val attestedCredentialData = AttestedCredentialData(AAGUID.ZERO, keyMaterial.credentialId, keyMaterial.coseKey)
        val flags = (AuthenticatorData.BIT_UP.toInt() or AuthenticatorData.BIT_UV.toInt() or AuthenticatorData.BIT_AT.toInt()).toByte()
        val authenticatorData = AuthenticatorData<RegistrationExtensionAuthenticatorOutput>(rpIdHash(rpId), flags, 0L, attestedCredentialData)
        val attestationObject = AttestationObject(authenticatorData, NoneAttestationStatement())
        val attestationObjectBytes = attestationObjectConverter.convertToBytes(attestationObject)

        val response = AuthenticatorAttestationResponse(
            clientDataBytes(ClientDataType.WEBAUTHN_CREATE, origin, challenge),
            attestationObjectBytes,
            emptySet()
        )
        val publicKeyCredential = PublicKeyCredential<AuthenticatorAttestationResponse, RegistrationExtensionClientOutput>(
            keyMaterial.credentialId, response, null, AuthenticationExtensionsClientOutputs()
        )
        return objectConverter.jsonMapper.writeValueAsString(publicKeyCredential)
    }

    /** Builds an authentication (assertion) response JSON, genuinely signed with the authenticator's private key. */
    fun buildAuthenticationResponseJson(
        rpId: String,
        origin: String,
        challenge: ByteArray,
        credentialId: ByteArray,
        privateKey: java.security.PrivateKey,
        signCount: Long = 0L,
        userVerified: Boolean = true
    ): String {
        var flags = AuthenticatorData.BIT_UP
        if (userVerified) flags = (flags.toInt() or AuthenticatorData.BIT_UV.toInt()).toByte()
        val authenticatorData = AuthenticatorData<AuthenticationExtensionAuthenticatorOutput>(rpIdHash(rpId), flags, signCount)
        val authenticatorDataBytes = authenticatorDataConverter.convert(authenticatorData)

        val clientDataJSON = clientDataBytes(ClientDataType.WEBAUTHN_GET, origin, challenge)
        val clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientDataJSON)
        val signedData = authenticatorDataBytes + clientDataHash

        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(signedData)
        }.sign()

        val response = AuthenticatorAssertionResponse(clientDataJSON, authenticatorDataBytes, signature, null)
        val publicKeyCredential = PublicKeyCredential<AuthenticatorAssertionResponse, AuthenticationExtensionClientOutput>(
            credentialId, response, null, AuthenticationExtensionsClientOutputs()
        )
        return objectConverter.jsonMapper.writeValueAsString(publicKeyCredential)
    }
}

@OptIn(ExperimentalTime::class)
class WebAuthnServiceTest {

    private val clock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))
    private lateinit var userRepository: UserRepository
    private lateinit var passwordService: PasswordService
    private lateinit var emailVerificationService: EmailVerificationService
    private lateinit var magicLinkService: MagicLinkService
    private lateinit var webAuthnService: WebAuthnService
    private lateinit var mockNotificationAdapter: MockNotificationAdapter
    private val adminEmail = "admin@test.com"
    private val rpId = "localhost"
    private val rpName = "Adopt-U Test"
    private val origins = listOf("http://localhost:80")

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        
        userRepository = UserRepository(clock)
        mockNotificationAdapter = MockNotificationAdapter()
        passwordService = PasswordService(userRepository, mockNotificationAdapter, clock, "http://localhost:80")
        emailVerificationService = EmailVerificationService(userRepository, mockNotificationAdapter, clock, "http://localhost:80")
        magicLinkService = MagicLinkService(userRepository, mockNotificationAdapter, clock, "http://localhost:80", emailVerificationService)
        webAuthnService = WebAuthnService(
            clock,
            emailVerificationService,
            userService(),
            passwordService,
            magicLinkService,
            adminEmail,
            rpId,
            rpName,
            origins
        )
        CryptoService.initialize()
    }

    private fun userService(): UserService =
        UserService(userRepository, PhotographerRepositoryImpl(PetRepositoryImpl(clock), userRepository, clock))

    @Test
    fun `hasPasskey returns false when user has no credentials`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        assertFalse(webAuthnService.hasPasskey(userId))
    }

    @Test
    fun `hasPasskey returns true when user has credentials`() = runBlocking {
        val userId = createTestUser("test@example.com", "Test User")
        createTestCredential(userId)
        assertTrue(webAuthnService.hasPasskey(userId))
    }

    @Test
    fun `hasPasskey returns false for non-existent user`() = runBlocking {
        assertFalse(webAuthnService.hasPasskey(99999))
    }

    @Test
    fun `registerWithPassword creates new user with password`() = runBlocking {
        val email = "newuser@example.com"
        val displayName = "New User"
        val roles = setOf(UserRole.ADOPTER)
        val encryptedPassword = encryptPassword("SecurePassword123!")

        val result = webAuthnService.registerWithPassword(email, displayName, roles, encryptedPassword)

        assertNotNull(result)
        assertTrue(result.userId > 0)
        
        val user = transaction {
            Users.selectAll().where { Users.username eq email }.firstOrNull()
        }
        assertNotNull(user)
        assertEquals(displayName, user[Users.displayName])
        
        assertTrue(passwordService.hasPassword(result.userId))
    }

    @Test
    fun `registerWithPassword assigns roles to new user`() = runBlocking {
        val email = "roles@example.com"
        val displayName = "Role User"
        val roles = setOf(UserRole.ADOPTER, UserRole.RESCUER)
        val encryptedPassword = encryptPassword("SecurePassword123!")

        val result = webAuthnService.registerWithPassword(email, displayName, roles, encryptedPassword)

        assertNotNull(result)
        
        val userRoles = transaction {
            UserActiveRoles.selectAll()
                .where { UserActiveRoles.userId eq result.userId }
                .map { it[UserActiveRoles.role] }
                .toSet()
        }
        
        // RESCUER requires email verification before activation (see
        // ROLES_REQUIRING_VERIFICATION_BEFORE_ACTIVATION in WebAuthnService) - only
        // ADOPTER is active immediately after registration.
        assertTrue(userRoles.contains("ADOPTER"))
        assertFalse(userRoles.contains("RESCUER"))

        // The selected-but-gated role is recorded as pending, not silently dropped - it
        // gets granted automatically once the user verifies their email (see UserService).
        val pendingRoles = userRepository.consumePendingRoleActivations(result.userId)
        assertEquals(setOf(UserRole.RESCUER), pendingRoles)
    }

    @Test
    fun `registerWithPassword then verifying email auto-grants the pending rescuer role`() = runBlocking {
        val email = "autogrant@example.com"
        val displayName = "Auto Grant User"
        val roles = setOf(UserRole.ADOPTER, UserRole.RESCUER)
        val encryptedPassword = encryptPassword("SecurePassword123!")

        val result = webAuthnService.registerWithPassword(email, displayName, roles, encryptedPassword)
        assertNotNull(result)

        val token = transaction {
            com.adoptu.adapters.db.EmailVerificationTokens.selectAll()
                .where { com.adoptu.adapters.db.EmailVerificationTokens.userId eq result.userId }
                .first()[com.adoptu.adapters.db.EmailVerificationTokens.token]
        }

        val verified = userService().verifyToken(token)
        assertTrue(verified)

        val userRoles = transaction {
            UserActiveRoles.selectAll()
                .where { UserActiveRoles.userId eq result.userId }
                .map { it[UserActiveRoles.role] }
                .toSet()
        }
        assertTrue(userRoles.contains("RESCUER"))
    }

    @Test
    fun `registerWithPassword sends verification email`() = runBlocking {
        val email = "email@example.com"
        val displayName = "Email User"
        val roles = setOf(UserRole.ADOPTER)
        val encryptedPassword = encryptPassword("SecurePassword123!")

        webAuthnService.registerWithPassword(email, displayName, roles, encryptedPassword)

        val sentEmails = mockNotificationAdapter.getSentEmails()
        assertEquals(1, sentEmails.size)
        assertTrue(sentEmails.first().to.contains(email))
    }

    @Test
    fun `registerWithPassword fails with invalid password`() = runBlocking {
        val email = "invalid@example.com"
        val displayName = "Invalid User"
        val roles = setOf(UserRole.ADOPTER)
        val encryptedPassword = encryptPassword("weak") 

        val result = webAuthnService.registerWithPassword(email, displayName, roles, encryptedPassword)
        assertNull(result)
    }

    @Test
    fun `registerWithPassword returns null for tampered encrypted data`() = runBlocking {
        val email = "tampered@example.com"
        val displayName = "Tampered User"
        val roles = setOf(UserRole.ADOPTER)

        val result = webAuthnService.registerWithPassword(email, displayName, roles, "tampered-data")
        assertNull(result)
    }

    @Test
    fun `registerWithPassword grants ADMIN role for admin email`() = runBlocking {
        val email = "admin@test.com"
        val displayName = "Admin User"
        val roles = setOf(UserRole.ADOPTER)
        val encryptedPassword = encryptPassword("AdminPassword123!")

        val result = webAuthnService.registerWithPassword(email, displayName, roles, encryptedPassword)

        assertNotNull(result)
        
        val userRoles = transaction {
            UserActiveRoles.selectAll()
                .where { UserActiveRoles.userId eq result.userId }
                .map { it[UserActiveRoles.role] }
                .toSet()
        }
        
        assertTrue(userRoles.contains("ADMIN"))
    }

    @Test
    fun `registerWithPassword returns emailSent true when email sent`() = runBlocking {
        val email = "emailsent@example.com"
        val displayName = "Email Sent User"
        val roles = setOf(UserRole.ADOPTER)
        val encryptedPassword = encryptPassword("SecurePassword123!")

        val result = webAuthnService.registerWithPassword(email, displayName, roles, encryptedPassword)

        assertNotNull(result)
        assertTrue(result.emailSent)
    }

    @Test
    fun `generateRegistrationOptionsForUser creates valid options`() {
        val userId = createTestUser("user@example.com", "Test User")

        val options = webAuthnService.generateRegistrationOptionsForUser(userId, "user@example.com", "Test User")

        assertNotNull(options)
        assertEquals(rpId, options.publicKey.rp.id)
        assertEquals(rpName, options.publicKey.rp.name)
        assertEquals("user@example.com", options.publicKey.user.name)
        assertEquals("Test User", options.publicKey.user.displayName)
        assertNotNull(options.publicKey.challenge)
        assertTrue(options.publicKey.pubKeyCredParams.isNotEmpty())
    }

    @Test
    fun `registerAdditionalPasskey returns false when no challenge stored`() = runBlocking {
        val userId = createTestUser("nopasskey@example.com", "No Passkey User")

        val result = webAuthnService.registerAdditionalPasskey(userId, "invalid-response")
        assertFalse(result)
    }

    @Test
    fun `registerAdditionalPasskey returns false for invalid registration response`() = runBlocking {
        val userId = createTestUser("invalid@example.com", "Invalid User")
        webAuthnService.generateRegistrationOptionsForUser(userId, "invalid@example.com", "Invalid User")

        val result = webAuthnService.registerAdditionalPasskey(userId, "completely-invalid-json")
        assertFalse(result)
    }

    @Test
    fun `registerAdditionalPasskey creates credential for valid response`() {
        val userId = createTestUser("additional@example.com", "Additional User")
        val email = "additional@example.com"
        val displayName = "Additional User"
        
        webAuthnService.generateRegistrationOptionsForUser(userId, email, displayName)
        
        val initialCredentialCount = transaction {
            WebAuthnCredentials.selectAll()
                .where { WebAuthnCredentials.userId eq userId }
                .count()
        }
        
        assertEquals(0, initialCredentialCount)
    }

    @Test
    fun `registerAdditionalPasskey inserts a real credential row for a genuinely signed response`() = runBlocking {
        val email = "additional-real@example.com"
        val displayName = "Additional Real User"
        val userId = createTestUser(email, displayName)

        val options = webAuthnService.generateRegistrationOptionsForUser(userId, email, displayName)
        val challenge = Base64.getUrlDecoder().decode(options.publicKey.challenge)
        val keyMaterial = WebAuthnCeremony.generateKeyMaterial()
        val responseJson = WebAuthnCeremony.buildRegistrationResponseJson(rpId, origins.first(), challenge, keyMaterial)

        val result = webAuthnService.registerAdditionalPasskey(userId, responseJson)

        assertTrue(result)
        val credentialRow = transaction {
            WebAuthnCredentials.selectAll().where { WebAuthnCredentials.userId eq userId }.firstOrNull()
        }
        assertNotNull(credentialRow)
        assertEquals(
            WebAuthnCeremony.credentialIdBase64Url(keyMaterial.credentialId),
            credentialRow[WebAuthnCredentials.credentialId]
        )
    }

    @Test
    fun `verifyAndRegister returns null when no challenge was stored`() = runBlocking {
        val result = webAuthnService.verifyAndRegister(
            "no-challenge@example.com", "No Challenge User", setOf(UserRole.ADOPTER), "irrelevant-response"
        )
        assertNull(result)
    }

    @Test
    fun `verifyAndRegister returns null for an unparseable registration response`() = runBlocking {
        val email = "badresponse@example.com"
        webAuthnService.generateRegistrationOptions(email, "Bad Response User")

        val result = webAuthnService.verifyAndRegister(
            email, "Bad Response User", setOf(UserRole.ADOPTER), "completely-invalid-json"
        )
        assertNull(result)
    }

    @Test
    fun `verifyAndRegister creates a new user, credential, and active roles for a genuinely signed response`() = runBlocking {
        val email = "verified-new@example.com"
        val displayName = "Verified New User"
        val options = webAuthnService.generateRegistrationOptions(email, displayName)
        val challenge = Base64.getUrlDecoder().decode(options.publicKey.challenge)
        val keyMaterial = WebAuthnCeremony.generateKeyMaterial()
        val responseJson = WebAuthnCeremony.buildRegistrationResponseJson(rpId, origins.first(), challenge, keyMaterial)

        val result = webAuthnService.verifyAndRegister(email, displayName, setOf(UserRole.ADOPTER), responseJson)

        assertNotNull(result)
        assertTrue(result.userId > 0)
        assertTrue(result.emailSent)

        val user = transaction { Users.selectAll().where { Users.username eq email }.firstOrNull() }
        assertNotNull(user)
        assertEquals(displayName, user[Users.displayName])

        val credentialRow = transaction {
            WebAuthnCredentials.selectAll().where { WebAuthnCredentials.userId eq result.userId }.firstOrNull()
        }
        assertNotNull(credentialRow)

        val userRoles = transaction {
            UserActiveRoles.selectAll().where { UserActiveRoles.userId eq result.userId }.map { it[UserActiveRoles.role] }.toSet()
        }
        assertTrue(userRoles.contains("ADOPTER"))
    }

    @Test
    fun `verifyAndRegister records a gated role as pending instead of granting it immediately`() = runBlocking {
        val email = "pending-passkey@example.com"
        val displayName = "Pending Passkey User"
        val options = webAuthnService.generateRegistrationOptions(email, displayName)
        val challenge = Base64.getUrlDecoder().decode(options.publicKey.challenge)
        val keyMaterial = WebAuthnCeremony.generateKeyMaterial()
        val responseJson = WebAuthnCeremony.buildRegistrationResponseJson(rpId, origins.first(), challenge, keyMaterial)

        val result = webAuthnService.verifyAndRegister(email, displayName, setOf(UserRole.ADOPTER, UserRole.RESCUER), responseJson)

        assertNotNull(result)
        val userRoles = transaction {
            UserActiveRoles.selectAll().where { UserActiveRoles.userId eq result.userId }.map { it[UserActiveRoles.role] }.toSet()
        }
        assertFalse(userRoles.contains("RESCUER"))

        val pendingRoles = userRepository.consumePendingRoleActivations(result.userId)
        assertEquals(setOf(UserRole.RESCUER), pendingRoles)
    }

    @Test
    fun `verifyAndRegister grants ADMIN role for the configured admin email`() = runBlocking {
        val email = adminEmail
        val displayName = "Admin Via Passkey"
        val options = webAuthnService.generateRegistrationOptions(email, displayName)
        val challenge = Base64.getUrlDecoder().decode(options.publicKey.challenge)
        val keyMaterial = WebAuthnCeremony.generateKeyMaterial()
        val responseJson = WebAuthnCeremony.buildRegistrationResponseJson(rpId, origins.first(), challenge, keyMaterial)

        val result = webAuthnService.verifyAndRegister(email, displayName, setOf(UserRole.ADOPTER), responseJson)

        assertNotNull(result)
        val userRoles = transaction {
            UserActiveRoles.selectAll().where { UserActiveRoles.userId eq result.userId }.map { it[UserActiveRoles.role] }.toSet()
        }
        assertTrue(userRoles.contains("ADMIN"))
    }

    @Test
    fun `verifyAndRegister on an existing password user adds the credential without duplicating roles`() = runBlocking {
        val email = "existing-passkey@example.com"
        val displayName = "Existing Passkey User"
        val encryptedPassword = encryptPassword("SecurePassword123!")
        val passwordResult = webAuthnService.registerWithPassword(email, displayName, setOf(UserRole.ADOPTER), encryptedPassword)
        assertNotNull(passwordResult)

        val options = webAuthnService.generateRegistrationOptions(email, displayName)
        val challenge = Base64.getUrlDecoder().decode(options.publicKey.challenge)
        val keyMaterial = WebAuthnCeremony.generateKeyMaterial()
        val responseJson = WebAuthnCeremony.buildRegistrationResponseJson(rpId, origins.first(), challenge, keyMaterial)

        val result = webAuthnService.verifyAndRegister(email, displayName, setOf(UserRole.ADOPTER), responseJson)

        assertNotNull(result)
        assertEquals(passwordResult.userId, result.userId)

        val credentialCount = transaction {
            WebAuthnCredentials.selectAll().where { WebAuthnCredentials.userId eq result.userId }.count()
        }
        assertEquals(1, credentialCount)
    }

    @Test
    fun `verifyAndRegister is idempotent when a credential already exists for the user`() = runBlocking {
        val email = "double-register@example.com"
        val displayName = "Double Register User"

        val firstOptions = webAuthnService.generateRegistrationOptions(email, displayName)
        val firstChallenge = Base64.getUrlDecoder().decode(firstOptions.publicKey.challenge)
        val firstKeyMaterial = WebAuthnCeremony.generateKeyMaterial()
        val firstResponseJson = WebAuthnCeremony.buildRegistrationResponseJson(rpId, origins.first(), firstChallenge, firstKeyMaterial)
        val firstResult = webAuthnService.verifyAndRegister(email, displayName, setOf(UserRole.ADOPTER), firstResponseJson)
        assertNotNull(firstResult)

        val secondOptions = webAuthnService.generateRegistrationOptions(email, displayName)
        val secondChallenge = Base64.getUrlDecoder().decode(secondOptions.publicKey.challenge)
        val secondKeyMaterial = WebAuthnCeremony.generateKeyMaterial()
        val secondResponseJson = WebAuthnCeremony.buildRegistrationResponseJson(rpId, origins.first(), secondChallenge, secondKeyMaterial)
        val secondResult = webAuthnService.verifyAndRegister(email, displayName, setOf(UserRole.ADOPTER), secondResponseJson)

        assertNotNull(secondResult)
        assertEquals(firstResult.userId, secondResult.userId)

        val credentialCount = transaction {
            WebAuthnCredentials.selectAll().where { WebAuthnCredentials.userId eq secondResult.userId }.count()
        }
        assertEquals(1, credentialCount)
    }

    @Test
    fun `verifyAndAuthenticate returns null for an unparseable authentication response`() = runBlocking {
        assertNull(webAuthnService.verifyAndAuthenticate("not-json-at-all"))
    }

    @Test
    fun `verifyAndAuthenticate returns null when the credential is not registered`() = runBlocking {
        webAuthnService.generateAssertionOptions()
        val keyMaterial = WebAuthnCeremony.generateKeyMaterial()
        val json = WebAuthnCeremony.buildAuthenticationResponseJson(
            rpId, origins.first(), ByteArray(32), keyMaterial.credentialId, keyMaterial.keyPair.private
        )

        assertNull(webAuthnService.verifyAndAuthenticate(json))
    }

    @Test
    fun `verifyAndAuthenticate returns null when no assertion challenge was stored`() = runBlocking {
        val email = "no-assertion-challenge@example.com"
        val registration = registerRealPasskeyUser(email, "No Assertion Challenge User", setOf(UserRole.ADOPTER))

        // Deliberately skip generateAssertionOptions() so ChallengeStore has nothing stored.
        val json = WebAuthnCeremony.buildAuthenticationResponseJson(
            rpId, origins.first(), ByteArray(32), registration.keyMaterial.credentialId, registration.keyMaterial.keyPair.private
        )

        assertNull(webAuthnService.verifyAndAuthenticate(json))
    }

    @Test
    fun `verifyAndAuthenticate returns null for a tampered signature`() = runBlocking {
        val email = "tampered-signature@example.com"
        val registration = registerRealPasskeyUser(email, "Tampered Signature User", setOf(UserRole.ADOPTER))

        val assertionOptions = webAuthnService.generateAssertionOptions()
        val challenge = Base64.getUrlDecoder().decode(assertionOptions.challenge)
        val wrongKeyMaterial = WebAuthnCeremony.generateKeyMaterial()
        val json = WebAuthnCeremony.buildAuthenticationResponseJson(
            rpId, origins.first(), challenge, registration.keyMaterial.credentialId, wrongKeyMaterial.keyPair.private
        )

        assertNull(webAuthnService.verifyAndAuthenticate(json))
    }

    @Test
    fun `verifyAndAuthenticate succeeds and updates the stored sign count`() = runBlocking {
        val email = "verified-auth@example.com"
        val registration = registerRealPasskeyUser(email, "Verified Auth User", setOf(UserRole.ADOPTER))

        val assertionOptions = webAuthnService.generateAssertionOptions()
        val challenge = Base64.getUrlDecoder().decode(assertionOptions.challenge)
        val json = WebAuthnCeremony.buildAuthenticationResponseJson(
            rpId, origins.first(), challenge, registration.keyMaterial.credentialId, registration.keyMaterial.keyPair.private, signCount = 1L
        )

        val result = webAuthnService.verifyAndAuthenticate(json)

        assertNotNull(result)
        assertEquals(registration.userId, result.userId)
        assertEquals(email, result.user.username)
        assertEquals("ADOPTER", result.user.role)

        val updatedRow = transaction {
            WebAuthnCredentials.selectAll().where { WebAuthnCredentials.userId eq registration.userId }.first()
        }
        assertEquals(1L, updatedRow[WebAuthnCredentials.signCount])
    }

    @Test
    fun `verifyAndAuthenticate reports ADMIN as the primary role when the user has it`() = runBlocking {
        val registration = registerRealPasskeyUser(adminEmail, "Admin Auth User", setOf(UserRole.ADOPTER))

        val assertionOptions = webAuthnService.generateAssertionOptions()
        val challenge = Base64.getUrlDecoder().decode(assertionOptions.challenge)
        val json = WebAuthnCeremony.buildAuthenticationResponseJson(
            rpId, origins.first(), challenge, registration.keyMaterial.credentialId, registration.keyMaterial.keyPair.private
        )

        val result = webAuthnService.verifyAndAuthenticate(json)

        assertNotNull(result)
        assertEquals("ADMIN", result.user.role)
    }

    /** Registers a brand-new user with a genuine passkey via the real verifyAndRegister ceremony, for authentication tests. */
    private data class RealPasskeyRegistration(val userId: Int, val keyMaterial: WebAuthnCeremony.KeyMaterial)

    private suspend fun registerRealPasskeyUser(email: String, displayName: String, roles: Set<UserRole>): RealPasskeyRegistration {
        val options = webAuthnService.generateRegistrationOptions(email, displayName)
        val challenge = Base64.getUrlDecoder().decode(options.publicKey.challenge)
        val keyMaterial = WebAuthnCeremony.generateKeyMaterial()
        val responseJson = WebAuthnCeremony.buildRegistrationResponseJson(rpId, origins.first(), challenge, keyMaterial)
        val result = webAuthnService.verifyAndRegister(email, displayName, roles, responseJson)
        assertNotNull(result)
        return RealPasskeyRegistration(result.userId, keyMaterial)
    }

    private fun encryptPassword(password: String): String {
        val publicKey = CryptoService.getPublicKey()
        return CryptoService.encrypt(password, publicKey) 
            ?: throw IllegalStateException("Encryption failed")
    }

    private fun createTestUser(username: String, displayName: String): Int {
        return transaction {
            Users.insert {
                it[Users.username] = username
                it[Users.displayName] = displayName
                it[Users.language] = "en"
                it[Users.isEmailVerified] = true
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
            } get Users.id
        }
    }

    private fun createTestCredential(userId: Int): Int {
        val credentialId = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val aaguid = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val publicKey = ByteArray(65).also { SecureRandom().nextBytes(it) }
        
        return transaction {
            WebAuthnCredentials.insert {
                it[WebAuthnCredentials.userId] = userId
                it[WebAuthnCredentials.credentialId] = java.util.Base64.getEncoder().encodeToString(credentialId)
                it[WebAuthnCredentials.attestedCredentialDataBase64] = java.util.Base64.getEncoder().encodeToString(aaguid + publicKey)
                it[WebAuthnCredentials.signCount] = 0
                it[WebAuthnCredentials.transports] = null
                it[WebAuthnCredentials.createdAt] = clock.now().toEpochMilliseconds()
            } get WebAuthnCredentials.id
        }
    }
}

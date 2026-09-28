package com.adoptu.dto.output

import com.universaliun.formats.json.generated.decodeAsAuthMeResponse
import com.universaliun.formats.json.generated.decodeAsRegistrationResponse
import com.universaliun.formats.json.generated.decodeAsSuccessWithErrorResponse
import com.universaliun.formats.json.generated.decodeAsVerificationResponse
import com.universaliun.formats.json.generated.encodeToJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * These four response DTOs carry most of the constructor defaults Jackson's
 * `jackson-module-kotlin` (`registerKotlinModule()`, no explicit `JsonInclude`) applied when a
 * field was absent from a hand-built `mapOf(...)`-free response body. The generated decoder must
 * apply the identical defaults (see `docs/codegen-consumers.md` §2, "Defaults vs. nullability").
 */
class AuthResponsesTest {

    @Test
    fun `AuthMeResponse round-trips the authenticated shape`() {
        val original = AuthMeResponse(
            authenticated = true, id = 1, email = "user@example.com", displayName = "User",
            language = "es", country = "MX", activeRoles = listOf("RESCUER", "ADOPTER"),
            lastAcceptedPrivacyPolicy = 1_700_000_000_000, lastAcceptedTermsAndConditions = 1_700_000_001_000,
            emailVerified = true, isBanned = false, banReason = null,
        )
        assertEquals(original, original.encodeToJson().decodeAsAuthMeResponse())
    }

    @Test
    fun `AuthMeResponse decodes the unauthenticated shape to its declared defaults`() {
        val decoded = """{"authenticated":false}""".encodeToByteArray().decodeAsAuthMeResponse()
        assertEquals(AuthMeResponse(authenticated = false), decoded)
        assertEquals("en", decoded.language)
        assertEquals(emptyList(), decoded.activeRoles)
    }

    @Test
    fun `AuthMeResponse keeps every null field explicit on the wire`() {
        val json = AuthMeResponse(authenticated = true).encodeToJson().decodeToString()
        assertTrue(json.contains("\"id\":null"))
        assertTrue(json.contains("\"email\":null"))
        assertTrue(json.contains("\"country\":null"))
    }

    @Test
    fun `SuccessWithErrorResponse round-trips`() {
        val original = SuccessWithErrorResponse(success = false, error = "invalid credentials", needsProfileCompletion = true, email = "u@example.com")
        assertEquals(original, original.encodeToJson().decodeAsSuccessWithErrorResponse())
    }

    @Test
    fun `RegistrationResponse round-trips including its declared defaults`() {
        val decoded = """{"success":true}""".encodeToByteArray().decodeAsRegistrationResponse()
        assertEquals(RegistrationResponse(success = true), decoded)
        val original = RegistrationResponse(success = true, message = "check your email", emailVerificationSent = true)
        assertEquals(original, original.encodeToJson().decodeAsRegistrationResponse())
    }

    @Test
    fun `VerificationResponse round-trips`() {
        val original = VerificationResponse(success = true, message = "verified")
        assertEquals(original, original.encodeToJson().decodeAsVerificationResponse())
    }
}

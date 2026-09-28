package com.adoptu.dto.output

import com.universaliun.formats.json.JsonDecodable
import com.universaliun.formats.json.JsonEncodable

@JsonDecodable(strict = false)
@JsonEncodable
data class AuthMeResponse(
    val authenticated: Boolean,
    val id: Int? = null,
    val email: String? = null,
    val displayName: String? = null,
    val language: String = "en",
    val country: String? = null,
    val activeRoles: List<String> = emptyList(),
    val lastAcceptedPrivacyPolicy: Long? = null,
    val lastAcceptedTermsAndConditions: Long? = null,
    val emailVerified: Boolean = false,
    val isBanned: Boolean = false,
    val banReason: String? = null
)

@JsonDecodable(strict = false)
@JsonEncodable
data class SuccessWithErrorResponse(
    val success: Boolean,
    val error: String? = null,
    val needsProfileCompletion: Boolean = false,
    val email: String? = null
)

@JsonDecodable(strict = false)
@JsonEncodable
data class RegistrationResponse(
    val success: Boolean,
    val message: String? = null,
    val emailVerificationSent: Boolean = false
)

@JsonDecodable(strict = false)
@JsonEncodable
data class VerificationResponse(
    val success: Boolean,
    val message: String? = null
)

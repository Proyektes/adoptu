package com.adoptu.dto.output


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
    val banReason: String? = null,
    val photographerFee: Double? = null,
    val photographerCurrency: String? = null,
    val photographerCountry: String? = null,
    val photographerState: String? = null
)

data class SuccessWithErrorResponse(
    val success: Boolean,
    val error: String? = null,
    val needsProfileCompletion: Boolean = false,
    val email: String? = null
)

data class RegistrationResponse(
    val success: Boolean,
    val message: String? = null,
    val emailVerificationSent: Boolean = false
)

data class VerificationResponse(
    val success: Boolean,
    val message: String? = null
)

data class PhotographerProfileResponse(
    val id: Int,
    val username: String,
    val email: String,
    val displayName: String,
    val language: String,
    val activeRoles: Set<String>,
    val lastAcceptedPrivacyPolicy: Long?,
    val lastAcceptedTermsAndConditions: Long?,
    val photographerFee: Double?,
    val photographerCurrency: String?,
    val photographerCountry: String?,
    val photographerState: String?
)

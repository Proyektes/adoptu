package com.adoptu.dto.input


enum class UserRole {
    ADMIN, RESCUER, ADOPTER, PHOTOGRAPHER, TEMPORAL_HOME, SHELTER, STERILIZATION_SERVICE, URGENT_RESCUER
}

data class UserDto(
    val id: Int,
    val username: String,
    val email: String? = null,
    val displayName: String,
    val language: String = "en",
    val country: String? = null,
    val isEmailVerified: Boolean = false,
    val activeRoles: Set<UserRole> = emptySet(),
    val lastAcceptedPrivacyPolicy: Long? = null,
    val lastAcceptedTermsAndConditions: Long? = null,
    val isBanned: Boolean = false,
    val banReason: String? = null,
    val deactivatedAt: Long? = null,
    val deactivatedBy: Int? = null
)

data class BanUserRequest(
    val reason: String? = null
)

data class PhotographerDto(
    val userId: Int,
    val displayName: String,
    val username: String? = null,
    val photographerFee: Double? = null,
    val photographerCurrency: String? = null,
    val country: String? = null,
    val state: String? = null
)

data class AcceptTermsRequest(
    val acceptPrivacyPolicy: Boolean = false,
    val acceptTermsAndConditions: Boolean = false
)

data class PhotographerSettingsRequest(
    val photographerFee: Double,
    val photographerCurrency: String,
    val country: String? = null,
    val state: String? = null
)

data class PhotographyRequestDto(
    val id: Int,
    val photographerId: Int,
    val photographerName: String? = null,
    val requesterId: Int,
    val requesterName: String? = null,
    val petId: Int? = null,
    val petName: String? = null,
    val message: String? = null,
    val status: String,
    val scheduledDate: Long? = null,
    val createdAt: Long
)

data class CreatePhotographyRequestRequest(
    val photographerId: Int,
    val petId: Int? = null,
    val message: String? = null
)

data class UpdatePhotographyRequestRequest(
    val status: String? = null,
    val scheduledDate: Long? = null
)

data class CreateMultiPhotographerRequestRequest(
    val photographerIds: List<Int>,
    val petId: Int? = null,
    val message: String
)

data class RoleActivationRequest(
    val activate: Boolean
)

data class TemporalHomeDto(
    val userId: Int,
    val alias: String,
    val country: String,
    val state: String? = null,
    val city: String,
    val zip: String? = null,
    val neighborhood: String? = null,
    val maxCapacity: Int? = null,
    val createdAt: Long
)

data class TemporalHomeSearchParams(
    val country: String? = null,
    val state: String? = null,
    val city: String? = null,
    val zip: String? = null,
    val neighborhood: String? = null
)

data class CreateTemporalHomeRequest(
    val alias: String,
    val country: String,
    val state: String? = null,
    val city: String,
    val zip: String? = null,
    val neighborhood: String? = null,
    val streetAddress: String? = null,
    val phone: String? = null,
    val maxCapacity: Int? = null
)

data class UpdateTemporalHomeRequest(
    val alias: String? = null,
    val country: String? = null,
    val state: String? = null,
    val city: String? = null,
    val zip: String? = null,
    val neighborhood: String? = null,
    val streetAddress: String? = null,
    val phone: String? = null,
    val maxCapacity: Int? = null
)

data class SendTemporalHomeRequestRequest(
    val temporalHomeId: Int,
    val petId: Int? = null,
    val message: String
)

data class BlockRescuerRequest(
    val rescuerId: Int
)

data class TemporalHomeRequestDto(
    val id: Int,
    val temporalHomeId: Int,
    val temporalHomeAlias: String? = null,
    val rescuerId: Int,
    val rescuerName: String? = null,
    val petId: Int? = null,
    val petName: String? = null,
    val message: String,
    val status: String,
    val createdAt: Long
)

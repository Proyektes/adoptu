package com.adoptu.dto.input

import com.universaliun.formats.json.JsonDecodable
import com.universaliun.formats.json.JsonEncodable

enum class UserRole {
    ADMIN, RESCUER, ADOPTER, PHOTOGRAPHER, TEMPORAL_HOME, SHELTER, STERILIZATION_SERVICE, URGENT_RESCUER
}

@JsonDecodable(strict = false)
@JsonEncodable
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

@JsonDecodable(strict = false)
@JsonEncodable
data class BanUserRequest(
    val reason: String? = null
)

@JsonDecodable(strict = false)
@JsonEncodable
data class PhotographerDto(
    val userId: Int,
    val displayName: String,
    val username: String? = null,
    val photographerFee: Double? = null,
    val photographerCurrency: String? = null,
    val country: String? = null,
    val state: String? = null
)

@JsonDecodable(strict = false)
@JsonEncodable
data class AcceptTermsRequest(
    val acceptPrivacyPolicy: Boolean = false,
    val acceptTermsAndConditions: Boolean = false
)

@JsonDecodable(strict = false)
@JsonEncodable
data class PhotographerSettingsRequest(
    val photographerFee: Double,
    val photographerCurrency: String,
    val country: String? = null,
    val state: String? = null
)

@JsonDecodable(strict = false)
@JsonEncodable
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

@JsonDecodable(strict = false)
@JsonEncodable
data class CreatePhotographyRequestRequest(
    val photographerId: Int,
    val petId: Int? = null,
    val message: String? = null
)

@JsonDecodable(strict = false)
@JsonEncodable
data class UpdatePhotographyRequestRequest(
    val status: String? = null,
    val scheduledDate: Long? = null
)

@JsonDecodable(strict = false)
@JsonEncodable
data class CreateMultiPhotographerRequestRequest(
    val photographerIds: List<Int>,
    val petId: Int? = null,
    val message: String
)

@JsonDecodable(strict = false)
@JsonEncodable
data class RoleActivationRequest(
    val activate: Boolean
)

@JsonDecodable(strict = false)
@JsonEncodable
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

@JsonDecodable(strict = false)
@JsonEncodable
data class TemporalHomeSearchParams(
    val country: String? = null,
    val state: String? = null,
    val city: String? = null,
    val zip: String? = null,
    val neighborhood: String? = null
)

@JsonDecodable(strict = false)
@JsonEncodable
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

@JsonDecodable(strict = false)
@JsonEncodable
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

@JsonDecodable(strict = false)
@JsonEncodable
data class SendTemporalHomeRequestRequest(
    val temporalHomeId: Int,
    val petId: Int? = null,
    val message: String
)

@JsonDecodable(strict = false)
@JsonEncodable
data class BlockRescuerRequest(
    val rescuerId: Int
)

@JsonDecodable(strict = false)
@JsonEncodable
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

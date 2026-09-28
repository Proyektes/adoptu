package com.adoptu.dto.input

import com.universaliun.formats.json.JsonDecodable
import com.universaliun.formats.json.JsonEncodable

enum class HousingType {
    HOUSE, APARTMENT
}

enum class AdoptionExperience {
    FIRST_TIME, EXPERIENCED
}

@JsonDecodable(strict = false)
@JsonEncodable
data class AdoptionRequestDto(
    val id: Int,
    val petId: Int,
    val adopterId: Int,
    val message: String,
    val status: String,
    val housingType: HousingType? = null,
    val hasYard: Boolean? = null,
    val hasOtherPets: Boolean? = null,
    val experienceLevel: AdoptionExperience? = null,
    // Rescuer-private - see AdoptionRequests.reviewNote. Null whenever this DTO is being
    // returned to the adopter themselves.
    val reviewNote: String? = null,
    val createdAt: Long,
    val adopterName: String? = null,
    val adopterEmail: String? = null,
    val petName: String? = null,
    val petType: String? = null
)

@JsonDecodable(strict = false)
@JsonEncodable
data class CreateAdoptionRequestRequest(
    val message: String = "",
    val housingType: HousingType? = null,
    val hasYard: Boolean? = null,
    val hasOtherPets: Boolean? = null,
    val experienceLevel: AdoptionExperience? = null
)

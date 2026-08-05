package com.adoptu.dto.input


enum class HousingType {
    HOUSE, APARTMENT
}

enum class AdoptionExperience {
    FIRST_TIME, EXPERIENCED
}

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
    val createdAt: Long
)

data class CreateAdoptionRequestRequest(
    val message: String = "",
    val housingType: HousingType? = null,
    val hasYard: Boolean? = null,
    val hasOtherPets: Boolean? = null,
    val experienceLevel: AdoptionExperience? = null
)

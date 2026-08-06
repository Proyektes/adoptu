package com.adoptu.dto.input

enum class PetEditSuggestionStatus {
    PENDING, APPROVED, REJECTED
}

// Every field is nullable - null means "no change suggested to this field", not "clear it".
// Curated safe subset of Pets' fields (see PetEditSuggestions in Models.kt for why).
data class PetEditSuggestionDto(
    val id: Int,
    val petId: Int,
    val petName: String? = null,
    val volunteerId: Int,
    val volunteerName: String? = null,
    val description: String? = null,
    val temperament: String? = null,
    val energyLevel: String? = null,
    val specialNeeds: String? = null,
    val vaccinations: String? = null,
    val status: PetEditSuggestionStatus,
    val createdAt: Long,
    val reviewedAt: Long? = null
)

data class CreatePetEditSuggestionRequest(
    val description: String? = null,
    val temperament: String? = null,
    val energyLevel: String? = null,
    val specialNeeds: String? = null,
    val vaccinations: String? = null
)

package com.adoptu.dto.input

import com.universaliun.formats.json.JsonDecodable
import com.universaliun.formats.json.JsonEncodable

enum class PetEditSuggestionStatus {
    PENDING, APPROVED, REJECTED
}

// Every field is nullable - null means "no change suggested to this field", not "clear it".
// Curated safe subset of Pets' fields (see PetEditSuggestions in Models.kt for why).
@JsonDecodable(strict = false)
@JsonEncodable
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

@JsonDecodable(strict = false)
@JsonEncodable
data class CreatePetEditSuggestionRequest(
    val description: String? = null,
    val temperament: String? = null,
    val energyLevel: String? = null,
    val specialNeeds: String? = null,
    val vaccinations: String? = null
)

package com.adoptu.dto.input

import com.universaliun.formats.json.JsonDecodable
import com.universaliun.formats.json.JsonEncodable

enum class MedicalEventCategory {
    VACCINATION, DEWORMING
}

@JsonDecodable(strict = false)
@JsonEncodable
data class PetMedicalEventDto(
    val id: Int,
    val petId: Int,
    val category: MedicalEventCategory,
    val name: String,
    val administeredDate: Long,
    val nextDueDate: Long? = null,
    val notes: String? = null,
    val reminder7dSent: Boolean = false,
    val reminderDueSent: Boolean = false,
    val reminderOverdueSent: Boolean = false,
    val createdAt: Long
)

@JsonDecodable(strict = false)
@JsonEncodable
data class CreatePetMedicalEventRequest(
    val category: MedicalEventCategory,
    val name: String,
    val administeredDate: Long,
    val nextDueDate: Long? = null,
    val notes: String? = null
)

enum class MedicalEventUrgency { OVERDUE, DUE_SOON, SCHEDULED }

// Flattened view used by the Manage Pets "Medical Events overview" - one row per event across
// every pet the rescuer owns, with the pet's name denormalized in (mirrors the pattern in
// SponsorshipOfferRepository.denormalize) so the frontend doesn't need a second round trip.
@JsonDecodable(strict = false)
@JsonEncodable
data class RescuerMedicalEventDto(
    val id: Int,
    val petId: Int,
    val petName: String,
    val category: MedicalEventCategory,
    val name: String,
    val administeredDate: Long,
    val nextDueDate: Long?,
    val urgency: MedicalEventUrgency?
)

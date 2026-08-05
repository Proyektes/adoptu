package com.adoptu.dto.input


enum class MedicalEventCategory {
    VACCINATION, DEWORMING
}

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

data class CreatePetMedicalEventRequest(
    val category: MedicalEventCategory,
    val name: String,
    val administeredDate: Long,
    val nextDueDate: Long? = null,
    val notes: String? = null
)

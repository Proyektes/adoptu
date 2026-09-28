package com.adoptu.dto.input

import com.universaliun.formats.json.generated.decodeAsCreatePetMedicalEventRequest
import com.universaliun.formats.json.generated.decodeAsPetMedicalEventDto
import com.universaliun.formats.json.generated.decodeAsRescuerMedicalEventDto
import com.universaliun.formats.json.generated.encodeToJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PetMedicalEventDtoTest {

    @Test
    fun `PetMedicalEventDto round-trips`() {
        val original = PetMedicalEventDto(
            id = 1,
            petId = 2,
            category = MedicalEventCategory.VACCINATION,
            name = "Rabies",
            administeredDate = 1_700_000_000_000,
            nextDueDate = 1_730_000_000_000,
            notes = "Booster next year",
            reminder7dSent = true,
            reminderDueSent = false,
            reminderOverdueSent = false,
            createdAt = 1_700_000_001_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsPetMedicalEventDto())
    }

    @Test
    fun `CreatePetMedicalEventRequest round-trips`() {
        val original = CreatePetMedicalEventRequest(
            category = MedicalEventCategory.DEWORMING,
            name = "Dewormer",
            administeredDate = 1_700_000_000_000,
            nextDueDate = null,
            notes = null,
        )
        assertEquals(original, original.encodeToJson().decodeAsCreatePetMedicalEventRequest())
    }

    @Test
    fun `RescuerMedicalEventDto round-trips including a null urgency`() {
        val original = RescuerMedicalEventDto(
            id = 1,
            petId = 2,
            petName = "Fido",
            category = MedicalEventCategory.VACCINATION,
            name = "Rabies",
            administeredDate = 1_700_000_000_000,
            nextDueDate = 1_730_000_000_000,
            urgency = MedicalEventUrgency.DUE_SOON,
        )
        assertEquals(original, original.encodeToJson().decodeAsRescuerMedicalEventDto())

        val withoutUrgency = original.copy(nextDueDate = null, urgency = null)
        assertEquals(withoutUrgency, withoutUrgency.encodeToJson().decodeAsRescuerMedicalEventDto())
    }
}

package com.adoptu.dto.input

import com.universaliun.formats.json.generated.decodeAsCreatePetEditSuggestionRequest
import com.universaliun.formats.json.generated.decodeAsPetEditSuggestionDto
import com.universaliun.formats.json.generated.encodeToJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PetEditSuggestionDtoTest {

    @Test
    fun `PetEditSuggestionDto round-trips`() {
        val original = PetEditSuggestionDto(
            id = 1,
            petId = 2,
            petName = "Fido",
            volunteerId = 3,
            volunteerName = "Vol",
            description = "New description",
            temperament = "Calm",
            energyLevel = "Low",
            specialNeeds = "None",
            vaccinations = "Rabies",
            status = PetEditSuggestionStatus.PENDING,
            createdAt = 1_700_000_000_000,
            reviewedAt = 1_700_000_001_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsPetEditSuggestionDto())
    }

    @Test
    fun `PetEditSuggestionDto round-trips with every nullable field absent`() {
        val original = PetEditSuggestionDto(
            id = 1,
            petId = 2,
            volunteerId = 3,
            status = PetEditSuggestionStatus.REJECTED,
            createdAt = 1_700_000_000_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsPetEditSuggestionDto())
    }

    @Test
    fun `CreatePetEditSuggestionRequest round-trips including its declared defaults`() {
        assertEquals(
            CreatePetEditSuggestionRequest(),
            CreatePetEditSuggestionRequest().encodeToJson().decodeAsCreatePetEditSuggestionRequest(),
        )
        val original = CreatePetEditSuggestionRequest(
            description = "New description",
            temperament = "Calm",
            energyLevel = "Low",
            specialNeeds = "None",
            vaccinations = "Rabies",
        )
        assertEquals(original, original.encodeToJson().decodeAsCreatePetEditSuggestionRequest())
    }
}

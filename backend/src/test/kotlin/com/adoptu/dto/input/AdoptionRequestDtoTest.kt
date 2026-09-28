package com.adoptu.dto.input

import com.universaliun.formats.json.generated.decodeAsAdoptionRequestDto
import com.universaliun.formats.json.generated.decodeAsCreateAdoptionRequestRequest
import com.universaliun.formats.json.generated.encodeToJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AdoptionRequestDtoTest {

    @Test
    fun `AdoptionRequestDto round-trips`() {
        val original = AdoptionRequestDto(
            id = 1,
            petId = 2,
            adopterId = 3,
            message = "I would love to adopt",
            status = "PENDING",
            housingType = HousingType.APARTMENT,
            hasYard = false,
            hasOtherPets = true,
            experienceLevel = AdoptionExperience.EXPERIENCED,
            reviewNote = "Looks good",
            createdAt = 1_700_000_000_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsAdoptionRequestDto())
    }

    @Test
    fun `AdoptionRequestDto round-trips with every nullable field absent`() {
        val original = AdoptionRequestDto(
            id = 1,
            petId = 2,
            adopterId = 3,
            message = "Hi",
            status = "PENDING",
            createdAt = 1_700_000_000_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsAdoptionRequestDto())
    }

    @Test
    fun `CreateAdoptionRequestRequest round-trips including its declared defaults`() {
        assertEquals(
            CreateAdoptionRequestRequest(),
            CreateAdoptionRequestRequest().encodeToJson().decodeAsCreateAdoptionRequestRequest(),
        )
        val original = CreateAdoptionRequestRequest(
            message = "Please consider me",
            housingType = HousingType.HOUSE,
            hasYard = true,
            hasOtherPets = false,
            experienceLevel = AdoptionExperience.FIRST_TIME,
        )
        assertEquals(original, original.encodeToJson().decodeAsCreateAdoptionRequestRequest())
    }
}

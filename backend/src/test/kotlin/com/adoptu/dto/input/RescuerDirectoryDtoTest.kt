package com.adoptu.dto.input

import com.universaliun.formats.json.generated.decodeAsRescuerDetailDto
import com.universaliun.formats.json.generated.decodeAsRescuerDirectoryDto
import com.universaliun.formats.json.generated.encodeToJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RescuerDirectoryDtoTest {

    @Test
    fun `RescuerDirectoryDto round-trips`() {
        val original = RescuerDirectoryDto(userId = 1, displayName = "Rescuer", country = "MX", availablePetCount = 4)
        assertEquals(original, original.encodeToJson().decodeAsRescuerDirectoryDto())
    }

    @Test
    fun `RescuerDetailDto round-trips a List of nested PetDto`() {
        val pet = PetDto(
            id = 1, rescuerId = 1, name = "Fido", type = "Dog", description = "Friendly", weight = 10.0,
            ageYears = 2, ageMonths = 0, sex = Gender.MALE, status = Status.AVAILABLE, createdAt = 1_700_000_000_000,
        )
        val original = RescuerDetailDto(userId = 1, displayName = "Rescuer", country = "MX", pets = listOf(pet))
        assertEquals(original, original.encodeToJson().decodeAsRescuerDetailDto())
    }
}

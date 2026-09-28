package com.adoptu.dto.input

import com.universaliun.formats.json.generated.decodeAsCreateFosterPlacementRequest
import com.universaliun.formats.json.generated.decodeAsPetFosterPlacementDto
import com.universaliun.formats.json.generated.encodeToJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PetFosterPlacementDtoTest {

    @Test
    fun `PetFosterPlacementDto round-trips`() {
        val original = PetFosterPlacementDto(
            id = 1,
            petId = 2,
            petName = "Fido",
            temporalHomeId = 3,
            temporalHomeAlias = "Home",
            startDate = 1_700_000_000_000,
            endDate = 1_700_100_000_000,
            notes = "Doing well",
            createdAt = 1_700_000_001_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsPetFosterPlacementDto())
    }

    @Test
    fun `PetFosterPlacementDto round-trips with every nullable field absent`() {
        val original = PetFosterPlacementDto(id = 1, petId = 2, temporalHomeId = 3, startDate = 1_700_000_000_000, createdAt = 1_700_000_001_000)
        assertEquals(original, original.encodeToJson().decodeAsPetFosterPlacementDto())
    }

    @Test
    fun `CreateFosterPlacementRequest round-trips`() {
        val original = CreateFosterPlacementRequest(temporalHomeId = 3, notes = "Please take care")
        assertEquals(original, original.encodeToJson().decodeAsCreateFosterPlacementRequest())
    }
}

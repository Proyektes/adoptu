package com.adoptu.dto.input

import com.universaliun.formats.json.generated.decodeAsCreatePetRequest
import com.universaliun.formats.json.generated.decodeAsPetDto
import com.universaliun.formats.json.generated.decodeAsPetImageDto
import com.universaliun.formats.json.generated.decodeAsUpdatePetRequest
import com.universaliun.formats.json.generated.encodeToJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Round-trip + wire-compat coverage for the DataFormatsKit-generated codecs
 * (`@JsonDecodable`/`@JsonEncodable`) that replace Jackson for this file's DTOs. Byte-compat
 * assertions pin the two things Jackson's `ObjectMapper` did differently that the generated
 * encoder had to match: enums are written by their Kotlin name (matches
 * `jackson-module-kotlin`'s default), and every property is written even when `null` - the
 * ObjectMapper in `JsonSupport.kt` never configured `JsonInclude.NON_NULL`, so its default
 * (`ALWAYS`) emitted `"field":null` rather than omitting the key, and the generated encoder does
 * the same (see `GeneratedEncodeTest`, `"nickname":null` in the DataFormatsKit repo).
 */
class PetDtoTest {

    private fun fullPet() = PetDto(
        id = 1,
        rescuerId = 2,
        name = "Fido",
        type = "Dog",
        breed = "Labrador",
        description = "Friendly",
        weight = 12.5,
        ageYears = 3,
        ageMonths = 4,
        sex = Gender.MALE,
        status = Status.AVAILABLE,
        color = "Brown",
        size = "Large",
        temperament = "Calm",
        isSterilized = true,
        isMicrochipped = true,
        microchipId = "abc123",
        vaccinations = "Rabies",
        isGoodWithKids = true,
        isGoodWithDogs = false,
        isGoodWithCats = false,
        isHouseTrained = true,
        energyLevel = "High",
        rescueDate = 1_700_000_000_000,
        rescueLocation = "Downtown",
        country = "MX",
        specialNeeds = "None",
        adoptionFee = 50.0,
        currency = Currency.MXN,
        isUrgent = true,
        isPromoted = true,
        promotedReason = PromotedReason.MOVING,
        promotedReasonDetail = "Relocating",
        createdAt = 1_700_000_001_000,
        deactivatedAt = null,
        deactivatedBy = null,
        images = listOf(PetImageDto(id = 10, imageUrl = "https://x/1.jpg", isPrimary = true, sortOrder = 0)),
        videoUrl = null,
    )

    @Test
    fun `PetDto round-trips through encode and decode`() {
        val original = fullPet()
        val decoded = original.encodeToJson().decodeAsPetDto()
        assertEquals(original, decoded)
    }

    @Test
    fun `PetDto encodes enums by Kotlin name and keeps explicit nulls`() {
        val json = fullPet().copy(deactivatedAt = null, videoUrl = null).encodeToJson().decodeToString()
        assertTrue(json.contains("\"sex\":\"MALE\""))
        assertTrue(json.contains("\"status\":\"AVAILABLE\""))
        assertTrue(json.contains("\"currency\":\"MXN\""))
        assertTrue(json.contains("\"promotedReason\":\"MOVING\""))
        assertTrue(json.contains("\"deactivatedAt\":null"))
        assertTrue(json.contains("\"videoUrl\":null"))
    }

    @Test
    fun `PetImageDto round-trips`() {
        val original = PetImageDto(id = 5, imageUrl = "https://x/y.jpg", isPrimary = false, sortOrder = 2)
        assertEquals(original, original.encodeToJson().decodeAsPetImageDto())
    }

    @Test
    fun `CreatePetRequest round-trips with every optional field populated`() {
        val original = CreatePetRequest(
            name = "Rex",
            type = "Dog",
            breed = "Poodle",
            description = "Playful",
            weight = 8.0,
            ageYears = 1,
            ageMonths = 2,
            sex = Gender.FEMALE,
            color = "White",
            size = "Small",
            temperament = "Energetic",
            isSterilized = false,
            isMicrochipped = false,
            microchipId = null,
            vaccinations = null,
            isGoodWithKids = true,
            isGoodWithDogs = true,
            isGoodWithCats = true,
            isHouseTrained = false,
            energyLevel = "Medium",
            rescueDate = 1_700_000_002_000,
            rescueLocation = "Park",
            country = "US",
            specialNeeds = null,
            adoptionFee = 25.0,
            currency = Currency.USD,
            isUrgent = false,
            isPromoted = false,
            promotedReason = null,
            promotedReasonDetail = null,
        )
        assertEquals(original, original.encodeToJson().decodeAsCreatePetRequest())
    }

    @Test
    fun `CreatePetRequest decodes to its declared defaults when optional fields are absent from the wire`() {
        val decoded = """{"name":"Rex","type":"Dog"}""".encodeToByteArray().decodeAsCreatePetRequest()
        assertEquals(CreatePetRequest(name = "Rex", type = "Dog"), decoded)
    }

    @Test
    fun `UpdatePetRequest round-trips an all-null (no-op) update`() {
        val original = UpdatePetRequest()
        assertEquals(original, original.encodeToJson().decodeAsUpdatePetRequest())
    }

    @Test
    fun `UpdatePetRequest round-trips a fully-populated partial update`() {
        val original = UpdatePetRequest(
            name = "New Name",
            status = Status.PENDING,
            sex = Gender.MALE,
            currency = Currency.EUR,
            promotedReason = PromotedReason.OTHER,
            isUrgent = true,
        )
        assertEquals(original, original.encodeToJson().decodeAsUpdatePetRequest())
    }
}

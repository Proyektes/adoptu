package com.adoptu.dto.input

import com.universaliun.formats.json.generated.decodeAsCreateSterilizationLocationRequest
import com.universaliun.formats.json.generated.decodeAsCreateUserSterilizationLocationRequest
import com.universaliun.formats.json.generated.decodeAsSterilizationLocationDto
import com.universaliun.formats.json.generated.decodeAsSterilizationLocationsByCity
import com.universaliun.formats.json.generated.decodeAsSterilizationLocationsByLocation
import com.universaliun.formats.json.generated.decodeAsSterilizationLocationsByState
import com.universaliun.formats.json.generated.decodeAsUpdateSterilizationLocationRequest
import com.universaliun.formats.json.generated.decodeAsUpdateUserSterilizationLocationRequest
import com.universaliun.formats.json.generated.decodeAsUserSterilizationLocationDto
import com.universaliun.formats.json.generated.encodeToJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SterilizationLocationDtoTest {

    private fun fullDto() = SterilizationLocationDto(
        id = 1, userId = 2, name = "Clinic", country = "MX", state = "CDMX", city = "Mexico City",
        neighborhood = "Centro", address = "Calle 1", zip = "01000", phone = "555-0000",
        email = "clinic@example.com", website = "https://clinic.example", description = "A clinic",
        createdAt = 1_700_000_000_000, updatedAt = 1_700_000_001_000,
    )

    @Test
    fun `SterilizationLocationDto round-trips`() {
        val original = fullDto()
        assertEquals(original, original.encodeToJson().decodeAsSterilizationLocationDto())
    }

    @Test
    fun `CreateSterilizationLocationRequest round-trips`() {
        val original = CreateSterilizationLocationRequest(name = "Clinic", country = "MX", city = "Mexico City", address = "Calle 1")
        assertEquals(original, original.encodeToJson().decodeAsCreateSterilizationLocationRequest())
    }

    @Test
    fun `UpdateSterilizationLocationRequest round-trips an all-null instance`() {
        val original = UpdateSterilizationLocationRequest()
        assertEquals(original, original.encodeToJson().decodeAsUpdateSterilizationLocationRequest())
    }

    @Test
    fun `nested By-Location-By-State-By-City hierarchy round-trips`() {
        val byCity = SterilizationLocationsByCity(city = "Mexico City", locations = listOf(fullDto()))
        assertEquals(byCity, byCity.encodeToJson().decodeAsSterilizationLocationsByCity())

        val byState = SterilizationLocationsByState(state = "CDMX", cities = listOf(byCity))
        assertEquals(byState, byState.encodeToJson().decodeAsSterilizationLocationsByState())

        val byLocation = SterilizationLocationsByLocation(country = "MX", states = listOf(byState))
        assertEquals(byLocation, byLocation.encodeToJson().decodeAsSterilizationLocationsByLocation())
    }

    @Test
    fun `SterilizationLocationsByState round-trips a null state (unspecified)`() {
        val original = SterilizationLocationsByState(state = null, cities = emptyList())
        assertEquals(original, original.encodeToJson().decodeAsSterilizationLocationsByState())
    }

    @Test
    fun `UserSterilizationLocationDto round-trips`() {
        val original = UserSterilizationLocationDto(
            userId = 1, name = "Clinic", country = "MX", state = "CDMX", city = "Mexico City",
            neighborhood = "Centro", address = "Calle 1", zip = "01000", phone = "555-0000",
            email = "clinic@example.com", emailVerified = true, website = "https://clinic.example",
            description = "A clinic", createdAt = 1_700_000_000_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsUserSterilizationLocationDto())
    }

    @Test
    fun `CreateUserSterilizationLocationRequest round-trips`() {
        val original = CreateUserSterilizationLocationRequest(name = "Clinic", country = "MX", city = "Mexico City", address = "Calle 1")
        assertEquals(original, original.encodeToJson().decodeAsCreateUserSterilizationLocationRequest())
    }

    @Test
    fun `UpdateUserSterilizationLocationRequest round-trips an all-null instance`() {
        val original = UpdateUserSterilizationLocationRequest()
        assertEquals(original, original.encodeToJson().decodeAsUpdateUserSterilizationLocationRequest())
    }
}

package com.adoptu.dto.input

import com.universaliun.formats.json.generated.decodeAsCreateShelterRequest
import com.universaliun.formats.json.generated.decodeAsCreateUserShelterRequest
import com.universaliun.formats.json.generated.decodeAsShelterDto
import com.universaliun.formats.json.generated.decodeAsUpdateShelterRequest
import com.universaliun.formats.json.generated.decodeAsUpdateUserShelterRequest
import com.universaliun.formats.json.generated.decodeAsUserShelterDto
import com.universaliun.formats.json.generated.encodeToJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ShelterDtoTest {

    @Test
    fun `ShelterDto round-trips with every field populated`() {
        val original = ShelterDto(
            id = 1, userId = 2, name = "Shelter", country = "MX", state = "CDMX", city = "Mexico City",
            neighborhood = "Centro", address = "Calle 1", zip = "01000", phone = "555-0000",
            email = "shelter@example.com", website = "https://shelter.example", fiscalId = "FID1",
            bankName = "Bank", accountHolderName = "Holder", accountNumber = "12345", iban = "MX00",
            swiftBic = "BIC1", currency = "MXN", description = "A shelter", createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_001_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsShelterDto())
    }

    @Test
    fun `ShelterDto round-trips with every nullable field absent`() {
        val original = ShelterDto(
            id = 1, name = "Shelter", country = "MX", city = "Mexico City", address = "Calle 1",
            createdAt = 1_700_000_000_000, updatedAt = 1_700_000_001_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsShelterDto())
    }

    @Test
    fun `CreateShelterRequest round-trips including its declared default currency`() {
        val original = CreateShelterRequest(name = "Shelter", country = "MX", city = "Mexico City", address = "Calle 1")
        assertEquals(original, original.encodeToJson().decodeAsCreateShelterRequest())
        assertEquals("USD", original.currency)
    }

    @Test
    fun `UpdateShelterRequest round-trips an all-null instance`() {
        val original = UpdateShelterRequest()
        assertEquals(original, original.encodeToJson().decodeAsUpdateShelterRequest())
    }

    @Test
    fun `UserShelterDto round-trips`() {
        val original = UserShelterDto(
            userId = 1, name = "Shelter", country = "MX", state = "CDMX", city = "Mexico City",
            neighborhood = "Centro", address = "Calle 1", zip = "01000", phone = "555-0000",
            email = "shelter@example.com", emailVerified = true, website = "https://shelter.example",
            fiscalId = "FID1", bankName = "Bank", accountHolderName = "Holder", accountNumber = "12345",
            iban = "MX00", swiftBic = "BIC1", currency = "MXN", description = "A shelter",
            createdAt = 1_700_000_000_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsUserShelterDto())
    }

    @Test
    fun `CreateUserShelterRequest round-trips`() {
        val original = CreateUserShelterRequest(name = "Shelter", country = "MX", city = "Mexico City", address = "Calle 1")
        assertEquals(original, original.encodeToJson().decodeAsCreateUserShelterRequest())
    }

    @Test
    fun `UpdateUserShelterRequest round-trips an all-null instance`() {
        val original = UpdateUserShelterRequest()
        assertEquals(original, original.encodeToJson().decodeAsUpdateUserShelterRequest())
    }
}

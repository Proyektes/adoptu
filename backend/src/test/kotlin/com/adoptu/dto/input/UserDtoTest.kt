package com.adoptu.dto.input

import com.universaliun.formats.json.generated.decodeAsAcceptTermsRequest
import com.universaliun.formats.json.generated.decodeAsBanUserRequest
import com.universaliun.formats.json.generated.decodeAsBlockRescuerRequest
import com.universaliun.formats.json.generated.decodeAsCreateMultiPhotographerRequestRequest
import com.universaliun.formats.json.generated.decodeAsCreatePhotographyRequestRequest
import com.universaliun.formats.json.generated.decodeAsCreateTemporalHomeRequest
import com.universaliun.formats.json.generated.decodeAsPhotographerDto
import com.universaliun.formats.json.generated.decodeAsPhotographerSettingsRequest
import com.universaliun.formats.json.generated.decodeAsPhotographyRequestDto
import com.universaliun.formats.json.generated.decodeAsRoleActivationRequest
import com.universaliun.formats.json.generated.decodeAsSendTemporalHomeRequestRequest
import com.universaliun.formats.json.generated.decodeAsTemporalHomeDto
import com.universaliun.formats.json.generated.decodeAsTemporalHomeRequestDto
import com.universaliun.formats.json.generated.decodeAsTemporalHomeSearchParams
import com.universaliun.formats.json.generated.decodeAsUpdatePhotographyRequestRequest
import com.universaliun.formats.json.generated.decodeAsUpdateTemporalHomeRequest
import com.universaliun.formats.json.generated.decodeAsUserDto
import com.universaliun.formats.json.generated.encodeToJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserDtoTest {

    @Test
    fun `UserDto round-trips with a populated Set of enums`() {
        val original = UserDto(
            id = 1,
            username = "user@example.com",
            email = "user@example.com",
            displayName = "User One",
            language = "es",
            country = "MX",
            isEmailVerified = true,
            activeRoles = setOf(UserRole.RESCUER, UserRole.ADOPTER),
            lastAcceptedPrivacyPolicy = 1_700_000_000_000,
            lastAcceptedTermsAndConditions = 1_700_000_000_001,
            isBanned = false,
            banReason = null,
            deactivatedAt = null,
            deactivatedBy = null,
        )
        assertEquals(original, original.encodeToJson().decodeAsUserDto())
    }

    @Test
    fun `UserDto encodes each Set entry by its enum name`() {
        val json = UserDto(id = 1, username = "u", displayName = "D", activeRoles = setOf(UserRole.SHELTER))
            .encodeToJson().decodeToString()
        assertTrue(json.contains("\"activeRoles\":[\"SHELTER\"]"))
    }

    @Test
    fun `UserDto decodes to its declared defaults when optional fields are absent`() {
        val decoded = """{"id":1,"username":"u","displayName":"D"}""".encodeToByteArray().decodeAsUserDto()
        assertEquals(UserDto(id = 1, username = "u", displayName = "D"), decoded)
    }

    @Test
    fun `BanUserRequest round-trips`() {
        val original = BanUserRequest(reason = "Policy violation")
        assertEquals(original, original.encodeToJson().decodeAsBanUserRequest())
        assertEquals(BanUserRequest(), BanUserRequest().encodeToJson().decodeAsBanUserRequest())
    }

    @Test
    fun `PhotographerDto round-trips`() {
        val original = PhotographerDto(
            userId = 1,
            displayName = "Photog",
            username = "photog",
            photographerFee = 20.0,
            photographerCurrency = "USD",
            country = "US",
            state = "CA",
        )
        assertEquals(original, original.encodeToJson().decodeAsPhotographerDto())
    }

    @Test
    fun `AcceptTermsRequest round-trips including its declared defaults`() {
        assertEquals(AcceptTermsRequest(), AcceptTermsRequest().encodeToJson().decodeAsAcceptTermsRequest())
        val original = AcceptTermsRequest(acceptPrivacyPolicy = true, acceptTermsAndConditions = true)
        assertEquals(original, original.encodeToJson().decodeAsAcceptTermsRequest())
    }

    @Test
    fun `PhotographerSettingsRequest round-trips`() {
        val original = PhotographerSettingsRequest(
            photographerFee = 15.5,
            photographerCurrency = "EUR",
            country = "FR",
            state = null,
        )
        assertEquals(original, original.encodeToJson().decodeAsPhotographerSettingsRequest())
    }

    @Test
    fun `PhotographyRequestDto round-trips`() {
        val original = PhotographyRequestDto(
            id = 1,
            photographerId = 2,
            photographerName = "P",
            requesterId = 3,
            requesterName = "R",
            petId = 4,
            petName = "Pet",
            message = "Please",
            status = "PENDING",
            scheduledDate = 1_700_000_003_000,
            createdAt = 1_700_000_004_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsPhotographyRequestDto())
    }

    @Test
    fun `CreatePhotographyRequestRequest round-trips`() {
        val original = CreatePhotographyRequestRequest(photographerId = 1, petId = 2, message = "hi")
        assertEquals(original, original.encodeToJson().decodeAsCreatePhotographyRequestRequest())
    }

    @Test
    fun `UpdatePhotographyRequestRequest round-trips`() {
        val original = UpdatePhotographyRequestRequest(status = "ACCEPTED", scheduledDate = 1_700_000_005_000)
        assertEquals(original, original.encodeToJson().decodeAsUpdatePhotographyRequestRequest())
    }

    @Test
    fun `CreateMultiPhotographerRequestRequest round-trips a List of Int`() {
        val original = CreateMultiPhotographerRequestRequest(photographerIds = listOf(1, 2, 3), petId = 9, message = "hi all")
        assertEquals(original, original.encodeToJson().decodeAsCreateMultiPhotographerRequestRequest())
    }

    @Test
    fun `RoleActivationRequest round-trips`() {
        val original = RoleActivationRequest(activate = true)
        assertEquals(original, original.encodeToJson().decodeAsRoleActivationRequest())
    }

    @Test
    fun `TemporalHomeDto round-trips`() {
        val original = TemporalHomeDto(
            userId = 1,
            alias = "Home",
            country = "MX",
            state = "CDMX",
            city = "Mexico City",
            zip = "01000",
            neighborhood = "Centro",
            maxCapacity = 5,
            createdAt = 1_700_000_006_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsTemporalHomeDto())
    }

    @Test
    fun `TemporalHomeSearchParams round-trips an all-null instance`() {
        val original = TemporalHomeSearchParams()
        assertEquals(original, original.encodeToJson().decodeAsTemporalHomeSearchParams())
    }

    @Test
    fun `CreateTemporalHomeRequest round-trips`() {
        val original = CreateTemporalHomeRequest(
            alias = "Home",
            country = "MX",
            state = "CDMX",
            city = "Mexico City",
            zip = "01000",
            neighborhood = "Centro",
            streetAddress = "Calle 1",
            phone = "555-0000",
            maxCapacity = 3,
        )
        assertEquals(original, original.encodeToJson().decodeAsCreateTemporalHomeRequest())
    }

    @Test
    fun `UpdateTemporalHomeRequest round-trips an all-null instance`() {
        val original = UpdateTemporalHomeRequest()
        assertEquals(original, original.encodeToJson().decodeAsUpdateTemporalHomeRequest())
    }

    @Test
    fun `SendTemporalHomeRequestRequest round-trips`() {
        val original = SendTemporalHomeRequestRequest(temporalHomeId = 1, petId = 2, message = "please host")
        assertEquals(original, original.encodeToJson().decodeAsSendTemporalHomeRequestRequest())
    }

    @Test
    fun `BlockRescuerRequest round-trips`() {
        val original = BlockRescuerRequest(rescuerId = 42)
        assertEquals(original, original.encodeToJson().decodeAsBlockRescuerRequest())
    }

    @Test
    fun `TemporalHomeRequestDto round-trips`() {
        val original = TemporalHomeRequestDto(
            id = 1,
            temporalHomeId = 2,
            temporalHomeAlias = "Home",
            rescuerId = 3,
            rescuerName = "R",
            petId = 4,
            petName = "Pet",
            message = "please",
            status = "PENDING",
            createdAt = 1_700_000_007_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsTemporalHomeRequestDto())
    }
}

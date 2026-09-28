package com.adoptu.dto.input

import com.universaliun.formats.json.generated.decodeAsCreateSponsorshipOfferRequest
import com.universaliun.formats.json.generated.decodeAsSponsorshipOfferDto
import com.universaliun.formats.json.generated.encodeToJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SponsorshipOfferDtoTest {

    @Test
    fun `SponsorshipOfferDto round-trips a money offer`() {
        val original = SponsorshipOfferDto(
            id = 1, sponsorId = 2, sponsorName = "Sponsor", rescuerId = 3, rescuerName = "Rescuer",
            petId = 4, petName = "Fido", offerType = SponsorshipOfferType.MONEY, amount = 50.0,
            currency = Currency.USD, inKindDescription = null, message = "Happy to help", status = "PENDING",
            createdAt = 1_700_000_000_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsSponsorshipOfferDto())
    }

    @Test
    fun `SponsorshipOfferDto round-trips an in-kind offer`() {
        val original = SponsorshipOfferDto(
            id = 1, sponsorId = 2, rescuerId = 3, offerType = SponsorshipOfferType.IN_KIND,
            amount = null, currency = null, inKindDescription = "10 bags of dog food",
            message = "Happy to help", status = "ACCEPTED", createdAt = 1_700_000_000_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsSponsorshipOfferDto())
    }

    @Test
    fun `CreateSponsorshipOfferRequest round-trips`() {
        val original = CreateSponsorshipOfferRequest(
            rescuerId = 3, petId = 4, offerType = SponsorshipOfferType.MONEY, amount = 20.0,
            currency = Currency.EUR, inKindDescription = null, message = "Here to help",
        )
        assertEquals(original, original.encodeToJson().decodeAsCreateSponsorshipOfferRequest())
    }
}

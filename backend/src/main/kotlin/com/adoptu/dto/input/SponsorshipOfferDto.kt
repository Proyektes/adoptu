package com.adoptu.dto.input

enum class SponsorshipOfferType {
    MONEY, IN_KIND
}

data class SponsorshipOfferDto(
    val id: Int,
    val sponsorId: Int,
    val sponsorName: String? = null,
    val rescuerId: Int,
    val rescuerName: String? = null,
    val petId: Int? = null,
    val petName: String? = null,
    val offerType: SponsorshipOfferType,
    val amount: Double? = null,
    val currency: Currency? = null,
    val inKindDescription: String? = null,
    val message: String,
    val status: String,
    val createdAt: Long
)

data class CreateSponsorshipOfferRequest(
    val rescuerId: Int,
    val petId: Int? = null,
    val offerType: SponsorshipOfferType,
    val amount: Double? = null,
    val currency: Currency? = null,
    val inKindDescription: String? = null,
    val message: String
)

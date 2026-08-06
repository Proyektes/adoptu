package com.adoptu.ports

import com.adoptu.dto.input.CreateSponsorshipOfferRequest
import com.adoptu.dto.input.SponsorshipOfferDto

interface SponsorshipOfferRepositoryPort {
    suspend fun create(sponsorId: Int, request: CreateSponsorshipOfferRequest): SponsorshipOfferDto
    suspend fun getById(id: Int): SponsorshipOfferDto?
    suspend fun getForRescuer(rescuerId: Int): List<SponsorshipOfferDto>
    suspend fun getForSponsor(sponsorId: Int): List<SponsorshipOfferDto>
    suspend fun markRead(id: Int): Boolean
}

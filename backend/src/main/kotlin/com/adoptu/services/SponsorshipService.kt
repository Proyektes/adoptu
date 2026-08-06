package com.adoptu.services

import com.adoptu.dto.input.CreateSponsorshipOfferRequest
import com.adoptu.dto.input.SponsorshipOfferDto
import com.adoptu.dto.input.SponsorshipOfferType
import com.adoptu.dto.input.UserRole
import com.adoptu.ports.NotificationPort
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.ports.SponsorshipOfferRepositoryPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Matching/agreement only - see SponsorshipOffers' doc comment in Models.kt. This service's job
// ends at "connect the two sides"; no money or goods move through Adopt-U itself.
class SponsorshipService(
    private val offerRepository: SponsorshipOfferRepositoryPort,
    private val petRepository: PetRepositoryPort,
    private val userService: UserService,
    private val notificationPort: NotificationPort
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    suspend fun createOffer(sponsorId: Int, request: CreateSponsorshipOfferRequest): ServiceResult<SponsorshipOfferDto> {
        val rescuer = userService.getById(request.rescuerId) ?: return ServiceResult.NotFound
        if (!rescuer.activeRoles.contains(UserRole.RESCUER)) {
            return ServiceResult.Error("That user is not an active rescuer")
        }

        var petName: String? = null
        if (request.petId != null) {
            val pet = petRepository.getById(request.petId) ?: return ServiceResult.NotFound
            if (pet.rescuerId != request.rescuerId) {
                return ServiceResult.Error("That pet doesn't belong to this rescuer")
            }
            petName = pet.name
        }

        when (request.offerType) {
            SponsorshipOfferType.MONEY -> {
                if (request.amount == null || request.amount <= 0 || request.currency == null) {
                    return ServiceResult.Error("A money offer needs a positive amount and a currency")
                }
            }
            SponsorshipOfferType.IN_KIND -> {
                if (request.inKindDescription.isNullOrBlank()) {
                    return ServiceResult.Error("Describe what you're offering to help with")
                }
            }
        }
        if (request.message.isBlank()) {
            return ServiceResult.Error("A message is required")
        }

        val offer = offerRepository.create(sponsorId, request)

        val sponsor = userService.getById(sponsorId)
        if (sponsor != null) {
            scope.launch {
                notificationPort.sendSponsorshipOffer(
                    rescuerEmail = rescuer.username,
                    rescuerName = rescuer.displayName,
                    sponsorName = sponsor.displayName,
                    petName = petName,
                    offerType = request.offerType.name,
                    amount = request.amount,
                    currency = request.currency?.name,
                    inKindDescription = request.inKindDescription,
                    message = request.message
                )
            }
        }

        return ServiceResult.Success(offer)
    }

    suspend fun getForRescuer(rescuerId: Int, userId: Int, userRoles: Set<String>): ServiceResult<List<SponsorshipOfferDto>> {
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        return ServiceResult.Success(offerRepository.getForRescuer(rescuerId))
    }

    suspend fun getForSponsor(sponsorId: Int): List<SponsorshipOfferDto> =
        offerRepository.getForSponsor(sponsorId)

    suspend fun markRead(id: Int, userId: Int, userRoles: Set<String>): ServiceResult<SponsorshipOfferDto> {
        val offer = offerRepository.getById(id) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && offer.rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        offerRepository.markRead(id)
        return ServiceResult.Success(offerRepository.getById(id)!!)
    }
}

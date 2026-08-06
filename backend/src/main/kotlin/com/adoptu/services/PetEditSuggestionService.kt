package com.adoptu.services

import com.adoptu.dto.input.CreatePetEditSuggestionRequest
import com.adoptu.dto.input.PetEditSuggestionDto
import com.adoptu.dto.input.PetEditSuggestionStatus
import com.adoptu.dto.input.UpdatePetRequest
import com.adoptu.ports.PetEditSuggestionRepositoryPort
import com.adoptu.ports.PetRepositoryPort
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PetEditSuggestionService(
    private val suggestionRepository: PetEditSuggestionRepositoryPort,
    private val petRepository: PetRepositoryPort,
    private val volunteerService: VolunteerService,
    private val clock: Clock
) {
    // Active-volunteer-only, scoped to the pet's own rescuer - being an active volunteer for
    // rescuer A doesn't grant suggest-edit rights on rescuer B's pets.
    suspend fun createSuggestion(petId: Int, volunteerId: Int, request: CreatePetEditSuggestionRequest): ServiceResult<PetEditSuggestionDto> {
        val pet = petRepository.getById(petId) ?: return ServiceResult.NotFound
        if (!volunteerService.isActiveVolunteerFor(pet.rescuerId, volunteerId)) {
            return ServiceResult.Forbidden
        }
        if (listOf(request.description, request.temperament, request.energyLevel, request.specialNeeds, request.vaccinations).all { it == null }) {
            return ServiceResult.Error("Suggest a change to at least one field")
        }
        return ServiceResult.Success(suggestionRepository.create(petId, volunteerId, request))
    }

    // Rescuer/admin only - matches who can already edit the pet. Approving applies the
    // suggestion's non-null fields to the pet via the existing update path (UpdatePetRequest
    // already treats null as "no change"), so this is exactly the pet's own edit flow with a
    // volunteer's proposed values instead of the rescuer's.
    suspend fun updateStatus(id: Int, status: PetEditSuggestionStatus, userId: Int, userRoles: Set<String>): ServiceResult<PetEditSuggestionDto> {
        val suggestion = suggestionRepository.getById(id) ?: return ServiceResult.NotFound
        if (suggestion.status != PetEditSuggestionStatus.PENDING) {
            return ServiceResult.Error("This suggestion has already been reviewed")
        }
        val pet = petRepository.getById(suggestion.petId) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && pet.rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        if (status == PetEditSuggestionStatus.PENDING) {
            return ServiceResult.Error("Cannot set a suggestion back to pending")
        }

        if (status == PetEditSuggestionStatus.APPROVED) {
            petRepository.update(
                suggestion.petId,
                UpdatePetRequest(
                    description = suggestion.description,
                    temperament = suggestion.temperament,
                    energyLevel = suggestion.energyLevel,
                    specialNeeds = suggestion.specialNeeds,
                    vaccinations = suggestion.vaccinations
                )
            )
        }

        suggestionRepository.updateStatus(id, status, clock.now().toEpochMilliseconds())
        return ServiceResult.Success(suggestionRepository.getById(id)!!)
    }

    suspend fun getPendingForRescuer(rescuerId: Int, userId: Int, userRoles: Set<String>): ServiceResult<List<PetEditSuggestionDto>> {
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        return ServiceResult.Success(suggestionRepository.getPendingForRescuer(rescuerId))
    }

    suspend fun getMySuggestions(volunteerId: Int): List<PetEditSuggestionDto> =
        suggestionRepository.getForVolunteer(volunteerId)
}

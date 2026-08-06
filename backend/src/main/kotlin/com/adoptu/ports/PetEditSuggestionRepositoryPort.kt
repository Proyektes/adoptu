package com.adoptu.ports

import com.adoptu.dto.input.CreatePetEditSuggestionRequest
import com.adoptu.dto.input.PetEditSuggestionDto
import com.adoptu.dto.input.PetEditSuggestionStatus

interface PetEditSuggestionRepositoryPort {
    suspend fun create(petId: Int, volunteerId: Int, request: CreatePetEditSuggestionRequest): PetEditSuggestionDto
    suspend fun getById(id: Int): PetEditSuggestionDto?
    // Joins through Pets to find every PENDING suggestion across all of this rescuer's pets -
    // suggestions don't store rescuerId directly, it's derived via the pet.
    suspend fun getPendingForRescuer(rescuerId: Int): List<PetEditSuggestionDto>
    suspend fun getForVolunteer(volunteerId: Int): List<PetEditSuggestionDto>
    suspend fun updateStatus(id: Int, status: PetEditSuggestionStatus, reviewedAt: Long): Boolean
}

package com.adoptu.services

import com.adoptu.dto.input.CreatePetMedicalEventRequest
import com.adoptu.dto.input.PetMedicalEventDto
import com.adoptu.ports.PetMedicalEventRepositoryPort
import com.adoptu.ports.PetRepositoryPort

class PetMedicalEventService(
    private val repository: PetMedicalEventRepositoryPort,
    private val petRepository: PetRepositoryPort
) {
    suspend fun getForPet(petId: Int): List<PetMedicalEventDto> = repository.getForPet(petId)

    suspend fun create(petId: Int, userId: Int, userRoles: Set<String>, request: CreatePetMedicalEventRequest): ServiceResult<PetMedicalEventDto> {
        val pet = petRepository.getById(petId) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && pet.rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        if (request.name.isBlank()) {
            return ServiceResult.Error("Name is required")
        }
        return ServiceResult.Success(repository.create(petId, request))
    }

    suspend fun delete(id: Int, userId: Int, userRoles: Set<String>): ServiceResult<Unit> {
        val existing = repository.getById(id) ?: return ServiceResult.NotFound
        val pet = petRepository.getById(existing.petId) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && pet.rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        repository.delete(id)
        return ServiceResult.Success(Unit)
    }
}

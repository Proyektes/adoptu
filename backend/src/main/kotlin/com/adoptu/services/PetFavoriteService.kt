package com.adoptu.services

import com.adoptu.dto.input.PetDto
import com.adoptu.ports.PetFavoriteRepositoryPort
import com.adoptu.ports.PetRepositoryPort

class PetFavoriteService(
    private val petFavoriteRepository: PetFavoriteRepositoryPort,
    private val petRepository: PetRepositoryPort
) {
    suspend fun add(userId: Int, petId: Int): ServiceResult<Unit> {
        petRepository.getById(petId) ?: return ServiceResult.NotFound
        petFavoriteRepository.add(userId, petId)
        return ServiceResult.Success(Unit)
    }

    suspend fun remove(userId: Int, petId: Int): ServiceResult<Unit> {
        petFavoriteRepository.remove(userId, petId)
        return ServiceResult.Success(Unit)
    }

    suspend fun getFavoritePetIds(userId: Int): List<Int> = petFavoriteRepository.getFavoritePetIds(userId)

    suspend fun getFavoritePets(userId: Int): List<PetDto> =
        petFavoriteRepository.getFavoritePetIds(userId).mapNotNull { petRepository.getById(it) }
}

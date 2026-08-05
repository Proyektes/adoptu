package com.adoptu.services

import com.adoptu.dto.input.CreateFosterPlacementRequest
import com.adoptu.dto.input.PetFosterPlacementDto
import com.adoptu.dto.input.UserRole
import com.adoptu.ports.PetFosterPlacementRepositoryPort
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.ports.TemporalHomeRepositoryPort

class PetFosterPlacementService(
    private val placementRepository: PetFosterPlacementRepositoryPort,
    private val petRepository: PetRepositoryPort,
    private val temporalHomeRepository: TemporalHomeRepositoryPort,
    private val userService: UserService
) {
    suspend fun createPlacement(
        petId: Int,
        userId: Int,
        userRoles: Set<String>,
        request: CreateFosterPlacementRequest
    ): ServiceResult<PetFosterPlacementDto> {
        val pet = petRepository.getById(petId) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && pet.rescuerId != userId) {
            return ServiceResult.Forbidden
        }

        val temporalHomeUser = userService.getById(request.temporalHomeId)
            ?: return ServiceResult.Error("Temporal home not found")
        if (!temporalHomeUser.activeRoles.contains(UserRole.TEMPORAL_HOME)) {
            return ServiceResult.Error("That user is not an active temporal home")
        }

        if (placementRepository.getActiveForPet(petId) != null) {
            return ServiceResult.Error("This pet already has an active foster placement - end it first")
        }

        val maxCapacity = temporalHomeRepository.getTemporalHome(request.temporalHomeId)?.maxCapacity
        if (maxCapacity != null && placementRepository.countActiveForTemporalHome(request.temporalHomeId) >= maxCapacity) {
            return ServiceResult.Error("This temporal home is at capacity")
        }

        return ServiceResult.Success(placementRepository.create(petId, request.temporalHomeId, request.notes))
    }

    // Rescuer/admin only - matches who can already edit the pet. The temporal home reports the
    // return informally (message/call); the rescuer records it here.
    suspend fun endPlacement(placementId: Int, userId: Int, userRoles: Set<String>): ServiceResult<PetFosterPlacementDto> {
        val placement = placementRepository.getById(placementId) ?: return ServiceResult.NotFound
        if (placement.endDate != null) {
            return ServiceResult.Error("This placement has already ended")
        }
        val pet = petRepository.getById(placement.petId) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && pet.rescuerId != userId) {
            return ServiceResult.Forbidden
        }

        placementRepository.endPlacement(placementId)
        return ServiceResult.Success(placementRepository.getById(placementId)!!)
    }

    suspend fun getHistoryForPet(petId: Int, userId: Int, userRoles: Set<String>): ServiceResult<List<PetFosterPlacementDto>> {
        val pet = petRepository.getById(petId) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && pet.rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        return ServiceResult.Success(placementRepository.getHistoryForPet(petId))
    }

    suspend fun getMyActivePlacements(temporalHomeId: Int): List<PetFosterPlacementDto> =
        placementRepository.getActiveForTemporalHome(temporalHomeId)
}

package com.adoptu.ports

import com.adoptu.dto.input.PetFosterPlacementDto

interface PetFosterPlacementRepositoryPort {
    suspend fun create(petId: Int, temporalHomeId: Int, notes: String?): PetFosterPlacementDto
    // Null when the pet has no active placement - i.e. it's implicitly with its rescuer, see
    // PetFosterPlacements' table-level doc comment in Models.kt.
    suspend fun getActiveForPet(petId: Int): PetFosterPlacementDto?
    suspend fun getHistoryForPet(petId: Int): List<PetFosterPlacementDto>
    suspend fun getActiveForTemporalHome(temporalHomeId: Int): List<PetFosterPlacementDto>
    suspend fun countActiveForTemporalHome(temporalHomeId: Int): Int
    suspend fun getById(id: Int): PetFosterPlacementDto?
    suspend fun endPlacement(id: Int): Boolean
}

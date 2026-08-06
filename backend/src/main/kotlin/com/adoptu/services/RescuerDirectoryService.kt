package com.adoptu.services

import com.adoptu.dto.input.RescuerDetailDto
import com.adoptu.dto.input.RescuerDirectoryDto
import com.adoptu.dto.input.UserRole
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.ports.UserRepositoryPort

// Kept separate from UserService rather than adding petRepository to its already-widely-injected
// constructor - this directory/volunteer concern doesn't need to ripple through every existing
// UserService call site and test module.
class RescuerDirectoryService(
    private val userRepository: UserRepositoryPort,
    private val petRepository: PetRepositoryPort
) {
    suspend fun getDirectory(): List<RescuerDirectoryDto> {
        return userRepository.getRescuers().map { rescuer ->
            RescuerDirectoryDto(
                userId = rescuer.id,
                displayName = rescuer.displayName,
                country = rescuer.country,
                availablePetCount = petRepository.getAvailableForRescuer(rescuer.id).size
            )
        }
    }

    suspend fun getDetail(id: Int): RescuerDetailDto? {
        val rescuer = userRepository.getById(id) ?: return null
        if (!rescuer.activeRoles.contains(UserRole.RESCUER)) return null
        return RescuerDetailDto(
            userId = rescuer.id,
            displayName = rescuer.displayName,
            country = rescuer.country,
            pets = petRepository.getAvailableForRescuer(id)
        )
    }
}

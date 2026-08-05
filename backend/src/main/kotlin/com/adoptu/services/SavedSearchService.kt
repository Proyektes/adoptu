package com.adoptu.services

import com.adoptu.dto.input.CreateSavedSearchRequest
import com.adoptu.dto.input.SavedSearchDto
import com.adoptu.ports.SavedSearchRepositoryPort

class SavedSearchService(
    private val savedSearchRepository: SavedSearchRepositoryPort
) {
    suspend fun create(userId: Int, request: CreateSavedSearchRequest): SavedSearchDto =
        savedSearchRepository.create(userId, request)

    suspend fun list(userId: Int): List<SavedSearchDto> = savedSearchRepository.getByUser(userId)

    suspend fun delete(id: Int, userId: Int): ServiceResult<Unit> {
        val existing = savedSearchRepository.getById(id) ?: return ServiceResult.NotFound
        if (existing.userId != userId) return ServiceResult.Forbidden
        savedSearchRepository.delete(id)
        return ServiceResult.Success(Unit)
    }
}

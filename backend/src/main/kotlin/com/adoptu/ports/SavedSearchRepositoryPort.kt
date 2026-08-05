package com.adoptu.ports

import com.adoptu.dto.input.CreateSavedSearchRequest
import com.adoptu.dto.input.SavedSearchDto

interface SavedSearchRepositoryPort {
    suspend fun create(userId: Int, request: CreateSavedSearchRequest): SavedSearchDto
    suspend fun getByUser(userId: Int): List<SavedSearchDto>
    suspend fun getById(id: Int): SavedSearchDto?
    suspend fun delete(id: Int): Boolean
    // type=null in a saved search means "any type" - matches every new pet of that country
    // regardless of the pet's own type.
    suspend fun getMatching(type: String, country: String): List<SavedSearchDto>
}

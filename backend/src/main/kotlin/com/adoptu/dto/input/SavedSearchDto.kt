package com.adoptu.dto.input

data class SavedSearchDto(
    val id: Int,
    val userId: Int,
    // null = any type. Matches PetsRoutes' GET /api/pets query param exactly.
    val type: String? = null,
    val country: String,
    val createdAt: Long
)

data class CreateSavedSearchRequest(
    val type: String? = null,
    val country: String
)

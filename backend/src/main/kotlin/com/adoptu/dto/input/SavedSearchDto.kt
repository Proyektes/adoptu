package com.adoptu.dto.input

import com.universaliun.formats.json.JsonDecodable
import com.universaliun.formats.json.JsonEncodable

@JsonDecodable(strict = false)
@JsonEncodable
data class SavedSearchDto(
    val id: Int,
    val userId: Int,
    // null = any type. Matches PetsRoutes' GET /api/pets query param exactly.
    val type: String? = null,
    val country: String,
    val createdAt: Long
)

@JsonDecodable(strict = false)
@JsonEncodable
data class CreateSavedSearchRequest(
    val type: String? = null,
    val country: String
)

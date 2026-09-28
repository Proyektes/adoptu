package com.adoptu.dto.input

import com.universaliun.formats.json.JsonDecodable
import com.universaliun.formats.json.JsonEncodable

// Deliberately NOT UserDto - GET /api/users/rescuers is public/unauthenticated, and UserDto
// carries username (the user's email), isBanned/banReason, etc. that must never be exposed to
// an anonymous caller. See the fix note on the route itself.
@JsonDecodable(strict = false)
@JsonEncodable
data class RescuerDirectoryDto(
    val userId: Int,
    val displayName: String,
    val country: String? = null,
    val availablePetCount: Int
)

@JsonDecodable(strict = false)
@JsonEncodable
data class RescuerDetailDto(
    val userId: Int,
    val displayName: String,
    val country: String? = null,
    val pets: List<PetDto>
)

package com.adoptu.dto.input


// Deliberately NOT UserDto - GET /api/users/rescuers is public/unauthenticated, and UserDto
// carries username (the user's email), isBanned/banReason, etc. that must never be exposed to
// an anonymous caller. See the fix note on the route itself.
data class RescuerDirectoryDto(
    val userId: Int,
    val displayName: String,
    val country: String? = null,
    val availablePetCount: Int
)

data class RescuerDetailDto(
    val userId: Int,
    val displayName: String,
    val country: String? = null,
    val pets: List<PetDto>
)

package com.adoptu.dto.input

import com.universaliun.formats.json.JsonDecodable
import com.universaliun.formats.json.JsonEncodable

enum class VolunteerStatus {
    PENDING, ACTIVE, REJECTED
}

@JsonDecodable(strict = false)
@JsonEncodable
data class VolunteerDto(
    val id: Int,
    val rescuerId: Int,
    val rescuerName: String? = null,
    val volunteerId: Int,
    val volunteerName: String? = null,
    val status: VolunteerStatus,
    val createdAt: Long
)

@JsonDecodable(strict = false)
@JsonEncodable
data class CreateVolunteerApplicationRequest(
    val rescuerId: Int
)

@JsonDecodable(strict = false)
@JsonEncodable
data class UpdateVolunteerStatusRequest(
    val status: VolunteerStatus
)

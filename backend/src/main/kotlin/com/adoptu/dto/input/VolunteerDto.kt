package com.adoptu.dto.input

enum class VolunteerStatus {
    PENDING, ACTIVE, REJECTED
}

data class VolunteerDto(
    val id: Int,
    val rescuerId: Int,
    val rescuerName: String? = null,
    val volunteerId: Int,
    val volunteerName: String? = null,
    val status: VolunteerStatus,
    val createdAt: Long
)

data class CreateVolunteerApplicationRequest(
    val rescuerId: Int
)

data class UpdateVolunteerStatusRequest(
    val status: VolunteerStatus
)

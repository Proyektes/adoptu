package com.adoptu.ports

import com.adoptu.dto.input.VolunteerDto
import com.adoptu.dto.input.VolunteerStatus

interface VolunteerRepositoryPort {
    suspend fun create(rescuerId: Int, volunteerId: Int): VolunteerDto
    suspend fun getById(id: Int): VolunteerDto?
    // Most recent application by this volunteer to this rescuer, regardless of status - used to
    // block a duplicate PENDING/ACTIVE application while still allowing re-application after a
    // REJECTED one.
    suspend fun getLatestForPair(rescuerId: Int, volunteerId: Int): VolunteerDto?
    suspend fun getForVolunteer(volunteerId: Int): List<VolunteerDto>
    suspend fun getForRescuer(rescuerId: Int): List<VolunteerDto>
    suspend fun isActiveVolunteerFor(rescuerId: Int, volunteerId: Int): Boolean
    suspend fun updateStatus(id: Int, status: VolunteerStatus): Boolean
}

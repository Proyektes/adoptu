package com.adoptu.services

import com.adoptu.dto.input.CreateVolunteerApplicationRequest
import com.adoptu.dto.input.UserRole
import com.adoptu.dto.input.VolunteerDto
import com.adoptu.dto.input.VolunteerStatus
import com.adoptu.ports.VolunteerRepositoryPort

class VolunteerService(
    private val volunteerRepository: VolunteerRepositoryPort,
    private val userService: UserService
) {
    suspend fun apply(volunteerId: Int, request: CreateVolunteerApplicationRequest): ServiceResult<VolunteerDto> {
        if (request.rescuerId == volunteerId) {
            return ServiceResult.Error("You can't volunteer for yourself")
        }
        val rescuer = userService.getById(request.rescuerId) ?: return ServiceResult.NotFound
        if (!rescuer.activeRoles.contains(UserRole.RESCUER)) {
            return ServiceResult.Error("That user is not an active rescuer")
        }

        val latest = volunteerRepository.getLatestForPair(request.rescuerId, volunteerId)
        if (latest != null && latest.status != VolunteerStatus.REJECTED) {
            return ServiceResult.Error("You already have a pending or active application with this rescuer")
        }

        return ServiceResult.Success(volunteerRepository.create(request.rescuerId, volunteerId))
    }

    // Rescuer/admin only - the rescuer being volunteered for is the only one who can approve or
    // reject their own incoming applications.
    suspend fun updateStatus(id: Int, status: VolunteerStatus, userId: Int, userRoles: Set<String>): ServiceResult<VolunteerDto> {
        val application = volunteerRepository.getById(id) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && application.rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        if (status == VolunteerStatus.PENDING) {
            return ServiceResult.Error("Cannot set an application back to pending")
        }

        volunteerRepository.updateStatus(id, status)
        return ServiceResult.Success(volunteerRepository.getById(id)!!)
    }

    suspend fun getMyApplications(volunteerId: Int): List<VolunteerDto> =
        volunteerRepository.getForVolunteer(volunteerId)

    suspend fun getApplicationsForRescuer(rescuerId: Int, userId: Int, userRoles: Set<String>): ServiceResult<List<VolunteerDto>> {
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        return ServiceResult.Success(volunteerRepository.getForRescuer(rescuerId))
    }

    suspend fun isActiveVolunteerFor(rescuerId: Int, volunteerId: Int): Boolean =
        volunteerRepository.isActiveVolunteerFor(rescuerId, volunteerId)
}

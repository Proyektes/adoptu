package com.adoptu.ports

import com.adoptu.dto.input.PhotographerDto
import com.adoptu.dto.input.PhotographerSettingsRequest
import com.adoptu.dto.input.PhotographyRequestDto
import com.adoptu.dto.input.UserDto

interface PhotographerRepositoryPort {
    suspend fun canSendMessage(userId: Int): Boolean
    suspend fun createPhotographyRequest(requesterId: Int, photographerId: Int, petId: Int?, message: String): Int
    suspend fun getMyRequests(userId: Int): List<PhotographyRequestDto>
    suspend fun getRequestsForPhotographer(photographerId: Int): List<PhotographyRequestDto>
    suspend fun getRequestById(requestId: Int): PhotographyRequestDto?
    suspend fun updatePhotographyRequest(requestId: Int, status: String?, scheduledDate: Long?)
    suspend fun getPhotographerById(userId: Int): PhotographerDto?
    suspend fun getOwnPhotographerSettings(userId: Int): PhotographerDto?
    suspend fun getPhotographers(country: String? = null, state: String? = null): List<PhotographerDto>
    suspend fun updatePhotographerSettings(userId: Int, request: PhotographerSettingsRequest): PhotographerDto?
    suspend fun activatePhotographerProfile(userId: Int): UserDto?
    suspend fun deactivatePhotographerProfile(userId: Int): UserDto?
}

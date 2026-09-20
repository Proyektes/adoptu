package com.adoptu.services

import com.adoptu.dto.input.CreatePhotographyRequestRequest
import com.adoptu.dto.input.PhotographerDto
import com.adoptu.dto.input.PhotographerSettingsRequest
import com.adoptu.dto.input.UpdatePhotographyRequestRequest
import com.adoptu.dto.input.UserDto
import com.adoptu.ports.NotificationPort
import com.adoptu.ports.PhotographerRepositoryPort
import com.adoptu.ports.UserRepositoryPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PhotographerService(
    private val photographerRepository: PhotographerRepositoryPort,
    private val notificationAdapter: NotificationPort?,
    private val userRepository: UserRepositoryPort,
    private val clock: Clock
) {
    suspend fun getPhotographers(country: String? = null, state: String? = null): List<PhotographerDto> =
        photographerRepository.getPhotographers(country, state)

    suspend fun getPhotographerById(userId: Int): PhotographerDto? = photographerRepository.getPhotographerById(userId)

    suspend fun updatePhotographerSettings(userId: Int, request: PhotographerSettingsRequest): PhotographerDto? =
        photographerRepository.updatePhotographerSettings(userId, request)

    suspend fun canSendMessage(userId: Int): Boolean = photographerRepository.canSendMessage(userId)

    suspend fun createPhotographyRequest(
        requesterId: Int,
        photographerIds: List<Int>,
        petId: Int?,
        message: String
    ): Result<List<Int>> {
        if (photographerIds.isEmpty()) {
            return Result.failure(IllegalArgumentException("At least one photographer must be selected"))
        }
        if (photographerIds.size > 3) {
            return Result.failure(IllegalArgumentException("Maximum 3 photographers can be selected"))
        }

        if (!canSendMessage(requesterId)) {
            return Result.failure(IllegalArgumentException("You can only send photographer requests once per week"))
        }

        val requester = userRepository.getById(requesterId)
            ?: return Result.failure(IllegalArgumentException("User not found"))

        val createdRequestIds = mutableListOf<Int>()

        for (photographerId in photographerIds) {
            val photographer = photographerRepository.getPhotographers().find { it.userId == photographerId }
                ?: continue

            val requestId = photographerRepository.createPhotographyRequest(
                requesterId = requesterId,
                photographerId = photographerId,
                petId = petId,
                message = message
            )
            createdRequestIds.add(requestId)

            val adapter = notificationAdapter
            val photo = photographer
            val req = requester
            CoroutineScope(Dispatchers.IO).launch {
                adapter?.sendPhotographerRequest(
                    photographerEmail = photo.username ?: "",
                    photographerName = photo.displayName,
                    requesterName = req.displayName,
                    petName = null,
                    message = message,
                    fee = photo.photographerFee,
                    currency = photo.photographerCurrency
                )
            }
        }

        return Result.success(createdRequestIds)
    }

    suspend fun getMyRequests(userId: Int): List<Map<String, Any?>> {
        return photographerRepository.getMyRequests(userId).map { dto ->
            mapOf(
                "id" to dto.id,
                "photographerId" to dto.photographerId,
                "photographerName" to dto.photographerName,
                "photographerFee" to null,
                "photographerCurrency" to null,
                "petId" to dto.petId,
                "message" to dto.message,
                "status" to dto.status,
                "scheduledDate" to dto.scheduledDate,
                "createdAt" to dto.createdAt
            )
        }
    }

    suspend fun getRequestsForPhotographer(photographerId: Int): List<Map<String, Any?>> {
        return photographerRepository.getRequestsForPhotographer(photographerId).map { dto ->
            mapOf(
                "id" to dto.id,
                "requesterId" to dto.requesterId,
                "requesterName" to dto.requesterName,
                "petId" to dto.petId,
                "petName" to dto.petName,
                "message" to dto.message,
                "status" to dto.status,
                "scheduledDate" to dto.scheduledDate,
                "createdAt" to dto.createdAt
            )
        }
    }

    suspend fun createPhotographyRequest(
        requesterId: Int,
        photographerId: Int,
        petId: Int?,
        message: String?
    ): Map<String, Any?> {
        val photographer = getPhotographerById(photographerId)
            ?: throw IllegalArgumentException("Photographer not found or not active")

        val createdAt = clock.now().toEpochMilliseconds()
        val requestId = photographerRepository.createPhotographyRequest(
            requesterId = requesterId,
            photographerId = photographerId,
            petId = petId,
            message = message ?: ""
        )

        return mapOf(
            "id" to requestId,
            "photographerId" to photographerId,
            "photographerName" to photographer.displayName,
            "requesterId" to requesterId,
            "petId" to petId,
            "message" to message,
            "status" to "PENDING",
            "createdAt" to createdAt
        )
    }

    suspend fun getRequestsForUser(user: UserDto, scope: String? = null): List<Map<String, Any?>> {
        val activeRoles = user.activeRoles.map { it.name }
        val received = when (scope) {
            "received" -> true
            "sent" -> false
            else -> activeRoles.contains("PHOTOGRAPHER")
        }

        return if (received) {
            photographerRepository.getRequestsForPhotographer(user.id).map { dto ->
                val requester = userRepository.getById(dto.requesterId)
                mapOf(
                    "id" to dto.id,
                    "photographerId" to dto.photographerId,
                    "photographerName" to dto.photographerName,
                    "requesterId" to dto.requesterId,
                    "requesterName" to requester?.displayName,
                    "petId" to dto.petId,
                    "message" to dto.message,
                    "status" to dto.status,
                    "scheduledDate" to dto.scheduledDate,
                    "createdAt" to dto.createdAt
                )
            }
        } else {
            photographerRepository.getMyRequests(user.id).map { dto ->
                val photographer = userRepository.getPhotographers().find { it.userId == dto.photographerId }
                mapOf(
                    "id" to dto.id,
                    "photographerId" to dto.photographerId,
                    "photographerName" to photographer?.displayName,
                    "requesterId" to dto.requesterId,
                    "requesterName" to dto.requesterName,
                    "petId" to dto.petId,
                    "message" to dto.message,
                    "status" to dto.status,
                    "scheduledDate" to dto.scheduledDate,
                    "createdAt" to dto.createdAt
                )
            }
        }
    }

    suspend fun activatePhotographerProfile(userId: Int): UserDto? = photographerRepository.activatePhotographerProfile(userId)

    suspend fun deactivatePhotographerProfile(userId: Int): UserDto? = photographerRepository.deactivatePhotographerProfile(userId)

    suspend fun updatePhotographyRequest(
        userId: Int,
        user: UserDto?,
        requestId: Int,
        body: UpdatePhotographyRequestRequest
    ): ServiceResult<Map<String, Any?>> {
        val activeRoles = user?.activeRoles?.map { it.name } ?: emptyList()

        val existing = photographerRepository.getRequestById(requestId)
            ?: return ServiceResult.NotFound

        val isPhotographer = existing.photographerId == userId
        val isRequester = existing.requesterId == userId
        val isAdmin = activeRoles.contains("ADMIN")

        if (!isPhotographer && !isRequester && !isAdmin) {
            return ServiceResult.Forbidden
        }

        if (body.status != null) {
            val validTransitions = if (isPhotographer || isAdmin) {
                mapOf("PENDING" to listOf("APPROVED", "REJECTED", "CANCELLED"),
                    "APPROVED" to listOf("COMPLETED", "CANCELLED"))
            } else {
                mapOf("PENDING" to listOf("CANCELLED"))
            }
            val currentStatus = existing.status
            if (body.status !in (validTransitions[currentStatus] ?: emptyList())) {
                throw IllegalArgumentException("Invalid status transition")
            }
        }

        photographerRepository.updatePhotographyRequest(requestId, body.status, body.scheduledDate)

        val updated = photographerRepository.getRequestById(requestId)!!
        val photographer = userRepository.getById(updated.photographerId)
        val requester = userRepository.getById(updated.requesterId)

        return ServiceResult.Success(
            mapOf(
                "id" to updated.id,
                "photographerId" to updated.photographerId,
                "photographerName" to photographer?.displayName,
                "requesterId" to updated.requesterId,
                "requesterName" to requester?.displayName,
                "petId" to updated.petId,
                "message" to updated.message,
                "status" to updated.status,
                "scheduledDate" to updated.scheduledDate,
                "createdAt" to updated.createdAt
            )
        )
    }
}

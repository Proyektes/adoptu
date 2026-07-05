package com.adoptu.services

import com.adoptu.dto.input.CreateTemporalHomeRequest
import com.adoptu.dto.input.SendTemporalHomeRequestRequest
import com.adoptu.dto.input.TemporalHomeDto
import com.adoptu.dto.input.TemporalHomeRequestDto
import com.adoptu.dto.input.TemporalHomeSearchParams
import com.adoptu.dto.input.UpdateTemporalHomeRequest
import com.adoptu.dto.input.UserDto
import com.adoptu.dto.input.UserRole
import com.adoptu.ports.NotificationPort
import com.adoptu.ports.TemporalHomeRepositoryPort
import com.adoptu.ports.UserRepositoryPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TemporalHomeService(
    private val temporalHomeRepository: TemporalHomeRepositoryPort,
    private val notificationAdapter: NotificationPort,
    private val userService: UserService,
    private val userRepository: UserRepositoryPort,
    private val baseUrl: String = "http://localhost:80"
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    suspend fun getTemporalHome(userId: Int): TemporalHomeDto? = temporalHomeRepository.getTemporalHome(userId)

    suspend fun createTemporalHome(userId: Int, request: CreateTemporalHomeRequest): TemporalHomeDto =
        temporalHomeRepository.createTemporalHome(userId, request)

    suspend fun updateTemporalHome(userId: Int, request: UpdateTemporalHomeRequest): TemporalHomeDto? =
        temporalHomeRepository.updateTemporalHome(userId, request)

    suspend fun searchTemporalHomes(params: TemporalHomeSearchParams): List<TemporalHomeDto> =
        temporalHomeRepository.searchTemporalHomes(params)

    suspend fun sendRequest(requesterId: Int, request: SendTemporalHomeRequestRequest): Result<Int> {
        val temporalHome = getTemporalHome(request.temporalHomeId)
            ?: return Result.failure(IllegalArgumentException("Temporal home not found"))

        val isBlocked = temporalHomeRepository.isBlocked(request.temporalHomeId, requesterId)
        if (isBlocked) {
            return Result.failure(IllegalArgumentException("You have been blocked by this temporal home"))
        }

        val requester = userService.getById(requesterId)
            ?: return Result.failure(IllegalArgumentException("User not found"))

        if (!requester.activeRoles.contains(UserRole.RESCUER) && !requester.activeRoles.contains(UserRole.ADMIN)) {
            return Result.failure(IllegalArgumentException("Only rescuers can send temporal home requests"))
        }

        val createdRequestId = temporalHomeRepository.createTemporalHomeRequest(
            temporalHomeId = request.temporalHomeId,
            rescuerId = requesterId,
            petId = request.petId,
            message = request.message
        )

        val temporalHomeEmail = userService.getById(request.temporalHomeId)?.username
        if (temporalHomeEmail != null) {
            // A signed, single-use token rather than the raw temporalHomeId/rescuerId in
            // the URL - this link must work without the recipient being logged in, and
            // those IDs are guessable sequential integers with no secret component.
            val token = temporalHomeRepository.createSpamReportToken(request.temporalHomeId, requesterId)
            val spamReportLink = "$baseUrl/temporal-home/block?token=$token"
            val pet = if (request.petId != null) temporalHomeRepository.getTemporalHome(request.temporalHomeId) else null

            scope.launch {
                notificationAdapter.sendTemporalHomeRequest(
                    temporalHomeEmail = temporalHomeEmail,
                    temporalHomeAlias = temporalHome.alias,
                    rescuerName = requester.displayName,
                    petName = pet?.alias,
                    message = request.message,
                    spamReportLink = spamReportLink
                )
            }
        }

        return Result.success(createdRequestId)
    }

    suspend fun isBlocked(temporalHomeId: Int, rescuerId: Int): Boolean =
        temporalHomeRepository.isBlocked(temporalHomeId, rescuerId)

    suspend fun blockRescuer(temporalHomeId: Int, rescuerId: Int): Boolean =
        temporalHomeRepository.blockRescuer(temporalHomeId, rescuerId)

    /** Validates and consumes a spam-report token (see sendRequest), then blocks the rescuer it names. */
    suspend fun blockRescuerByToken(token: String): Boolean {
        val (temporalHomeId, rescuerId) = temporalHomeRepository.consumeSpamReportToken(token) ?: return false
        return temporalHomeRepository.blockRescuer(temporalHomeId, rescuerId)
    }

    suspend fun getMyRequests(userId: Int): List<TemporalHomeRequestDto> =
        temporalHomeRepository.getMyRequests(userId)

    suspend fun activateTemporalHomeProfile(userId: Int): UserDto? = userRepository.activateTemporalHomeProfile(userId)

    suspend fun deactivateTemporalHomeProfile(userId: Int): UserDto? = userRepository.deactivateTemporalHomeProfile(userId)
}

package com.adoptu.ports

import com.adoptu.dto.input.CreateTemporalHomeRequest
import com.adoptu.dto.input.TemporalHomeDto
import com.adoptu.dto.input.TemporalHomeRequestDto
import com.adoptu.dto.input.TemporalHomeSearchParams
import com.adoptu.dto.input.UpdateTemporalHomeRequest

interface TemporalHomeRepositoryPort {
    suspend fun getTemporalHome(userId: Int): TemporalHomeDto?
    suspend fun createTemporalHome(userId: Int, request: CreateTemporalHomeRequest): TemporalHomeDto
    suspend fun updateTemporalHome(userId: Int, request: UpdateTemporalHomeRequest): TemporalHomeDto?
    suspend fun searchTemporalHomes(params: TemporalHomeSearchParams): List<TemporalHomeDto>
    suspend fun createTemporalHomeRequest(temporalHomeId: Int, rescuerId: Int, petId: Int?, message: String): Int
    suspend fun isBlocked(temporalHomeId: Int, rescuerId: Int): Boolean
    suspend fun blockRescuer(temporalHomeId: Int, rescuerId: Int): Boolean
    suspend fun getMyRequests(userId: Int): List<TemporalHomeRequestDto>
    suspend fun createSpamReportToken(temporalHomeId: Int, rescuerId: Int): String
    /** Returns (temporalHomeId, rescuerId) and marks the token used, or null if invalid/expired/already used. */
    suspend fun consumeSpamReportToken(token: String): Pair<Int, Int>?
}

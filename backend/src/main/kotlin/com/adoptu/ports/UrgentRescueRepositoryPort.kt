package com.adoptu.ports

import com.adoptu.dto.input.CreateUrgentRescuerProfileRequest
import com.adoptu.dto.input.SubmitUrgentReportRequest
import com.adoptu.dto.input.UpdateUrgentRescuerProfileRequest
import com.adoptu.dto.input.UrgentReportDto
import com.adoptu.dto.input.UrgentReportPageDto
import com.adoptu.dto.input.UrgentRescuerLeaderboardEntryDto
import com.adoptu.dto.input.UrgentRescuerProfileDto

interface UrgentRescueRepositoryPort {
    suspend fun getProfile(userId: Int): UrgentRescuerProfileDto?
    suspend fun createProfile(
        userId: Int,
        request: CreateUrgentRescuerProfileRequest,
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): UrgentRescuerProfileDto
    suspend fun updateProfile(
        userId: Int,
        request: UpdateUrgentRescuerProfileRequest,
        latitude: Double?,
        longitude: Double?,
        radiusKm: Double?
    ): UrgentRescuerProfileDto?

    /** Every currently-active urgent-rescuer profile - matching filters this list by distance in Kotlin. */
    suspend fun getActiveProfiles(): List<UrgentRescuerProfileDto>

    suspend fun createReport(
        request: SubmitUrgentReportRequest,
        reporterUserId: Int?,
        reporterEmail: String,
        latitude: Double,
        longitude: Double,
        locationLabel: String
    ): UrgentReportDto
    suspend fun getReport(reportId: Int): UrgentReportDto?

    /** Single conditional UPDATE ... WHERE status='PENDING' - returns true only for the winner of the race. */
    suspend fun tryAcceptReport(reportId: Int, rescuerId: Int): Boolean

    suspend fun createReportPage(reportId: Int, rescuerId: Int): UrgentReportPageDto
    /** After [tryAcceptReport] wins: sets [winningRescuerId]'s page ACCEPTED and every other PAGED page for [reportId] MISSED. */
    suspend fun resolvePages(reportId: Int, winningRescuerId: Int)
    suspend fun getPendingPagesForRescuer(rescuerId: Int): List<UrgentReportPageDto>
    /** Looks up (reportId, rescuerId) for an unresolved page token, without mutating anything - the caller drives the actual accept race via [tryAcceptReport]. Null if the token is unknown or its page is no longer PAGED. */
    suspend fun resolveAcceptToken(token: String): Pair<Int, Int>?

    suspend fun getLeaderboard(sinceEpochMs: Long): List<UrgentRescuerLeaderboardEntryDto>
}

package com.adoptu.ports

import com.adoptu.dto.input.LostFoundKind
import com.adoptu.dto.input.LostFoundReportDto
import com.adoptu.dto.input.SubmitLostFoundReportRequest

interface LostFoundRepositoryPort {
    suspend fun createReport(
        request: SubmitLostFoundReportRequest,
        reporterUserId: Int?,
        reporterEmail: String,
        latitude: Double,
        longitude: Double,
        locationLabel: String,
        lastSeenAt: Long
    ): LostFoundReportDto

    suspend fun getReport(id: Int): LostFoundReportDto?

    /** Every currently-OPEN report of [kind] - matching/browsing filter this list in Kotlin (small volume, same reasoning as UrgentRescueRepositoryPort.getActiveProfiles). */
    suspend fun getOpenReports(kind: LostFoundKind): List<LostFoundReportDto>

    suspend fun markResolved(id: Int): LostFoundReportDto?

    /** Looks up the report id for an unresolved single-use resolve token, without mutating anything. Null if unknown or already resolved. */
    suspend fun resolveToken(token: String): Int?
}

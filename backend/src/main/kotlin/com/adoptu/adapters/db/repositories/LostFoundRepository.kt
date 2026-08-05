package com.adoptu.adapters.db.repositories

import com.adoptu.adapters.db.LostFoundReports
import com.adoptu.adapters.db.dbDispatcher
import com.adoptu.common.Country
import com.adoptu.dto.input.LostFoundKind
import com.adoptu.dto.input.LostFoundReportDto
import com.adoptu.dto.input.LostFoundStatus
import com.adoptu.dto.input.SubmitLostFoundReportRequest
import com.adoptu.ports.LostFoundRepositoryPort
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class LostFoundRepositoryImpl(
    private val clock: Clock
) : LostFoundRepositoryPort {

    private val secureRandom = SecureRandom()

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun rowToReport(row: org.jetbrains.exposed.v1.core.ResultRow): LostFoundReportDto = LostFoundReportDto(
        id = row[LostFoundReports.id],
        kind = LostFoundKind.valueOf(row[LostFoundReports.kind]),
        reporterUserId = row[LostFoundReports.reporterUserId],
        reporterEmail = row[LostFoundReports.reporterEmail],
        reporterPhone = row[LostFoundReports.reporterPhone],
        petType = row[LostFoundReports.petType],
        description = row[LostFoundReports.description],
        photoUrl = row[LostFoundReports.photoUrl],
        latitude = row[LostFoundReports.latitude],
        longitude = row[LostFoundReports.longitude],
        locationLabel = row[LostFoundReports.locationLabel],
        country = row[LostFoundReports.country].displayName,
        lastSeenAt = row[LostFoundReports.lastSeenAt],
        status = LostFoundStatus.valueOf(row[LostFoundReports.status]),
        createdAt = row[LostFoundReports.createdAt]
    )

    override suspend fun createReport(
        request: SubmitLostFoundReportRequest,
        reporterUserId: Int?,
        reporterEmail: String,
        latitude: Double,
        longitude: Double,
        locationLabel: String,
        lastSeenAt: Long
    ): LostFoundReportDto = withContext(dbDispatcher) {
        val createdAt = clock.now().toEpochMilliseconds()
        val token = generateToken()
        val country = Country.fromDisplayName(request.country)
            ?: throw IllegalArgumentException("Invalid country: ${request.country}")
        transaction {
            val id = LostFoundReports.insert {
                it[LostFoundReports.kind] = request.kind.name
                it[LostFoundReports.reporterUserId] = reporterUserId
                it[LostFoundReports.reporterEmail] = reporterEmail
                it[LostFoundReports.reporterPhone] = request.reporterPhone
                it[LostFoundReports.petType] = request.petType
                it[LostFoundReports.description] = request.description
                it[LostFoundReports.photoUrl] = null
                it[LostFoundReports.latitude] = latitude
                it[LostFoundReports.longitude] = longitude
                it[LostFoundReports.locationLabel] = locationLabel
                it[LostFoundReports.country] = country
                it[LostFoundReports.lastSeenAt] = lastSeenAt
                it[LostFoundReports.status] = LostFoundStatus.OPEN.name
                it[LostFoundReports.resolveToken] = token
                it[LostFoundReports.createdAt] = createdAt
            } get LostFoundReports.id

            LostFoundReportDto(
                id = id,
                kind = request.kind,
                reporterUserId = reporterUserId,
                reporterEmail = reporterEmail,
                reporterPhone = request.reporterPhone,
                petType = request.petType,
                description = request.description,
                photoUrl = null,
                latitude = latitude,
                longitude = longitude,
                locationLabel = locationLabel,
                country = country.displayName,
                lastSeenAt = lastSeenAt,
                status = LostFoundStatus.OPEN,
                createdAt = createdAt,
                resolveToken = token
            )
        }
    }

    override suspend fun getReport(id: Int): LostFoundReportDto? = withContext(dbDispatcher) {
        transaction {
            LostFoundReports.selectAll()
                .where { LostFoundReports.id eq id }
                .firstOrNull()
                ?.let(::rowToReport)
        }
    }

    override suspend fun getOpenReports(kind: LostFoundKind): List<LostFoundReportDto> = withContext(dbDispatcher) {
        transaction {
            LostFoundReports.selectAll()
                .where { (LostFoundReports.kind eq kind.name) and (LostFoundReports.status eq LostFoundStatus.OPEN.name) }
                .map(::rowToReport)
        }
    }

    override suspend fun markResolved(id: Int): LostFoundReportDto? = withContext(dbDispatcher) {
        transaction {
            val updated = LostFoundReports.update({ LostFoundReports.id eq id }) {
                it[LostFoundReports.status] = LostFoundStatus.RESOLVED.name
            }
            if (updated == 0) return@transaction null
            LostFoundReports.selectAll()
                .where { LostFoundReports.id eq id }
                .first()
                .let(::rowToReport)
        }
    }

    override suspend fun resolveToken(token: String): Int? = withContext(dbDispatcher) {
        transaction {
            LostFoundReports.selectAll()
                .where { (LostFoundReports.resolveToken eq token) and (LostFoundReports.status eq LostFoundStatus.OPEN.name) }
                .firstOrNull()
                ?.get(LostFoundReports.id)
        }
    }
}

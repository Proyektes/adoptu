package com.adoptu.adapters.db.repositories

import com.adoptu.adapters.db.UrgentReportPages
import com.adoptu.adapters.db.UrgentReports
import com.adoptu.adapters.db.UrgentRescuerProfiles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.dbDispatcher
import com.adoptu.common.Country
import com.adoptu.dto.input.CreateUrgentRescuerProfileRequest
import com.adoptu.dto.input.LocationInputMode
import com.adoptu.dto.input.SubmitUrgentReportRequest
import com.adoptu.dto.input.UpdateUrgentRescuerProfileRequest
import com.adoptu.dto.input.UrgentDangerType
import com.adoptu.dto.input.UrgentReportDto
import com.adoptu.dto.input.UrgentReportPageDto
import com.adoptu.dto.input.UrgentReportPageStatus
import com.adoptu.dto.input.UrgentReportStatus
import com.adoptu.dto.input.UrgentRescuerLeaderboardEntryDto
import com.adoptu.dto.input.UrgentRescuerProfileDto
import com.adoptu.ports.UrgentRescueRepositoryPort
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class UrgentRescueRepositoryImpl(
    private val clock: Clock
) : UrgentRescueRepositoryPort {

    private val secureRandom = SecureRandom()

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun rowToProfile(row: org.jetbrains.exposed.v1.core.ResultRow): UrgentRescuerProfileDto = UrgentRescuerProfileDto(
        userId = row[UrgentRescuerProfiles.userId],
        phone = row[UrgentRescuerProfiles.phone],
        latitude = row[UrgentRescuerProfiles.latitude],
        longitude = row[UrgentRescuerProfiles.longitude],
        radiusKm = row[UrgentRescuerProfiles.radiusKm],
        inputMode = LocationInputMode.valueOf(row[UrgentRescuerProfiles.inputMode]),
        zoneCountry = row[UrgentRescuerProfiles.zoneCountry]?.displayName,
        zoneState = row[UrgentRescuerProfiles.zoneState],
        zoneCity = row[UrgentRescuerProfiles.zoneCity],
        active = row[UrgentRescuerProfiles.active],
        createdAt = row[UrgentRescuerProfiles.createdAt]
    )

    private fun rowToReport(row: org.jetbrains.exposed.v1.core.ResultRow): UrgentReportDto = UrgentReportDto(
        id = row[UrgentReports.id],
        reporterUserId = row[UrgentReports.reporterUserId],
        reporterEmail = row[UrgentReports.reporterEmail],
        reporterPhone = row[UrgentReports.reporterPhone],
        description = row[UrgentReports.description],
        dangerType = UrgentDangerType.valueOf(row[UrgentReports.dangerType]),
        photoUrl = row[UrgentReports.photoUrl],
        latitude = row[UrgentReports.latitude],
        longitude = row[UrgentReports.longitude],
        locationLabel = row[UrgentReports.locationLabel],
        street = row[UrgentReports.street],
        exteriorNumber = row[UrgentReports.exteriorNumber],
        referenceNotes = row[UrgentReports.referenceNotes],
        status = UrgentReportStatus.valueOf(row[UrgentReports.status]),
        acceptedByUserId = row[UrgentReports.acceptedByUserId],
        acceptedAt = row[UrgentReports.acceptedAt],
        createdAt = row[UrgentReports.createdAt]
    )

    private fun rowToPage(row: org.jetbrains.exposed.v1.core.ResultRow): UrgentReportPageDto = UrgentReportPageDto(
        id = row[UrgentReportPages.id],
        reportId = row[UrgentReportPages.reportId],
        rescuerId = row[UrgentReportPages.rescuerId],
        token = row[UrgentReportPages.token],
        status = UrgentReportPageStatus.valueOf(row[UrgentReportPages.status]),
        createdAt = row[UrgentReportPages.createdAt]
    )

    override suspend fun getProfile(userId: Int): UrgentRescuerProfileDto? = withContext(dbDispatcher) {
        transaction {
            UrgentRescuerProfiles.selectAll()
                .where { UrgentRescuerProfiles.userId eq userId }
                .firstOrNull()
                ?.let(::rowToProfile)
        }
    }

    override suspend fun createProfile(
        userId: Int,
        request: CreateUrgentRescuerProfileRequest,
        latitude: Double,
        longitude: Double,
        radiusKm: Double
    ): UrgentRescuerProfileDto = withContext(dbDispatcher) {
        val createdAt = clock.now().toEpochMilliseconds()
        val zoneCountry = request.zoneCountry?.let {
            Country.fromDisplayName(it) ?: throw IllegalArgumentException("Invalid country: $it")
        }
        transaction {
            UrgentRescuerProfiles.insert {
                it[UrgentRescuerProfiles.userId] = userId
                it[UrgentRescuerProfiles.phone] = request.phone
                it[UrgentRescuerProfiles.latitude] = latitude
                it[UrgentRescuerProfiles.longitude] = longitude
                it[UrgentRescuerProfiles.radiusKm] = radiusKm
                it[UrgentRescuerProfiles.inputMode] = request.inputMode.name
                it[UrgentRescuerProfiles.zoneCountry] = zoneCountry
                it[UrgentRescuerProfiles.zoneState] = request.zoneState
                it[UrgentRescuerProfiles.zoneCity] = request.zoneCity
                it[UrgentRescuerProfiles.active] = true
                it[UrgentRescuerProfiles.createdAt] = createdAt
            }
        }
        UrgentRescuerProfileDto(
            userId = userId,
            phone = request.phone,
            latitude = latitude,
            longitude = longitude,
            radiusKm = radiusKm,
            inputMode = request.inputMode,
            zoneCountry = zoneCountry?.displayName,
            zoneState = request.zoneState,
            zoneCity = request.zoneCity,
            active = true,
            createdAt = createdAt
        )
    }

    override suspend fun updateProfile(
        userId: Int,
        request: UpdateUrgentRescuerProfileRequest,
        latitude: Double?,
        longitude: Double?,
        radiusKm: Double?
    ): UrgentRescuerProfileDto? = withContext(dbDispatcher) {
        transaction {
            val existing = UrgentRescuerProfiles.selectAll()
                .where { UrgentRescuerProfiles.userId eq userId }
                .firstOrNull() ?: return@transaction null

            val zoneCountry = request.zoneCountry?.let {
                Country.fromDisplayName(it) ?: throw IllegalArgumentException("Invalid country: $it")
            } ?: existing[UrgentRescuerProfiles.zoneCountry]

            UrgentRescuerProfiles.update({ UrgentRescuerProfiles.userId eq userId }) {
                it[UrgentRescuerProfiles.phone] = request.phone ?: existing[UrgentRescuerProfiles.phone]
                it[UrgentRescuerProfiles.active] = request.active ?: existing[UrgentRescuerProfiles.active]
                it[UrgentRescuerProfiles.inputMode] = (request.inputMode?.name) ?: existing[UrgentRescuerProfiles.inputMode]
                it[UrgentRescuerProfiles.latitude] = latitude ?: existing[UrgentRescuerProfiles.latitude]
                it[UrgentRescuerProfiles.longitude] = longitude ?: existing[UrgentRescuerProfiles.longitude]
                it[UrgentRescuerProfiles.radiusKm] = radiusKm ?: existing[UrgentRescuerProfiles.radiusKm]
                it[UrgentRescuerProfiles.zoneCountry] = zoneCountry
                it[UrgentRescuerProfiles.zoneState] = request.zoneState ?: existing[UrgentRescuerProfiles.zoneState]
                it[UrgentRescuerProfiles.zoneCity] = request.zoneCity ?: existing[UrgentRescuerProfiles.zoneCity]
            }

            UrgentRescuerProfiles.selectAll()
                .where { UrgentRescuerProfiles.userId eq userId }
                .first()
                .let(::rowToProfile)
        }
    }

    override suspend fun getActiveProfiles(): List<UrgentRescuerProfileDto> = withContext(dbDispatcher) {
        transaction {
            UrgentRescuerProfiles.selectAll()
                .where { UrgentRescuerProfiles.active eq true }
                .map(::rowToProfile)
        }
    }

    override suspend fun createReport(
        request: SubmitUrgentReportRequest,
        reporterUserId: Int?,
        reporterEmail: String,
        latitude: Double,
        longitude: Double,
        locationLabel: String
    ): UrgentReportDto = withContext(dbDispatcher) {
        val createdAt = clock.now().toEpochMilliseconds()
        transaction {
            val id = UrgentReports.insert {
                it[UrgentReports.reporterUserId] = reporterUserId
                it[UrgentReports.reporterEmail] = reporterEmail
                it[UrgentReports.reporterPhone] = request.reporterPhone
                it[UrgentReports.description] = request.description
                it[UrgentReports.dangerType] = request.dangerType.name
                it[UrgentReports.photoUrl] = null
                it[UrgentReports.latitude] = latitude
                it[UrgentReports.longitude] = longitude
                it[UrgentReports.locationLabel] = locationLabel
                it[UrgentReports.street] = request.street
                it[UrgentReports.exteriorNumber] = request.exteriorNumber
                it[UrgentReports.referenceNotes] = request.referenceNotes
                it[UrgentReports.status] = UrgentReportStatus.PENDING.name
                it[UrgentReports.createdAt] = createdAt
            } get UrgentReports.id

            UrgentReportDto(
                id = id,
                reporterUserId = reporterUserId,
                reporterEmail = reporterEmail,
                reporterPhone = request.reporterPhone,
                description = request.description,
                dangerType = request.dangerType,
                photoUrl = null,
                latitude = latitude,
                longitude = longitude,
                locationLabel = locationLabel,
                street = request.street,
                exteriorNumber = request.exteriorNumber,
                referenceNotes = request.referenceNotes,
                status = UrgentReportStatus.PENDING,
                createdAt = createdAt
            )
        }
    }

    override suspend fun getReport(reportId: Int): UrgentReportDto? = withContext(dbDispatcher) {
        transaction {
            UrgentReports.selectAll()
                .where { UrgentReports.id eq reportId }
                .firstOrNull()
                ?.let(::rowToReport)
        }
    }

    override suspend fun tryAcceptReport(reportId: Int, rescuerId: Int): Boolean = withContext(dbDispatcher) {
        val now = clock.now().toEpochMilliseconds()
        transaction {
            val updatedRows = UrgentReports.update({
                (UrgentReports.id eq reportId) and (UrgentReports.status eq UrgentReportStatus.PENDING.name)
            }) {
                it[UrgentReports.status] = UrgentReportStatus.ACCEPTED.name
                it[UrgentReports.acceptedByUserId] = rescuerId
                it[UrgentReports.acceptedAt] = now
            }
            updatedRows > 0
        }
    }

    override suspend fun createReportPage(reportId: Int, rescuerId: Int): UrgentReportPageDto = withContext(dbDispatcher) {
        val createdAt = clock.now().toEpochMilliseconds()
        val token = generateToken()
        transaction {
            val id = UrgentReportPages.insert {
                it[UrgentReportPages.reportId] = reportId
                it[UrgentReportPages.rescuerId] = rescuerId
                it[UrgentReportPages.token] = token
                it[UrgentReportPages.status] = UrgentReportPageStatus.PAGED.name
                it[UrgentReportPages.createdAt] = createdAt
            } get UrgentReportPages.id

            UrgentReportPageDto(
                id = id,
                reportId = reportId,
                rescuerId = rescuerId,
                token = token,
                status = UrgentReportPageStatus.PAGED,
                createdAt = createdAt
            )
        }
    }

    override suspend fun resolvePages(reportId: Int, winningRescuerId: Int): Unit = withContext(dbDispatcher) {
        transaction {
            UrgentReportPages.update({
                (UrgentReportPages.reportId eq reportId) and (UrgentReportPages.rescuerId eq winningRescuerId)
            }) {
                it[UrgentReportPages.status] = UrgentReportPageStatus.ACCEPTED.name
            }
            UrgentReportPages.update({
                (UrgentReportPages.reportId eq reportId) and
                    (UrgentReportPages.rescuerId neq winningRescuerId) and
                    (UrgentReportPages.status eq UrgentReportPageStatus.PAGED.name)
            }) {
                it[UrgentReportPages.status] = UrgentReportPageStatus.MISSED.name
            }
        }
    }

    override suspend fun getPendingPagesForRescuer(rescuerId: Int): List<UrgentReportPageDto> = withContext(dbDispatcher) {
        transaction {
            UrgentReportPages.selectAll()
                .where { (UrgentReportPages.rescuerId eq rescuerId) and (UrgentReportPages.status eq UrgentReportPageStatus.PAGED.name) }
                .map(::rowToPage)
        }
    }

    override suspend fun resolveAcceptToken(token: String): Pair<Int, Int>? = withContext(dbDispatcher) {
        transaction {
            val row = UrgentReportPages.selectAll()
                .where { (UrgentReportPages.token eq token) and (UrgentReportPages.status eq UrgentReportPageStatus.PAGED.name) }
                .firstOrNull() ?: return@transaction null

            row[UrgentReportPages.reportId] to row[UrgentReportPages.rescuerId]
        }
    }

    override suspend fun getLeaderboard(sinceEpochMs: Long): List<UrgentRescuerLeaderboardEntryDto> = withContext(dbDispatcher) {
        transaction {
            UrgentReports.selectAll()
                .where {
                    (UrgentReports.status eq UrgentReportStatus.ACCEPTED.name) and
                        (UrgentReports.acceptedAt.isNotNull()) and
                        (UrgentReports.acceptedAt greaterEq sinceEpochMs)
                }
                .mapNotNull { it[UrgentReports.acceptedByUserId] }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .mapNotNull { (userId, count) ->
                    val displayName = Users.selectAll()
                        .where { Users.id eq userId }
                        .firstOrNull()
                        ?.get(Users.displayName)
                        ?: return@mapNotNull null
                    UrgentRescuerLeaderboardEntryDto(userId = userId, displayName = displayName, acceptedCount = count)
                }
        }
    }
}

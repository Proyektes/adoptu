package com.adoptu.adapters.db.repositories

import com.adoptu.adapters.db.Photographers
import com.adoptu.adapters.db.PhotographyRequests
import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.common.Country
import com.adoptu.dto.input.PhotographerDto
import com.adoptu.dto.input.PhotographerSettingsRequest
import com.adoptu.dto.input.PhotographyRequestDto
import com.adoptu.dto.input.UserDto
import com.adoptu.dto.input.UserRole
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.ports.PhotographerRepositoryPort
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.adapters.db.dbDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PhotographerRepositoryImpl(
    private val petRepository: PetRepositoryPort,
    private val userRepository: UserRepositoryPort,
    private val clock: Clock
) : PhotographerRepositoryPort {

    companion object {
        const val ONE_WEEK = 7 * 24 * 60 * 60 * 1000L
    }

    private data class RawPhotographyRequest(
        val id: Int,
        val photographerId: Int,
        val requesterId: Int,
        val petId: Int?,
        val message: String?,
        val status: String,
        val scheduledDate: Long?,
        val createdAt: Long
    )

    private fun rowToRaw(row: org.jetbrains.exposed.v1.core.ResultRow): RawPhotographyRequest = RawPhotographyRequest(
        id = row[PhotographyRequests.id],
        photographerId = row[PhotographyRequests.photographerId],
        requesterId = row[PhotographyRequests.requesterId],
        petId = row[PhotographyRequests.petId],
        message = row[PhotographyRequests.message],
        status = row[PhotographyRequests.status],
        scheduledDate = row[PhotographyRequests.scheduledDate],
        createdAt = row[PhotographyRequests.createdAt]
    )

    override suspend fun canSendMessage(userId: Int): Boolean {
        val oneWeekAgo = clock.now().toEpochMilliseconds() - ONE_WEEK
        val requests = withContext(dbDispatcher) {
            transaction {
                PhotographyRequests.selectAll()
                    .where { PhotographyRequests.requesterId.eq(userId).and(PhotographyRequests.createdAt.greaterEq(oneWeekAgo)) }
                    .count()
            }
        }
        return requests == 0L
    }

    override suspend fun createPhotographyRequest(
        requesterId: Int,
        photographerId: Int,
        petId: Int?,
        message: String
    ): Int = withContext(dbDispatcher) {
        transaction {
            val createdAt = clock.now().toEpochMilliseconds()
            val requestId = PhotographyRequests.insert {
                it[PhotographyRequests.photographerId] = photographerId
                it[PhotographyRequests.requesterId] = requesterId
                it[PhotographyRequests.petId] = petId
                it[PhotographyRequests.message] = message
                it[PhotographyRequests.status] = "PENDING"
                it[PhotographyRequests.createdAt] = createdAt
            } get PhotographyRequests.id

            requestId
        }
    }

    override suspend fun getMyRequests(userId: Int): List<PhotographyRequestDto> {
        // Raw rows are fetched inside the transaction; the cross-repository lookup
        // (userRepository.getPhotographers, now suspend) happens afterward since it
        // cannot be called from within the transaction {} lambda.
        val rawRequests = withContext(dbDispatcher) {
            transaction {
                PhotographyRequests.selectAll()
                    .where { PhotographyRequests.requesterId eq userId }
                    .map { row -> rowToRaw(row) }
            }
        }

        val photographers = userRepository.getPhotographers()
        return rawRequests.map { raw ->
            val photographer = photographers.find { it.userId == raw.photographerId }
            PhotographyRequestDto(
                id = raw.id,
                photographerId = raw.photographerId,
                photographerName = photographer?.displayName,
                requesterId = raw.requesterId,
                requesterName = null,
                petId = raw.petId,
                petName = null,
                message = raw.message,
                status = raw.status,
                scheduledDate = raw.scheduledDate,
                createdAt = raw.createdAt
            )
        }
    }

    override suspend fun getRequestsForPhotographer(photographerId: Int): List<PhotographyRequestDto> {
        val rawRequests = withContext(dbDispatcher) {
            transaction {
                PhotographyRequests.selectAll()
                    .where { PhotographyRequests.photographerId eq photographerId }
                    .map { row -> rowToRaw(row) }
            }
        }

        return rawRequests.map { raw ->
            val requester = userRepository.getById(raw.requesterId)
            val pet = raw.petId?.let { petRepository.getById(it) }
            PhotographyRequestDto(
                id = raw.id,
                photographerId = raw.photographerId,
                photographerName = null,
                requesterId = raw.requesterId,
                requesterName = requester?.displayName,
                petId = raw.petId,
                petName = pet?.name,
                message = raw.message,
                status = raw.status,
                scheduledDate = raw.scheduledDate,
                createdAt = raw.createdAt
            )
        }
    }

    override suspend fun getRequestById(requestId: Int): PhotographyRequestDto? {
        val raw = withContext(dbDispatcher) {
            transaction {
                PhotographyRequests.selectAll()
                    .where { PhotographyRequests.id eq requestId }
                    .firstOrNull()
                    ?.let { rowToRaw(it) }
            }
        } ?: return null

        val photographer = userRepository.getPhotographers().find { it.userId == raw.photographerId }
        val requester = userRepository.getById(raw.requesterId)
        val pet = raw.petId?.let { petRepository.getById(it) }

        return PhotographyRequestDto(
            id = raw.id,
            photographerId = raw.photographerId,
            photographerName = photographer?.displayName,
            requesterId = raw.requesterId,
            requesterName = requester?.displayName,
            petId = raw.petId,
            petName = pet?.name,
            message = raw.message,
            status = raw.status,
            scheduledDate = raw.scheduledDate,
            createdAt = raw.createdAt
        )
    }

    override suspend fun updatePhotographyRequest(requestId: Int, status: String?, scheduledDate: Long?) {
        withContext(dbDispatcher) {
            transaction {
                PhotographyRequests.update({ PhotographyRequests.id eq requestId }) {
                    if (status != null) it[PhotographyRequests.status] = status
                    if (scheduledDate != null) it[PhotographyRequests.scheduledDate] = scheduledDate
                }
            }
        }
    }

    override suspend fun getPhotographerById(userId: Int): PhotographerDto? {
        val hasPhotographerRole = withContext(dbDispatcher) {
            transaction {
                UserActiveRoles.selectAll()
                    .where { (UserActiveRoles.userId eq userId) and (UserActiveRoles.role eq UserRole.PHOTOGRAPHER.name) }
                    .count() > 0
            }
        }
        if (!hasPhotographerRole) return null

        return withContext(dbDispatcher) {
            transaction {
                val user = Users.selectAll().where { Users.id eq userId }.firstOrNull()
                val photographer = Photographers.selectAll().where { Photographers.userId eq userId }.firstOrNull()
                if (user != null) {
                    PhotographerDto(
                        userId = userId,
                        displayName = user[Users.displayName],
                        username = user[Users.username],
                        photographerFee = photographer?.get(Photographers.photographerFee)?.toDouble(),
                        photographerCurrency = photographer?.get(Photographers.photographerCurrency),
                        country = photographer?.get(Photographers.country)?.displayName,
                        state = photographer?.get(Photographers.state)
                    )
                } else null
            }
        }
    }

    override suspend fun getOwnPhotographerSettings(userId: Int): PhotographerDto? = withContext(dbDispatcher) {
        transaction {
            val user = Users.selectAll().where { Users.id eq userId }.firstOrNull() ?: return@transaction null
            val photographer = Photographers.selectAll().where { Photographers.userId eq userId }.firstOrNull()
            PhotographerDto(
                userId = userId,
                displayName = user[Users.displayName],
                username = user[Users.username],
                photographerFee = photographer?.get(Photographers.photographerFee)?.toDouble(),
                photographerCurrency = photographer?.get(Photographers.photographerCurrency),
                country = photographer?.get(Photographers.country)?.displayName,
                state = photographer?.get(Photographers.state)
            )
        }
    }

    override suspend fun getPhotographers(country: String?, state: String?): List<PhotographerDto> = withContext(dbDispatcher) {
        transaction {
            val parsedFilterCountry: Country? = if (!country.isNullOrBlank()) {
                Country.fromDisplayName(country) ?: return@transaction emptyList()
            } else null

            val photographerUserIds = UserActiveRoles.selectAll()
                .where { UserActiveRoles.role eq UserRole.PHOTOGRAPHER.name }
                .map { row -> row[UserActiveRoles.userId] }
                .distinct()

            photographerUserIds.mapNotNull { userId ->
                val user = Users.selectAll().where { Users.id eq userId }.firstOrNull()
                val photographer = Photographers.selectAll().where { Photographers.userId eq userId }.firstOrNull()

                if (user != null) {
                    val photographerCountry = photographer?.get(Photographers.country)
                    val photographerState = photographer?.get(Photographers.state)

                    val matchesCountry = parsedFilterCountry == null || photographerCountry == parsedFilterCountry
                    val matchesState = state.isNullOrBlank() || photographerState == state

                    if (matchesCountry && matchesState) {
                        PhotographerDto(
                            userId = userId,
                            displayName = user[Users.displayName],
                            username = user[Users.username],
                            photographerFee = photographer?.get(Photographers.photographerFee)?.toDouble(),
                            photographerCurrency = photographer?.get(Photographers.photographerCurrency),
                            country = photographerCountry?.displayName,
                            state = photographerState
                        )
                    } else null
                } else null
            }
        }
    }

    override suspend fun updatePhotographerSettings(userId: Int, request: PhotographerSettingsRequest): PhotographerDto? {
        if (request.photographerFee < 0) {
            throw IllegalArgumentException("Photographer fee must be zero or positive")
        }

        val userExists = withContext(dbDispatcher) {
            transaction {
                Users.selectAll().where { Users.id eq userId }.firstOrNull() != null
            }
        }
        if (!userExists) return null

        val parsedCountry = request.country?.let {
            Country.fromDisplayName(it) ?: throw IllegalArgumentException("Invalid country: $it")
        }

        withContext(dbDispatcher) {
            transaction {
                val existing = Photographers.selectAll().where { Photographers.userId eq userId }.firstOrNull()
                if (existing != null) {
                    Photographers.update({ Photographers.userId eq userId }) {
                        it[photographerFee] = BigDecimal.valueOf(request.photographerFee)
                        it[photographerCurrency] = request.photographerCurrency
                        it[country] = parsedCountry
                        it[state] = request.state
                    }
                } else {
                    Photographers.insert {
                        it[Photographers.userId] = userId
                        it[photographerFee] = BigDecimal.valueOf(request.photographerFee)
                        it[photographerCurrency] = request.photographerCurrency
                        it[country] = parsedCountry
                        it[state] = request.state
                    }
                }
            }
        }

        return getPhotographerById(userId)
    }

    override suspend fun activatePhotographerProfile(userId: Int): UserDto? {
        val user = userRepository.getById(userId) ?: return null

        withContext(dbDispatcher) {
            transaction {
                val existingRole = UserActiveRoles.selectAll()
                    .where { (UserActiveRoles.userId eq userId) and (UserActiveRoles.role eq UserRole.PHOTOGRAPHER.name) }
                    .firstOrNull()
                if (existingRole == null) {
                    UserActiveRoles.insert {
                        it[UserActiveRoles.userId] = userId
                        it[UserActiveRoles.role] = UserRole.PHOTOGRAPHER.name
                    }
                }
                val existingPhotographer = Photographers.selectAll()
                    .where { Photographers.userId eq userId }
                    .firstOrNull()
                if (existingPhotographer == null) {
                    Photographers.insert {
                        it[Photographers.userId] = userId
                        it[country] = null
                        it[state] = null
                    }
                }
            }
        }
        return userRepository.getById(userId)
    }

    override suspend fun deactivatePhotographerProfile(userId: Int): UserDto? {
        withContext(dbDispatcher) {
            transaction {
                UserActiveRoles.deleteWhere {
                    (UserActiveRoles.userId eq userId) and (UserActiveRoles.role eq UserRole.PHOTOGRAPHER.name)
                }
            }
        }
        return userRepository.getById(userId)
    }
}

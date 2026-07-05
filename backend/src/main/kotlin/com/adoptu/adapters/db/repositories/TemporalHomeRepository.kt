package com.adoptu.adapters.db.repositories

import com.adoptu.adapters.db.BlockedRescuers
import com.adoptu.adapters.db.SpamReportTokens
import com.adoptu.adapters.db.TemporalHomeRequests
import com.adoptu.adapters.db.TemporalHomes
import com.adoptu.common.Country
import com.adoptu.dto.input.CreateTemporalHomeRequest
import com.adoptu.dto.input.TemporalHomeDto
import com.adoptu.dto.input.TemporalHomeRequestDto
import com.adoptu.dto.input.TemporalHomeSearchParams
import com.adoptu.dto.input.UpdateTemporalHomeRequest
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.ports.TemporalHomeRepositoryPort
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.adapters.db.dbDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class TemporalHomeRepositoryImpl(
    private val petRepository: PetRepositoryPort,
    private val userRepository: UserRepositoryPort,
    private val clock: Clock
) : TemporalHomeRepositoryPort {

    private val secureRandom = SecureRandom()
    private val spamReportTokenExpirationMs = 30 * 24 * 60 * 60 * 1000L

    private data class RawTemporalHomeRequest(
        val id: Int,
        val temporalHomeId: Int,
        val rescuerId: Int,
        val petId: Int?,
        val message: String,
        val status: String,
        val createdAt: Long
    )

    override suspend fun getTemporalHome(userId: Int): TemporalHomeDto? = withContext(dbDispatcher) {
        transaction {
            val result = TemporalHomes.selectAll()
                .where { TemporalHomes.userId eq userId }
                .firstOrNull()
            if (result != null) {
                TemporalHomeDto(
                    userId = result[TemporalHomes.userId],
                    alias = result[TemporalHomes.alias],
                    country = result[TemporalHomes.country].displayName,
                    state = result[TemporalHomes.state],
                    city = result[TemporalHomes.city],
                    zip = result[TemporalHomes.zip],
                    neighborhood = result[TemporalHomes.neighborhood],
                    createdAt = result[TemporalHomes.createdAt]
                )
            } else null
        }
    }

    override suspend fun createTemporalHome(userId: Int, request: CreateTemporalHomeRequest): TemporalHomeDto {
        val createdAt = clock.now().toEpochMilliseconds()
        val parsedCountry = Country.fromDisplayName(request.country)
            ?: throw IllegalArgumentException("Invalid country: ${request.country}")
        return withContext(dbDispatcher) {
            transaction {
                TemporalHomes.insert {
                    it[TemporalHomes.userId] = userId
                    it[TemporalHomes.alias] = request.alias
                    it[TemporalHomes.country] = parsedCountry
                    it[TemporalHomes.state] = request.state
                    it[TemporalHomes.city] = request.city
                    it[TemporalHomes.zip] = request.zip
                    it[TemporalHomes.neighborhood] = request.neighborhood
                    it[TemporalHomes.createdAt] = createdAt
                }

                TemporalHomeDto(
                    userId = userId,
                    alias = request.alias,
                    country = parsedCountry.displayName,
                    state = request.state,
                    city = request.city,
                    zip = request.zip,
                    neighborhood = request.neighborhood,
                    createdAt = createdAt
                )
            }
        }
    }

    override suspend fun updateTemporalHome(userId: Int, request: UpdateTemporalHomeRequest): TemporalHomeDto? = withContext(dbDispatcher) {
        transaction {
            val existing = TemporalHomes.selectAll()
                .where { TemporalHomes.userId eq userId }
                .firstOrNull()
                ?: return@transaction null

            val updatedAlias = request.alias ?: existing[TemporalHomes.alias]
            val updatedCountry = request.country?.let {
                Country.fromDisplayName(it) ?: throw IllegalArgumentException("Invalid country: $it")
            } ?: existing[TemporalHomes.country]
            val updatedState = request.state ?: existing[TemporalHomes.state]
            val updatedCity = request.city ?: existing[TemporalHomes.city]
            val updatedZip = request.zip ?: existing[TemporalHomes.zip]
            val updatedNeighborhood = request.neighborhood ?: existing[TemporalHomes.neighborhood]

            TemporalHomes.update({ TemporalHomes.userId eq userId }) {
                it[TemporalHomes.alias] = updatedAlias
                it[TemporalHomes.country] = updatedCountry
                it[TemporalHomes.state] = updatedState
                it[TemporalHomes.city] = updatedCity
                it[TemporalHomes.zip] = updatedZip
                it[TemporalHomes.neighborhood] = updatedNeighborhood
            }

            TemporalHomeDto(
                userId = userId,
                alias = updatedAlias,
                country = updatedCountry.displayName,
                state = updatedState,
                city = updatedCity,
                zip = updatedZip,
                neighborhood = updatedNeighborhood,
                createdAt = existing[TemporalHomes.createdAt]
            )
        }
    }

    override suspend fun searchTemporalHomes(params: TemporalHomeSearchParams): List<TemporalHomeDto> = withContext(dbDispatcher) {
        transaction {
            var conditions: Op<Boolean>? = null

            params.country?.let { country ->
                val parsedCountry = Country.fromDisplayName(country) ?: return@transaction emptyList()
                conditions = conditions?.and(TemporalHomes.country eq parsedCountry) ?: (TemporalHomes.country eq parsedCountry)
            }
            params.state?.let { state ->
                conditions = conditions?.and(TemporalHomes.state eq state) ?: (TemporalHomes.state eq state)
            }
            params.city?.let { city ->
                conditions = conditions?.and(TemporalHomes.city eq city) ?: (TemporalHomes.city eq city)
            }
            params.zip?.let { zip ->
                conditions = conditions?.and(TemporalHomes.zip eq zip) ?: (TemporalHomes.zip eq zip)
            }
            params.neighborhood?.let { neighborhood ->
                conditions = conditions?.and(TemporalHomes.neighborhood eq neighborhood) ?: (TemporalHomes.neighborhood eq neighborhood)
            }

            val query = if (conditions != null) {
                TemporalHomes.selectAll().where { conditions }
            } else {
                TemporalHomes.selectAll()
            }

            query.map { row ->
                TemporalHomeDto(
                    userId = row[TemporalHomes.userId],
                    alias = row[TemporalHomes.alias],
                    country = row[TemporalHomes.country].displayName,
                    state = row[TemporalHomes.state],
                    city = row[TemporalHomes.city],
                    zip = row[TemporalHomes.zip],
                    neighborhood = row[TemporalHomes.neighborhood],
                    createdAt = row[TemporalHomes.createdAt]
                )
            }
        }
    }

    override suspend fun createTemporalHomeRequest(
        temporalHomeId: Int,
        rescuerId: Int,
        petId: Int?,
        message: String
    ): Int = withContext(dbDispatcher) {
        transaction {
            val createdAt = clock.now().toEpochMilliseconds()
            val requestId = TemporalHomeRequests.insert {
                it[TemporalHomeRequests.temporalHomeId] = temporalHomeId
                it[TemporalHomeRequests.rescuerId] = rescuerId
                it[TemporalHomeRequests.petId] = petId
                it[TemporalHomeRequests.message] = message
                it[TemporalHomeRequests.status] = "SENT"
                it[TemporalHomeRequests.createdAt] = createdAt
            } get TemporalHomeRequests.id

            requestId
        }
    }

    override suspend fun isBlocked(temporalHomeId: Int, rescuerId: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            val blocked = BlockedRescuers.selectAll()
                .where { (BlockedRescuers.temporalHomeId eq temporalHomeId).and(BlockedRescuers.rescuerId eq rescuerId) }
                .firstOrNull()
            blocked != null
        }
    }

    override suspend fun blockRescuer(temporalHomeId: Int, rescuerId: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            val alreadyBlocked = BlockedRescuers.selectAll()
                .where { (BlockedRescuers.temporalHomeId eq temporalHomeId).and(BlockedRescuers.rescuerId eq rescuerId) }
                .firstOrNull()

            if (alreadyBlocked != null) {
                return@transaction false
            }

            val createdAt = clock.now().toEpochMilliseconds()
            BlockedRescuers.insert {
                it[BlockedRescuers.temporalHomeId] = temporalHomeId
                it[BlockedRescuers.rescuerId] = rescuerId
                it[BlockedRescuers.createdAt] = createdAt
            }
            true
        }
    }

    override suspend fun getMyRequests(userId: Int): List<TemporalHomeRequestDto> {
        // Raw rows are fetched inside the transaction; the cross-repository lookups
        // (userRepository / petRepository, now suspend) happen afterward since they
        // cannot be called from within the transaction {} lambda.
        val rawRequests = withContext(dbDispatcher) {
            transaction {
                TemporalHomeRequests.selectAll()
                    .where { TemporalHomeRequests.temporalHomeId eq userId }
                    .map { row ->
                        RawTemporalHomeRequest(
                            id = row[TemporalHomeRequests.id],
                            temporalHomeId = row[TemporalHomeRequests.temporalHomeId],
                            rescuerId = row[TemporalHomeRequests.rescuerId],
                            petId = row[TemporalHomeRequests.petId],
                            message = row[TemporalHomeRequests.message],
                            status = row[TemporalHomeRequests.status],
                            createdAt = row[TemporalHomeRequests.createdAt]
                        )
                    }
            }
        }

        return rawRequests.map { raw ->
            val rescuer = userRepository.getById(raw.rescuerId)
            val pet = raw.petId?.let { petRepository.getById(it) }

            TemporalHomeRequestDto(
                id = raw.id,
                temporalHomeId = raw.temporalHomeId,
                rescuerId = raw.rescuerId,
                rescuerName = rescuer?.displayName,
                petId = raw.petId,
                petName = pet?.name,
                message = raw.message,
                status = raw.status,
                createdAt = raw.createdAt
            )
        }
    }

    override suspend fun createSpamReportToken(temporalHomeId: Int, rescuerId: Int): String = withContext(dbDispatcher) {
        val token = generateToken()
        val now = clock.now().toEpochMilliseconds()
        transaction {
            SpamReportTokens.insert {
                it[SpamReportTokens.temporalHomeId] = temporalHomeId
                it[SpamReportTokens.rescuerId] = rescuerId
                it[SpamReportTokens.token] = token
                it[SpamReportTokens.expiresAt] = now + spamReportTokenExpirationMs
                it[SpamReportTokens.createdAt] = now
            }
        }
        token
    }

    override suspend fun consumeSpamReportToken(token: String): Pair<Int, Int>? = withContext(dbDispatcher) {
        val now = clock.now().toEpochMilliseconds()
        transaction {
            val row = SpamReportTokens.selectAll()
                .where { (SpamReportTokens.token eq token) and (SpamReportTokens.usedAt.isNull()) }
                .firstOrNull() ?: return@transaction null

            if (row[SpamReportTokens.expiresAt] <= now) {
                return@transaction null
            }

            SpamReportTokens.update({ SpamReportTokens.id eq row[SpamReportTokens.id] }) {
                it[SpamReportTokens.usedAt] = now
            }

            row[SpamReportTokens.temporalHomeId] to row[SpamReportTokens.rescuerId]
        }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

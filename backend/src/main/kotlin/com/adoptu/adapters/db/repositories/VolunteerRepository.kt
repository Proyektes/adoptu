package com.adoptu.adapters.db.repositories

import com.adoptu.adapters.db.Volunteers
import com.adoptu.adapters.db.dbDispatcher
import com.adoptu.dto.input.VolunteerDto
import com.adoptu.dto.input.VolunteerStatus
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.ports.VolunteerRepositoryPort
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// Raw row + a denormalize() pass afterward - same shape as PetFosterPlacementRepositoryImpl,
// since resolving rescuer/volunteer display names is a suspend call that can't run inside the
// transaction {} lambda.
private data class RawVolunteer(
    val id: Int,
    val rescuerId: Int,
    val volunteerId: Int,
    val status: VolunteerStatus,
    val createdAt: Long
)

@OptIn(ExperimentalTime::class)
class VolunteerRepositoryImpl(
    private val userRepository: UserRepositoryPort,
    private val clock: Clock
) : VolunteerRepositoryPort {

    private fun rowToRaw(row: ResultRow) = RawVolunteer(
        id = row[Volunteers.id],
        rescuerId = row[Volunteers.rescuerId],
        volunteerId = row[Volunteers.volunteerId],
        status = VolunteerStatus.valueOf(row[Volunteers.status]),
        createdAt = row[Volunteers.createdAt]
    )

    private suspend fun denormalize(raw: RawVolunteer): VolunteerDto {
        val rescuer = userRepository.getById(raw.rescuerId)
        val volunteer = userRepository.getById(raw.volunteerId)
        return VolunteerDto(
            id = raw.id,
            rescuerId = raw.rescuerId,
            rescuerName = rescuer?.displayName,
            volunteerId = raw.volunteerId,
            volunteerName = volunteer?.displayName,
            status = raw.status,
            createdAt = raw.createdAt
        )
    }

    override suspend fun create(rescuerId: Int, volunteerId: Int): VolunteerDto {
        val now = clock.now().toEpochMilliseconds()
        val raw = withContext(dbDispatcher) {
            transaction {
                val id = Volunteers.insert {
                    it[Volunteers.rescuerId] = rescuerId
                    it[Volunteers.volunteerId] = volunteerId
                    it[Volunteers.status] = VolunteerStatus.PENDING.name
                    it[Volunteers.createdAt] = now
                } get Volunteers.id
                RawVolunteer(id, rescuerId, volunteerId, VolunteerStatus.PENDING, now)
            }
        }
        return denormalize(raw)
    }

    override suspend fun getById(id: Int): VolunteerDto? {
        val raw = withContext(dbDispatcher) {
            transaction {
                Volunteers.selectAll()
                    .where { Volunteers.id eq id }
                    .firstOrNull()
                    ?.let(::rowToRaw)
            }
        }
        return raw?.let { denormalize(it) }
    }

    override suspend fun getLatestForPair(rescuerId: Int, volunteerId: Int): VolunteerDto? {
        val raw = withContext(dbDispatcher) {
            transaction {
                Volunteers.selectAll()
                    .where { (Volunteers.rescuerId eq rescuerId) and (Volunteers.volunteerId eq volunteerId) }
                    .orderBy(Volunteers.createdAt, SortOrder.DESC)
                    .firstOrNull()
                    ?.let(::rowToRaw)
            }
        }
        return raw?.let { denormalize(it) }
    }

    override suspend fun getForVolunteer(volunteerId: Int): List<VolunteerDto> {
        val raws = withContext(dbDispatcher) {
            transaction {
                Volunteers.selectAll()
                    .where { Volunteers.volunteerId eq volunteerId }
                    .orderBy(Volunteers.createdAt, SortOrder.DESC)
                    .map(::rowToRaw)
            }
        }
        return raws.map { denormalize(it) }
    }

    override suspend fun getForRescuer(rescuerId: Int): List<VolunteerDto> {
        val raws = withContext(dbDispatcher) {
            transaction {
                Volunteers.selectAll()
                    .where { Volunteers.rescuerId eq rescuerId }
                    .orderBy(Volunteers.createdAt, SortOrder.DESC)
                    .map(::rowToRaw)
            }
        }
        return raws.map { denormalize(it) }
    }

    override suspend fun isActiveVolunteerFor(rescuerId: Int, volunteerId: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            Volunteers.selectAll()
                .where {
                    (Volunteers.rescuerId eq rescuerId) and
                        (Volunteers.volunteerId eq volunteerId) and
                        (Volunteers.status eq VolunteerStatus.ACTIVE.name)
                }
                .count() > 0
        }
    }

    override suspend fun updateStatus(id: Int, status: VolunteerStatus): Boolean = withContext(dbDispatcher) {
        transaction {
            Volunteers.update({ Volunteers.id eq id }) {
                it[Volunteers.status] = status.name
            } > 0
        }
    }
}

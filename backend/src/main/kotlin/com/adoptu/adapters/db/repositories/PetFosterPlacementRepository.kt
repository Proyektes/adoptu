package com.adoptu.adapters.db.repositories

import com.adoptu.adapters.db.PetFosterPlacements
import com.adoptu.adapters.db.dbDispatcher
import com.adoptu.dto.input.PetFosterPlacementDto
import com.adoptu.ports.PetFosterPlacementRepositoryPort
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.ports.TemporalHomeRepositoryPort
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// Raw row + a denormalize() pass afterward - same shape as TemporalHomeRepositoryImpl.getMyRequests,
// since the cross-repository lookups (pet name, temporal home alias) are suspend calls that can't
// run inside the transaction {} lambda.
private data class RawPlacement(
    val id: Int,
    val petId: Int,
    val temporalHomeId: Int,
    val startDate: Long,
    val endDate: Long?,
    val notes: String?,
    val createdAt: Long
)

@OptIn(ExperimentalTime::class)
class PetFosterPlacementRepositoryImpl(
    private val petRepository: PetRepositoryPort,
    private val temporalHomeRepository: TemporalHomeRepositoryPort,
    private val clock: Clock
) : PetFosterPlacementRepositoryPort {

    private fun rowToRaw(row: ResultRow) = RawPlacement(
        id = row[PetFosterPlacements.id],
        petId = row[PetFosterPlacements.petId],
        temporalHomeId = row[PetFosterPlacements.temporalHomeId],
        startDate = row[PetFosterPlacements.startDate],
        endDate = row[PetFosterPlacements.endDate],
        notes = row[PetFosterPlacements.notes],
        createdAt = row[PetFosterPlacements.createdAt]
    )

    private suspend fun denormalize(raw: RawPlacement): PetFosterPlacementDto {
        val pet = petRepository.getById(raw.petId)
        val temporalHome = temporalHomeRepository.getTemporalHome(raw.temporalHomeId)
        return PetFosterPlacementDto(
            id = raw.id,
            petId = raw.petId,
            petName = pet?.name,
            temporalHomeId = raw.temporalHomeId,
            temporalHomeAlias = temporalHome?.alias,
            startDate = raw.startDate,
            endDate = raw.endDate,
            notes = raw.notes,
            createdAt = raw.createdAt
        )
    }

    override suspend fun create(petId: Int, temporalHomeId: Int, notes: String?): PetFosterPlacementDto {
        val now = clock.now().toEpochMilliseconds()
        val raw = withContext(dbDispatcher) {
            transaction {
                val id = PetFosterPlacements.insert {
                    it[PetFosterPlacements.petId] = petId
                    it[PetFosterPlacements.temporalHomeId] = temporalHomeId
                    it[PetFosterPlacements.startDate] = now
                    it[PetFosterPlacements.notes] = notes
                    it[PetFosterPlacements.createdAt] = now
                } get PetFosterPlacements.id
                RawPlacement(id, petId, temporalHomeId, now, null, notes, now)
            }
        }
        return denormalize(raw)
    }

    override suspend fun getActiveForPet(petId: Int): PetFosterPlacementDto? {
        val raw = withContext(dbDispatcher) {
            transaction {
                PetFosterPlacements.selectAll()
                    .where { (PetFosterPlacements.petId eq petId) and PetFosterPlacements.endDate.isNull() }
                    .firstOrNull()
                    ?.let(::rowToRaw)
            }
        }
        return raw?.let { denormalize(it) }
    }

    override suspend fun getHistoryForPet(petId: Int): List<PetFosterPlacementDto> {
        val raws = withContext(dbDispatcher) {
            transaction {
                PetFosterPlacements.selectAll()
                    .where { PetFosterPlacements.petId eq petId }
                    .orderBy(PetFosterPlacements.startDate, SortOrder.DESC)
                    .map(::rowToRaw)
            }
        }
        return raws.map { denormalize(it) }
    }

    override suspend fun getActiveForTemporalHome(temporalHomeId: Int): List<PetFosterPlacementDto> {
        val raws = withContext(dbDispatcher) {
            transaction {
                PetFosterPlacements.selectAll()
                    .where { (PetFosterPlacements.temporalHomeId eq temporalHomeId) and PetFosterPlacements.endDate.isNull() }
                    .map(::rowToRaw)
            }
        }
        return raws.map { denormalize(it) }
    }

    override suspend fun countActiveForTemporalHome(temporalHomeId: Int): Int = withContext(dbDispatcher) {
        transaction {
            PetFosterPlacements.selectAll()
                .where { (PetFosterPlacements.temporalHomeId eq temporalHomeId) and PetFosterPlacements.endDate.isNull() }
                .count()
                .toInt()
        }
    }

    override suspend fun getById(id: Int): PetFosterPlacementDto? {
        val raw = withContext(dbDispatcher) {
            transaction {
                PetFosterPlacements.selectAll()
                    .where { PetFosterPlacements.id eq id }
                    .firstOrNull()
                    ?.let(::rowToRaw)
            }
        }
        return raw?.let { denormalize(it) }
    }

    override suspend fun endPlacement(id: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            PetFosterPlacements.update({ PetFosterPlacements.id eq id }) {
                it[PetFosterPlacements.endDate] = clock.now().toEpochMilliseconds()
            } > 0
        }
    }
}

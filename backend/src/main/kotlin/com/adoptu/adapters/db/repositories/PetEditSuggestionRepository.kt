package com.adoptu.adapters.db.repositories

import com.adoptu.adapters.db.PetEditSuggestions
import com.adoptu.adapters.db.dbDispatcher
import com.adoptu.dto.input.CreatePetEditSuggestionRequest
import com.adoptu.dto.input.PetEditSuggestionDto
import com.adoptu.dto.input.PetEditSuggestionStatus
import com.adoptu.ports.PetEditSuggestionRepositoryPort
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.ports.UserRepositoryPort
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// Raw row + a denormalize() pass afterward - same shape as PetFosterPlacementRepositoryImpl,
// since resolving the pet name/volunteer display name is a suspend call that can't run inside the
// transaction {} lambda.
private data class RawSuggestion(
    val id: Int,
    val petId: Int,
    val volunteerId: Int,
    val description: String?,
    val temperament: String?,
    val energyLevel: String?,
    val specialNeeds: String?,
    val vaccinations: String?,
    val status: PetEditSuggestionStatus,
    val createdAt: Long,
    val reviewedAt: Long?
)

@OptIn(ExperimentalTime::class)
class PetEditSuggestionRepositoryImpl(
    private val petRepository: PetRepositoryPort,
    private val userRepository: UserRepositoryPort,
    private val clock: Clock
) : PetEditSuggestionRepositoryPort {

    private fun rowToRaw(row: ResultRow) = RawSuggestion(
        id = row[PetEditSuggestions.id],
        petId = row[PetEditSuggestions.petId],
        volunteerId = row[PetEditSuggestions.volunteerId],
        description = row[PetEditSuggestions.description],
        temperament = row[PetEditSuggestions.temperament],
        energyLevel = row[PetEditSuggestions.energyLevel],
        specialNeeds = row[PetEditSuggestions.specialNeeds],
        vaccinations = row[PetEditSuggestions.vaccinations],
        status = PetEditSuggestionStatus.valueOf(row[PetEditSuggestions.status]),
        createdAt = row[PetEditSuggestions.createdAt],
        reviewedAt = row[PetEditSuggestions.reviewedAt]
    )

    private suspend fun denormalize(raw: RawSuggestion): PetEditSuggestionDto {
        val pet = petRepository.getById(raw.petId)
        val volunteer = userRepository.getById(raw.volunteerId)
        return PetEditSuggestionDto(
            id = raw.id,
            petId = raw.petId,
            petName = pet?.name,
            volunteerId = raw.volunteerId,
            volunteerName = volunteer?.displayName,
            description = raw.description,
            temperament = raw.temperament,
            energyLevel = raw.energyLevel,
            specialNeeds = raw.specialNeeds,
            vaccinations = raw.vaccinations,
            status = raw.status,
            createdAt = raw.createdAt,
            reviewedAt = raw.reviewedAt
        )
    }

    override suspend fun create(petId: Int, volunteerId: Int, request: CreatePetEditSuggestionRequest): PetEditSuggestionDto {
        val now = clock.now().toEpochMilliseconds()
        val raw = withContext(dbDispatcher) {
            transaction {
                val id = PetEditSuggestions.insert {
                    it[PetEditSuggestions.petId] = petId
                    it[PetEditSuggestions.volunteerId] = volunteerId
                    it[PetEditSuggestions.description] = request.description
                    it[PetEditSuggestions.temperament] = request.temperament
                    it[PetEditSuggestions.energyLevel] = request.energyLevel
                    it[PetEditSuggestions.specialNeeds] = request.specialNeeds
                    it[PetEditSuggestions.vaccinations] = request.vaccinations
                    it[PetEditSuggestions.status] = PetEditSuggestionStatus.PENDING.name
                    it[PetEditSuggestions.createdAt] = now
                } get PetEditSuggestions.id
                RawSuggestion(
                    id, petId, volunteerId, request.description, request.temperament,
                    request.energyLevel, request.specialNeeds, request.vaccinations,
                    PetEditSuggestionStatus.PENDING, now, null
                )
            }
        }
        return denormalize(raw)
    }

    override suspend fun getById(id: Int): PetEditSuggestionDto? {
        val raw = withContext(dbDispatcher) {
            transaction {
                PetEditSuggestions.selectAll()
                    .where { PetEditSuggestions.id eq id }
                    .firstOrNull()
                    ?.let(::rowToRaw)
            }
        }
        return raw?.let { denormalize(it) }
    }

    override suspend fun getPendingForRescuer(rescuerId: Int): List<PetEditSuggestionDto> {
        val petIds = petRepository.getAllUnfiltered().filter { it.rescuerId == rescuerId }.map { it.id }
        if (petIds.isEmpty()) return emptyList()
        val raws = withContext(dbDispatcher) {
            transaction {
                PetEditSuggestions.selectAll()
                    .where { (PetEditSuggestions.petId inList petIds) and (PetEditSuggestions.status eq PetEditSuggestionStatus.PENDING.name) }
                    .orderBy(PetEditSuggestions.createdAt, SortOrder.DESC)
                    .map(::rowToRaw)
            }
        }
        return raws.map { denormalize(it) }
    }

    override suspend fun getForVolunteer(volunteerId: Int): List<PetEditSuggestionDto> {
        val raws = withContext(dbDispatcher) {
            transaction {
                PetEditSuggestions.selectAll()
                    .where { PetEditSuggestions.volunteerId eq volunteerId }
                    .orderBy(PetEditSuggestions.createdAt, SortOrder.DESC)
                    .map(::rowToRaw)
            }
        }
        return raws.map { denormalize(it) }
    }

    override suspend fun updateStatus(id: Int, status: PetEditSuggestionStatus, reviewedAt: Long): Boolean = withContext(dbDispatcher) {
        transaction {
            PetEditSuggestions.update({ PetEditSuggestions.id eq id }) {
                it[PetEditSuggestions.status] = status.name
                it[PetEditSuggestions.reviewedAt] = reviewedAt
            } > 0
        }
    }
}

package com.adoptu.services

import com.adoptu.dto.input.CreatePetMedicalEventRequest
import com.adoptu.dto.input.MedicalEventUrgency
import com.adoptu.dto.input.PetMedicalEventDto
import com.adoptu.dto.input.RescuerMedicalEventDto
import com.adoptu.ports.PetMedicalEventRepositoryPort
import com.adoptu.ports.PetRepositoryPort
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val DAY_MS = 24 * 60 * 60 * 1000L
private const val DUE_SOON_WINDOW_DAYS = 7

@OptIn(ExperimentalTime::class)
class PetMedicalEventService(
    private val repository: PetMedicalEventRepositoryPort,
    private val petRepository: PetRepositoryPort,
    private val clock: Clock
) {
    suspend fun getForPet(petId: Int): List<PetMedicalEventDto> = repository.getForPet(petId)

    // Every medical event across every pet the rescuer owns, newest first, with urgency computed
    // against the current time - backs the Manage Pets "Medical Events overview" section.
    suspend fun getForRescuer(rescuerId: Int, userId: Int, userRoles: Set<String>): ServiceResult<List<RescuerMedicalEventDto>> {
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        val now = clock.now().toEpochMilliseconds()
        val petNames = mutableMapOf<Int, String>()
        val events = repository.getForRescuer(rescuerId).map { event ->
            val petName = petNames.getOrPut(event.petId) { petRepository.getById(event.petId)?.name ?: "" }
            RescuerMedicalEventDto(
                id = event.id,
                petId = event.petId,
                petName = petName,
                category = event.category,
                name = event.name,
                administeredDate = event.administeredDate,
                nextDueDate = event.nextDueDate,
                urgency = urgencyOf(event.nextDueDate, now)
            )
        }
        return ServiceResult.Success(events.sortedWith(compareBy({ urgencyRank(it.urgency) }, { it.nextDueDate ?: Long.MAX_VALUE })))
    }

    private fun urgencyOf(nextDueDate: Long?, now: Long): MedicalEventUrgency? {
        if (nextDueDate == null) return null
        val daysUntilDue = Math.floorDiv(nextDueDate - now, DAY_MS)
        return when {
            daysUntilDue < 0 -> MedicalEventUrgency.OVERDUE
            daysUntilDue <= DUE_SOON_WINDOW_DAYS -> MedicalEventUrgency.DUE_SOON
            else -> MedicalEventUrgency.SCHEDULED
        }
    }

    private fun urgencyRank(urgency: MedicalEventUrgency?): Int = when (urgency) {
        MedicalEventUrgency.OVERDUE -> 0
        MedicalEventUrgency.DUE_SOON -> 1
        MedicalEventUrgency.SCHEDULED -> 2
        null -> 3
    }

    suspend fun create(petId: Int, userId: Int, userRoles: Set<String>, request: CreatePetMedicalEventRequest): ServiceResult<PetMedicalEventDto> {
        val pet = petRepository.getById(petId) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && pet.rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        if (request.name.isBlank()) {
            return ServiceResult.Error("Name is required")
        }
        return ServiceResult.Success(repository.create(petId, request))
    }

    suspend fun delete(id: Int, userId: Int, userRoles: Set<String>): ServiceResult<Unit> {
        val existing = repository.getById(id) ?: return ServiceResult.NotFound
        val pet = petRepository.getById(existing.petId) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && pet.rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        repository.delete(id)
        return ServiceResult.Success(Unit)
    }
}

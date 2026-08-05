package com.adoptu.adapters.db.repositories

import com.adoptu.adapters.db.PetMedicalEvents
import com.adoptu.adapters.db.dbDispatcher
import com.adoptu.dto.input.CreatePetMedicalEventRequest
import com.adoptu.dto.input.MedicalEventCategory
import com.adoptu.dto.input.PetMedicalEventDto
import com.adoptu.ports.PetMedicalEventRepositoryPort
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PetMedicalEventRepositoryImpl(private val clock: Clock) : PetMedicalEventRepositoryPort {

    private fun rowToDto(row: ResultRow): PetMedicalEventDto = PetMedicalEventDto(
        id = row[PetMedicalEvents.id],
        petId = row[PetMedicalEvents.petId],
        category = MedicalEventCategory.valueOf(row[PetMedicalEvents.category]),
        name = row[PetMedicalEvents.name],
        administeredDate = row[PetMedicalEvents.administeredDate],
        nextDueDate = row[PetMedicalEvents.nextDueDate],
        notes = row[PetMedicalEvents.notes],
        reminder7dSent = row[PetMedicalEvents.reminder7dSent],
        reminderDueSent = row[PetMedicalEvents.reminderDueSent],
        reminderOverdueSent = row[PetMedicalEvents.reminderOverdueSent],
        createdAt = row[PetMedicalEvents.createdAt]
    )

    override suspend fun create(petId: Int, request: CreatePetMedicalEventRequest): PetMedicalEventDto = withContext(dbDispatcher) {
        val createdAt = clock.now().toEpochMilliseconds()
        transaction {
            val id = PetMedicalEvents.insert {
                it[PetMedicalEvents.petId] = petId
                it[PetMedicalEvents.category] = request.category.name
                it[PetMedicalEvents.name] = request.name
                it[PetMedicalEvents.administeredDate] = request.administeredDate
                it[PetMedicalEvents.nextDueDate] = request.nextDueDate
                it[PetMedicalEvents.notes] = request.notes
                it[PetMedicalEvents.createdAt] = createdAt
            } get PetMedicalEvents.id
            PetMedicalEventDto(
                id = id,
                petId = petId,
                category = request.category,
                name = request.name,
                administeredDate = request.administeredDate,
                nextDueDate = request.nextDueDate,
                notes = request.notes,
                createdAt = createdAt
            )
        }
    }

    override suspend fun getForPet(petId: Int): List<PetMedicalEventDto> = withContext(dbDispatcher) {
        transaction {
            PetMedicalEvents.selectAll()
                .where { PetMedicalEvents.petId eq petId }
                .orderBy(PetMedicalEvents.administeredDate, SortOrder.DESC)
                .map(::rowToDto)
        }
    }

    override suspend fun getById(id: Int): PetMedicalEventDto? = withContext(dbDispatcher) {
        transaction {
            PetMedicalEvents.selectAll()
                .where { PetMedicalEvents.id eq id }
                .firstOrNull()
                ?.let(::rowToDto)
        }
    }

    override suspend fun delete(id: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            PetMedicalEvents.deleteWhere { PetMedicalEvents.id eq id } > 0
        }
    }

    override suspend fun getEventsWithPendingReminders(): List<PetMedicalEventDto> = withContext(dbDispatcher) {
        transaction {
            PetMedicalEvents.selectAll()
                .where {
                    PetMedicalEvents.nextDueDate.isNotNull() and
                        ((PetMedicalEvents.reminder7dSent eq false) or
                            (PetMedicalEvents.reminderDueSent eq false) or
                            (PetMedicalEvents.reminderOverdueSent eq false))
                }
                .map(::rowToDto)
        }
    }

    override suspend fun markReminder7dSent(id: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            PetMedicalEvents.update({ PetMedicalEvents.id eq id }) { it[PetMedicalEvents.reminder7dSent] = true } > 0
        }
    }

    override suspend fun markReminderDueSent(id: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            PetMedicalEvents.update({ PetMedicalEvents.id eq id }) { it[PetMedicalEvents.reminderDueSent] = true } > 0
        }
    }

    override suspend fun markReminderOverdueSent(id: Int): Boolean = withContext(dbDispatcher) {
        transaction {
            PetMedicalEvents.update({ PetMedicalEvents.id eq id }) { it[PetMedicalEvents.reminderOverdueSent] = true } > 0
        }
    }
}

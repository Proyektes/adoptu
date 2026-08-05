package com.adoptu.ports

import com.adoptu.dto.input.CreatePetMedicalEventRequest
import com.adoptu.dto.input.PetMedicalEventDto

interface PetMedicalEventRepositoryPort {
    suspend fun create(petId: Int, request: CreatePetMedicalEventRequest): PetMedicalEventDto
    suspend fun getForPet(petId: Int): List<PetMedicalEventDto>
    suspend fun getById(id: Int): PetMedicalEventDto?
    suspend fun delete(id: Int): Boolean
    // Records with a nextDueDate set and at least one reminder stage not yet sent -
    // MedicalReminderService's scan set, kept small rather than scanning every event ever created.
    suspend fun getEventsWithPendingReminders(): List<PetMedicalEventDto>
    suspend fun markReminder7dSent(id: Int): Boolean
    suspend fun markReminderDueSent(id: Int): Boolean
    suspend fun markReminderOverdueSent(id: Int): Boolean
}

package com.adoptu.services

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.PetMedicalEventRepositoryImpl
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.dto.input.CreatePetMedicalEventRequest
import com.adoptu.dto.input.Gender
import com.adoptu.dto.input.MedicalEventCategory
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class PetMedicalEventServiceTest {

    private lateinit var service: PetMedicalEventService
    private lateinit var petRepository: PetRepositoryImpl
    private val clock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        transaction {
            try {
                Users.insert {
                    it[Users.id] = 1
                    it[Users.username] = "rescuer@mocks.com"
                    it[Users.displayName] = "Test Rescuer"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 1
                    it[UserActiveRoles.role] = "RESCUER"
                }
            } catch (e: Exception) { /* already exists */ }
            try {
                Users.insert {
                    it[Users.id] = 2
                    it[Users.username] = "other@mocks.com"
                    it[Users.displayName] = "Other User"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
            } catch (e: Exception) { /* already exists */ }
            exec("DELETE FROM pet_medical_events")
            exec("DELETE FROM pets")
        }
        petRepository = PetRepositoryImpl(clock)
        val medicalEventRepository = PetMedicalEventRepositoryImpl(clock)
        service = PetMedicalEventService(medicalEventRepository, petRepository)
    }

    private suspend fun createTestPet(rescuerId: Int) = petRepository.create(
        rescuerId = rescuerId,
        name = "Buddy",
        type = "DOG",
        description = "Test",
        weight = 10.0,
        ageYears = 2,
        ageMonths = 0,
        sex = Gender.MALE,
        country = "United States"
    )

    private fun sampleRequest(category: MedicalEventCategory = MedicalEventCategory.VACCINATION) = CreatePetMedicalEventRequest(
        category = category,
        name = "Rabies",
        administeredDate = clock.now().toEpochMilliseconds(),
        nextDueDate = null,
        notes = null
    )

    @Test
    fun `create succeeds for the owning rescuer`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)

        val result = service.create(pet.id, 1, setOf("RESCUER"), sampleRequest())

        assertTrue(result is ServiceResult.Success)
        assertEquals("Rabies", result.data.name)
        assertEquals(MedicalEventCategory.VACCINATION, result.data.category)
    }

    @Test
    fun `create returns Forbidden for a non-owner`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)

        val result = service.create(pet.id, 2, setOf("RESCUER"), sampleRequest())

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `create allows admin regardless of ownership`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)

        val result = service.create(pet.id, 999, setOf("ADMIN"), sampleRequest())

        assertTrue(result is ServiceResult.Success)
    }

    @Test
    fun `create returns NotFound for a non-existent pet`() = runBlocking {
        val result = service.create(999, 1, setOf("RESCUER"), sampleRequest())

        assertEquals(ServiceResult.NotFound, result)
    }

    @Test
    fun `create returns Error for a blank name`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)

        val result = service.create(pet.id, 1, setOf("RESCUER"), sampleRequest().copy(name = "  "))

        assertTrue(result is ServiceResult.Error)
    }

    @Test
    fun `getForPet returns all records for a pet`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)
        service.create(pet.id, 1, setOf("RESCUER"), sampleRequest(MedicalEventCategory.VACCINATION))
        service.create(pet.id, 1, setOf("RESCUER"), sampleRequest(MedicalEventCategory.DEWORMING).copy(name = "Pyrantel"))

        val result = service.getForPet(pet.id)

        assertEquals(2, result.size)
    }

    @Test
    fun `delete succeeds for the owning rescuer`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)
        val created = service.create(pet.id, 1, setOf("RESCUER"), sampleRequest())
        val eventId = (created as ServiceResult.Success).data.id

        val result = service.delete(eventId, 1, setOf("RESCUER"))

        assertTrue(result is ServiceResult.Success)
        assertEquals(0, service.getForPet(pet.id).size)
    }

    @Test
    fun `delete returns Forbidden for a non-owner`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)
        val created = service.create(pet.id, 1, setOf("RESCUER"), sampleRequest())
        val eventId = (created as ServiceResult.Success).data.id

        val result = service.delete(eventId, 2, setOf("RESCUER"))

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `delete returns NotFound for a non-existent record`() = runBlocking {
        val result = service.delete(999, 1, setOf("RESCUER"))

        assertEquals(ServiceResult.NotFound, result)
    }
}

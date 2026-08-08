package com.adoptu.services
import com.adoptu.adapters.authkit.AdoptuUserRepositoryAdapter

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.Volunteers
import com.adoptu.adapters.db.repositories.PetEditSuggestionRepositoryImpl
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.adapters.db.repositories.VolunteerRepositoryImpl
import com.adoptu.dto.input.CreatePetEditSuggestionRequest
import com.adoptu.dto.input.Gender
import com.adoptu.dto.input.PetEditSuggestionStatus
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
class PetEditSuggestionServiceTest {

    private lateinit var service: PetEditSuggestionService
    private lateinit var petRepository: PetRepositoryImpl
    private val clock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        transaction {
            fun user(id: Int, username: String) {
                try {
                    Users.insert {
                        it[Users.id] = id
                        it[Users.username] = username
                        it[Users.displayName] = username
                        it[Users.createdAt] = clock.now().toEpochMilliseconds()
                    }
                } catch (e: Exception) { /* already exists */ }
            }
            user(1, "rescuer1@mocks.com")
            user(2, "activevolunteer@mocks.com")
            user(3, "plainuser@mocks.com")
            user(4, "rescuer2@mocks.com")
            fun role(userId: Int, role: String) {
                try {
                    UserActiveRoles.insert {
                        it[UserActiveRoles.userId] = userId
                        it[UserActiveRoles.role] = role
                    }
                } catch (e: Exception) { /* already exists */ }
            }
            role(1, "RESCUER")
            role(4, "RESCUER")
            Volunteers.insert {
                it[Volunteers.rescuerId] = 1
                it[Volunteers.volunteerId] = 2
                it[Volunteers.status] = "ACTIVE"
                it[Volunteers.createdAt] = clock.now().toEpochMilliseconds()
            }
            exec("DELETE FROM pet_edit_suggestions")
            exec("DELETE FROM pets")
        }
        val userRepository = UserRepository(clock)
        petRepository = PetRepositoryImpl(clock)
        val photographerRepository = PhotographerRepositoryImpl(petRepository, userRepository, clock)
        val userService = UserService(userRepository, photographerRepository, AdoptuUserRepositoryAdapter())
        val volunteerRepository = VolunteerRepositoryImpl(userRepository, clock)
        val volunteerService = VolunteerService(volunteerRepository, userService)
        val suggestionRepository = PetEditSuggestionRepositoryImpl(petRepository, userRepository, clock)
        service = PetEditSuggestionService(suggestionRepository, petRepository, volunteerService, clock)
    }

    private suspend fun createTestPet(rescuerId: Int) = petRepository.create(
        rescuerId = rescuerId, name = "Buddy", type = "DOG", description = "Original description",
        weight = 10.0, ageYears = 2, ageMonths = 0, sex = Gender.MALE, country = "United States"
    )

    @Test
    fun `createSuggestion succeeds for an active volunteer`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)

        val result = service.createSuggestion(pet.id, 2, CreatePetEditSuggestionRequest(description = "Updated description"))

        assertTrue(result is ServiceResult.Success)
        assertEquals(PetEditSuggestionStatus.PENDING, result.data.status)
    }

    @Test
    fun `createSuggestion returns Forbidden for a non-volunteer`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)

        val result = service.createSuggestion(pet.id, 3, CreatePetEditSuggestionRequest(description = "Updated description"))

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `createSuggestion returns Forbidden for a volunteer active with a different rescuer`() = runBlocking {
        val pet = createTestPet(rescuerId = 4)

        val result = service.createSuggestion(pet.id, 2, CreatePetEditSuggestionRequest(description = "Updated description"))

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `createSuggestion returns Error when no field is set`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)

        val result = service.createSuggestion(pet.id, 2, CreatePetEditSuggestionRequest())

        assertTrue(result is ServiceResult.Error)
    }

    @Test
    fun `updateStatus APPROVED applies the suggested fields to the pet`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)
        val created = service.createSuggestion(
            pet.id, 2,
            CreatePetEditSuggestionRequest(description = "New description", temperament = "Gentle")
        )
        val id = (created as ServiceResult.Success).data.id

        val result = service.updateStatus(id, PetEditSuggestionStatus.APPROVED, 1, setOf("RESCUER"))

        assertTrue(result is ServiceResult.Success)
        val updatedPet = petRepository.getById(pet.id)
        assertEquals("New description", updatedPet?.description)
        assertEquals("Gentle", updatedPet?.temperament)
    }

    @Test
    fun `updateStatus REJECTED leaves the pet unchanged`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)
        val created = service.createSuggestion(pet.id, 2, CreatePetEditSuggestionRequest(description = "New description"))
        val id = (created as ServiceResult.Success).data.id

        service.updateStatus(id, PetEditSuggestionStatus.REJECTED, 1, setOf("RESCUER"))

        val updatedPet = petRepository.getById(pet.id)
        assertEquals("Original description", updatedPet?.description)
    }

    @Test
    fun `updateStatus returns Forbidden for a non-owner`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)
        val created = service.createSuggestion(pet.id, 2, CreatePetEditSuggestionRequest(description = "New description"))
        val id = (created as ServiceResult.Success).data.id

        val result = service.updateStatus(id, PetEditSuggestionStatus.APPROVED, 4, setOf("RESCUER"))

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `updateStatus returns Error when already reviewed`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)
        val created = service.createSuggestion(pet.id, 2, CreatePetEditSuggestionRequest(description = "New description"))
        val id = (created as ServiceResult.Success).data.id
        service.updateStatus(id, PetEditSuggestionStatus.APPROVED, 1, setOf("RESCUER"))

        val result = service.updateStatus(id, PetEditSuggestionStatus.REJECTED, 1, setOf("RESCUER"))

        assertTrue(result is ServiceResult.Error)
    }

    @Test
    fun `getPendingForRescuer returns Forbidden for a non-owner`() = runBlocking {
        val result = service.getPendingForRescuer(1, 4, setOf("RESCUER"))

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `getPendingForRescuer returns only pending suggestions across the rescuer's pets`() = runBlocking {
        val petA = createTestPet(rescuerId = 1)
        val petB = createTestPet(rescuerId = 1)
        val s1 = service.createSuggestion(petA.id, 2, CreatePetEditSuggestionRequest(description = "A"))
        val s2 = service.createSuggestion(petB.id, 2, CreatePetEditSuggestionRequest(description = "B"))
        service.updateStatus((s2 as ServiceResult.Success).data.id, PetEditSuggestionStatus.APPROVED, 1, setOf("RESCUER"))

        val result = service.getPendingForRescuer(1, 1, setOf("RESCUER"))

        assertTrue(result is ServiceResult.Success)
        assertEquals(1, result.data.size)
        assertEquals((s1 as ServiceResult.Success).data.id, result.data.first().id)
    }

    @Test
    fun `getMySuggestions returns suggestions made by that volunteer`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)
        service.createSuggestion(pet.id, 2, CreatePetEditSuggestionRequest(description = "A"))

        val result = service.getMySuggestions(2)

        assertEquals(1, result.size)
        assertEquals(pet.id, result.first().petId)
    }
}

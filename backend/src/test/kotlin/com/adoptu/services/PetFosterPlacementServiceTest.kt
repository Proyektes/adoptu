package com.adoptu.services

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.PetFosterPlacementRepositoryImpl
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.TemporalHomeRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.CreateFosterPlacementRequest
import com.adoptu.dto.input.CreateTemporalHomeRequest
import com.adoptu.dto.input.Gender
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
class PetFosterPlacementServiceTest {

    private lateinit var service: PetFosterPlacementService
    private lateinit var petRepository: PetRepositoryImpl
    private lateinit var temporalHomeRepository: TemporalHomeRepositoryImpl
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
            user(2, "temporalhome@mocks.com")
            user(3, "rescuer2@mocks.com")
            user(4, "plainuser@mocks.com")
            fun role(userId: Int, role: String) {
                try {
                    UserActiveRoles.insert {
                        it[UserActiveRoles.userId] = userId
                        it[UserActiveRoles.role] = role
                    }
                } catch (e: Exception) { /* already exists */ }
            }
            role(1, "RESCUER")
            role(2, "TEMPORAL_HOME")
            role(3, "RESCUER")
            exec("DELETE FROM pet_foster_placements")
            exec("DELETE FROM pets")
        }
        petRepository = PetRepositoryImpl(clock)
        val userRepository = UserRepository(clock)
        temporalHomeRepository = TemporalHomeRepositoryImpl(petRepository, userRepository, clock)
        val placementRepository = PetFosterPlacementRepositoryImpl(petRepository, temporalHomeRepository, clock)
        val photographerRepository = com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl(petRepository, userRepository, clock)
        val userService = UserService(userRepository, photographerRepository)
        service = PetFosterPlacementService(placementRepository, petRepository, temporalHomeRepository, userService)

        runBlocking {
            temporalHomeRepository.createTemporalHome(2, CreateTemporalHomeRequest(alias = "Casa Feliz", country = "United States", city = "Springfield"))
        }
    }

    private suspend fun createTestPet(rescuerId: Int) = petRepository.create(
        rescuerId = rescuerId, name = "Buddy", type = "DOG", description = "Test",
        weight = 10.0, ageYears = 2, ageMonths = 0, sex = Gender.MALE, country = "United States"
    )

    @Test
    fun `createPlacement succeeds for the owning rescuer`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)

        val result = service.createPlacement(pet.id, 1, setOf("RESCUER"), CreateFosterPlacementRequest(temporalHomeId = 2))

        assertTrue(result is ServiceResult.Success)
        assertEquals(2, result.data.temporalHomeId)
        assertEquals("Casa Feliz", result.data.temporalHomeAlias)
    }

    @Test
    fun `createPlacement returns Forbidden for a non-owner`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)

        val result = service.createPlacement(pet.id, 3, setOf("RESCUER"), CreateFosterPlacementRequest(temporalHomeId = 2))

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `createPlacement returns NotFound for a non-existent pet`() = runBlocking {
        val result = service.createPlacement(999, 1, setOf("RESCUER"), CreateFosterPlacementRequest(temporalHomeId = 2))

        assertEquals(ServiceResult.NotFound, result)
    }

    @Test
    fun `createPlacement returns Error when target is not an active temporal home`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)

        val result = service.createPlacement(pet.id, 1, setOf("RESCUER"), CreateFosterPlacementRequest(temporalHomeId = 4))

        assertTrue(result is ServiceResult.Error)
    }

    @Test
    fun `createPlacement returns Error when the pet already has an active placement`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)
        service.createPlacement(pet.id, 1, setOf("RESCUER"), CreateFosterPlacementRequest(temporalHomeId = 2))

        val result = service.createPlacement(pet.id, 1, setOf("RESCUER"), CreateFosterPlacementRequest(temporalHomeId = 2))

        assertTrue(result is ServiceResult.Error)
    }

    @Test
    fun `createPlacement returns Error when the temporal home is at capacity`() = runBlocking {
        temporalHomeRepository.updateTemporalHome(2, com.adoptu.dto.input.UpdateTemporalHomeRequest(maxCapacity = 1))
        val petA = createTestPet(rescuerId = 1)
        val petB = createTestPet(rescuerId = 1)
        service.createPlacement(petA.id, 1, setOf("RESCUER"), CreateFosterPlacementRequest(temporalHomeId = 2))

        val result = service.createPlacement(petB.id, 1, setOf("RESCUER"), CreateFosterPlacementRequest(temporalHomeId = 2))

        assertTrue(result is ServiceResult.Error)
    }

    @Test
    fun `createPlacement succeeds when under capacity`() = runBlocking {
        temporalHomeRepository.updateTemporalHome(2, com.adoptu.dto.input.UpdateTemporalHomeRequest(maxCapacity = 2))
        val petA = createTestPet(rescuerId = 1)
        val petB = createTestPet(rescuerId = 1)
        service.createPlacement(petA.id, 1, setOf("RESCUER"), CreateFosterPlacementRequest(temporalHomeId = 2))

        val result = service.createPlacement(petB.id, 1, setOf("RESCUER"), CreateFosterPlacementRequest(temporalHomeId = 2))

        assertTrue(result is ServiceResult.Success)
    }

    @Test
    fun `endPlacement succeeds for the owning rescuer and allows a new placement afterward`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)
        val created = service.createPlacement(pet.id, 1, setOf("RESCUER"), CreateFosterPlacementRequest(temporalHomeId = 2))
        val placementId = (created as ServiceResult.Success).data.id

        val endResult = service.endPlacement(placementId, 1, setOf("RESCUER"))
        assertTrue(endResult is ServiceResult.Success)

        val secondPlacement = service.createPlacement(pet.id, 1, setOf("RESCUER"), CreateFosterPlacementRequest(temporalHomeId = 2))
        assertTrue(secondPlacement is ServiceResult.Success)
    }

    @Test
    fun `endPlacement returns Forbidden for a non-owner`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)
        val created = service.createPlacement(pet.id, 1, setOf("RESCUER"), CreateFosterPlacementRequest(temporalHomeId = 2))
        val placementId = (created as ServiceResult.Success).data.id

        val result = service.endPlacement(placementId, 3, setOf("RESCUER"))

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `endPlacement returns Error when already ended`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)
        val created = service.createPlacement(pet.id, 1, setOf("RESCUER"), CreateFosterPlacementRequest(temporalHomeId = 2))
        val placementId = (created as ServiceResult.Success).data.id
        service.endPlacement(placementId, 1, setOf("RESCUER"))

        val result = service.endPlacement(placementId, 1, setOf("RESCUER"))

        assertTrue(result is ServiceResult.Error)
    }

    @Test
    fun `getHistoryForPet returns Forbidden for a non-owner`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)

        val result = service.getHistoryForPet(pet.id, 3, setOf("RESCUER"))

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `getMyActivePlacements returns only active placements for that temporal home`() = runBlocking {
        val petA = createTestPet(rescuerId = 1)
        val petB = createTestPet(rescuerId = 1)
        service.createPlacement(petA.id, 1, setOf("RESCUER"), CreateFosterPlacementRequest(temporalHomeId = 2))
        val secondCreated = service.createPlacement(petB.id, 1, setOf("RESCUER"), CreateFosterPlacementRequest(temporalHomeId = 2))
        service.endPlacement((secondCreated as ServiceResult.Success).data.id, 1, setOf("RESCUER"))

        val result = service.getMyActivePlacements(2)

        assertEquals(1, result.size)
        assertEquals(petA.id, result.first().petId)
    }
}

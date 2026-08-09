package com.adoptu.services

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.PetFavoriteRepositoryImpl
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.dto.input.Gender
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class PetFavoriteServiceTest {

    private lateinit var service: PetFavoriteService
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
            exec("DELETE FROM pet_favorites")
            exec("DELETE FROM pets")
        }
        petRepository = PetRepositoryImpl(clock)
        service = PetFavoriteService(PetFavoriteRepositoryImpl(clock), petRepository)
    }

    private suspend fun createPet(): Int = petRepository.create(
        rescuerId = 1, name = "Buddy", type = "DOG", description = "Test",
        weight = 10.0, ageYears = 2, ageMonths = 0, sex = Gender.MALE, country = "United States"
    ).id

    @Test
    fun `add favorites an existing pet`() = runBlocking {
        val petId = createPet()

        val result = service.add(userId = 1, petId = petId)

        assertTrue(result is ServiceResult.Success)
        assertEquals(listOf(petId), service.getFavoritePetIds(1))
    }

    @Test
    fun `add returns NotFound for a pet that does not exist`() = runBlocking {
        val result = service.add(userId = 1, petId = 999999)

        assertEquals(ServiceResult.NotFound, result)
        assertTrue(service.getFavoritePetIds(1).isEmpty())
    }

    @Test
    fun `remove unfavorites a pet`() = runBlocking {
        val petId = createPet()
        service.add(userId = 1, petId = petId)

        val result = service.remove(userId = 1, petId = petId)

        assertTrue(result is ServiceResult.Success)
        assertTrue(service.getFavoritePetIds(1).isEmpty())
    }

    @Test
    fun `getFavoritePets returns the full pet DTOs for favorited pets`() = runBlocking {
        val petId = createPet()
        service.add(userId = 1, petId = petId)

        val favorites = service.getFavoritePets(1)

        assertEquals(1, favorites.size)
        assertEquals(petId, favorites.first().id)
        assertEquals("Buddy", favorites.first().name)
    }
}

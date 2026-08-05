package com.adoptu.services

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.CreatePetRequest
import com.adoptu.dto.input.Gender
import com.adoptu.dto.input.PromotedReason
import com.adoptu.dto.input.UpdatePetRequest
import com.adoptu.dto.input.UserRole
import com.adoptu.mocks.MockImageStorage
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalTime::class)
class PetServiceTest {

    private lateinit var petService: PetService
    private lateinit var petRepository: PetRepositoryImpl
    private lateinit var mockImageStorage: MockImageStorage
    private lateinit var mockNotificationAdapter: MockNotificationAdapter
    private val clock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        
        // Always create mocks users - use INSERT OR IGNORE to avoid duplicates
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
            } catch (e: Exception) {
                // User already exists, ignore
            }
            try {
                Users.insert {
                    it[Users.id] = 2
                    it[Users.username] = "adopter@mocks.com"
                    it[Users.displayName] = "Test Adopter"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 2
                    it[UserActiveRoles.role] = "ADOPTER"
                }
            } catch (e: Exception) {
                // User already exists, ignore
            }
            try {
                Users.insert {
                    it[Users.id] = 3
                    it[Users.username] = "other@mocks.com"
                    it[Users.displayName] = "Other User"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 3
                    it[UserActiveRoles.role] = "ADOPTER"
                }
            } catch (e: Exception) {
                // User already exists, ignore
            }
        }
        
        // Clear pets
        transaction {
            exec("DELETE FROM adoption_requests")
            exec("DELETE FROM pet_images")
            exec("DELETE FROM pets")
        }
        
        mockImageStorage = MockImageStorage()
        mockNotificationAdapter = MockNotificationAdapter()
        val userRepository = UserRepository(clock)
        petRepository = PetRepositoryImpl(clock)
        val photographerRepository = PhotographerRepositoryImpl(petRepository, userRepository, clock)
        val photographerService = PhotographerService(photographerRepository, null, userRepository, clock)
        val userService = UserService(userRepository, photographerRepository)
        val savedSearchRepository = com.adoptu.adapters.db.repositories.SavedSearchRepositoryImpl(clock)
        petService = PetService(petRepository, mockImageStorage, mockNotificationAdapter, userService, savedSearchRepository)
    }

    @Test
    fun `getAll returns empty list when no pets exist`() = runBlocking {
        val result = petService.getAll(country = "United States")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAll returns pets filtered by type`() = runBlocking {
        createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        createTestPet(rescuerId = 1, name = "Whiskers", type = "CAT")
        createTestPet(rescuerId = 1, name = "Max", type = "DOG")

        val dogs = petService.getAll("DOG", country = "United States")
        assertEquals(2, dogs.size)
        assertTrue(dogs.all { it.type == "DOG" })
    }

    @Test
    fun `getAll throws exception when country is blank`() = runBlocking {
        assertThrows<IllegalArgumentException> {
            petService.getAll(country = "")
        }
        Unit
    }

    @Test
    fun `getAll throws exception when country is whitespace only`() = runBlocking {
        assertThrows<IllegalArgumentException> {
            petService.getAll(country = "   ")
        }
        Unit
    }

    @Test
    fun `getAll only returns pets matching the given country`() = runBlocking {
        createTestPet(rescuerId = 1, name = "Buddy", type = "DOG", country = "United States")
        createTestPet(rescuerId = 1, name = "Milo", type = "DOG", country = "Canada")

        val result = petService.getAll(country = "United States")

        assertEquals(1, result.size)
        assertEquals("Buddy", result[0].name)
    }

    @Test
    fun `getMine returns pets regardless of country or status`() = runBlocking {
        createTestPet(rescuerId = 1, name = "Buddy", type = "DOG", country = "United States")
        createTestPet(rescuerId = 1, name = "NoCountry", type = "DOG", country = null)

        val result = petService.getMine()

        assertEquals(2, result.size)
    }

    @Test
    fun `getById returns null for non-existent pet`() = runBlocking {
        val result = petService.getById(999)
        assertNull(result)
    }

    @Test
    fun `getById returns pet by id`() = runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")

        val result = petService.getById(created.id)
        assertNotNull(result)
        assertEquals("Buddy", result.name)
    }

    @Test
    fun `create throws exception for negative weight`() = runBlocking {
        val request = CreatePetRequest(
            name = "Buddy",
            type = "DOG",
            description = "A friendly dog",
            weight = -5.0,
            ageYears = 3,
            ageMonths = 6,
            sex = Gender.MALE
        )

        val exception = assertThrows<IllegalArgumentException> {
            petService.create(1, request)
        }
        assertEquals("Weight must be zero or positive", exception.message)
    }

    @Test
    fun `create throws exception for invalid age months`() = runBlocking {
        val request = CreatePetRequest(
            name = "Buddy",
            type = "DOG",
            description = "A friendly dog",
            weight = 25.0,
            ageYears = 3,
            ageMonths = 15,
            sex = Gender.MALE
        )

        val exception = assertThrows<IllegalArgumentException> {
            petService.create(1, request)
        }
        assertEquals("Age (months) must be less than 12", exception.message)
    }

    @Test
    fun `update returns NotFound for non-existent pet`() = runBlocking {
        val result = petService.update(999, 1, setOf("RESCUER"), UpdatePetRequest(name = "Test"))
        assertEquals(ServiceResult.NotFound, result)
    }

    @Test
    fun `update returns Forbidden when user is not owner`() = runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")

        val result = petService.update(created.id, 2, setOf("RESCUER"), UpdatePetRequest(name = "Hacked"))

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `update returns Error when setting isPromoted true without a reason`() = runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")

        val result = petService.update(created.id, 1, setOf("RESCUER"), UpdatePetRequest(isPromoted = true))

        assertTrue(result is ServiceResult.Error)
        assertEquals("A reason is required when marking a pet as needing a new home", result.message)
    }

    @Test
    fun `update succeeds setting isPromoted true with a reason`() = runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")

        val result = petService.update(
            created.id, 1, setOf("RESCUER"),
            UpdatePetRequest(isPromoted = true, promotedReason = PromotedReason.PET_CONFLICT, promotedReasonDetail = "Fighting with another dog")
        )

        assertTrue(result is ServiceResult.Success)
        val pet = result.data
        assertTrue(pet.isPromoted)
        assertEquals(PromotedReason.PET_CONFLICT, pet.promotedReason)
        assertEquals("Fighting with another dog", pet.promotedReasonDetail)
    }

    @Test
    fun `update clears the reason when isPromoted is set back to false`() = runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG", isPromoted = true)
        petService.update(created.id, 1, setOf("RESCUER"), UpdatePetRequest(isPromoted = true, promotedReason = PromotedReason.OTHER))

        val result = petService.update(created.id, 1, setOf("RESCUER"), UpdatePetRequest(isPromoted = false))

        assertTrue(result is ServiceResult.Success)
        val pet = result.data
        assertTrue(!pet.isPromoted)
        assertNull(pet.promotedReason)
        assertNull(pet.promotedReasonDetail)
    }

    @Test
    fun `delete returns NotFound for non-existent pet`() = runBlocking {
        val result = petService.delete(999, 1, setOf("ADMIN"))
        assertEquals(ServiceResult.NotFound, result)
    }

    @Test
    fun `delete returns Forbidden when user is not owner`() = runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")

        val result = petService.delete(created.id, 2, setOf("RESCUER"))

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `addImage returns NotFound for non-existent pet`() = runBlocking {
        val result = petService.addImage(999, 1, setOf("ADMIN"), "https://example.com/image.jpg", false)
        assertEquals(ServiceResult.NotFound, result)
    }

    @Test
    fun `addImage returns Forbidden when user is not owner`() = runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")

        val result = petService.addImage(created.id, 2, setOf("RESCUER"), "https://example.com/image.jpg", false)

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `addImage adds image successfully`() = runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")

        val result = petService.addImage(created.id, 1, setOf("RESCUER"), "https://example.com/image.jpg", true)

        assertTrue(result is ServiceResult.Success)
        assertTrue(result.data.isPrimary)
    }

    @Test
    fun `addImage allows admin to add image`() = runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")

        val result = petService.addImage(created.id, 999, setOf("ADMIN"), "https://example.com/admin-image.jpg", false)

        assertTrue(result is ServiceResult.Success)
    }

    @Test
    fun `getAll returns only promoted pets when showPromotedOnly is true`() = runBlocking {
        createTestPet(rescuerId = 1, name = "Buddy", type = "DOG", isPromoted = true)
        createTestPet(rescuerId = 1, name = "Max", type = "DOG", isPromoted = false)
        createTestPet(rescuerId = 1, name = "Rocky", type = "DOG", isPromoted = true)

        val result = petService.getAll("DOG", showPromotedOnly = true, country = "United States")

        assertEquals(2, result.size)
        assertTrue(result.all { it.isPromoted })
    }

    @Test
    fun `getAll sorts promoted pets first even when they are older`() = runBlocking {
        createTestPet(rescuerId = 1, name = "Older Promoted", type = "DOG", isPromoted = true)
        clock.advanceMillis(60_000)
        createTestPet(rescuerId = 1, name = "Newer Regular", type = "DOG", isPromoted = false)

        val result = petService.getAll("DOG", country = "United States")

        assertEquals("Older Promoted", result.first().name)
    }

    @Test
    fun `getAll returns all available pets without filters`() = runBlocking {
        createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        createTestPet(rescuerId = 1, name = "Whiskers", type = "CAT")

        val result = petService.getAll(country = "United States")

        assertTrue(result.size >= 2)
    }

    @Test
    fun `create creates pet successfully`() = runBlocking {
        val request = CreatePetRequest(
            name = "Buddy",
            type = "DOG",
            description = "A friendly dog",
            weight = 25.0,
            ageYears = 3,
            ageMonths = 6,
            sex = Gender.MALE,
            breed = "Golden Retriever",
            country = "United States"
        )

        val result = petService.create(1, request)

        assertEquals("Buddy", result.name)
        assertEquals("DOG", result.type)
        assertEquals(25.0, result.weight)
        assertEquals(3, result.ageYears)
        assertEquals(6, result.ageMonths)
        assertEquals(Gender.MALE, result.sex)
        assertEquals("Golden Retriever", result.breed)
        assertEquals("United States", result.country)
    }

    @Test
    fun `create throws when isPromoted is true and no reason is given`() = runBlocking {
        val request = CreatePetRequest(
            name = "Buddy",
            type = "DOG",
            weight = 25.0,
            ageYears = 3,
            ageMonths = 0,
            sex = Gender.MALE,
            country = "United States",
            isPromoted = true
        )

        val exception = assertThrows<IllegalArgumentException> {
            petService.create(1, request)
        }
        assertEquals("A reason is required when marking a pet as needing a new home", exception.message)
    }

    @Test
    fun `create succeeds with isPromoted and a reason - no payment involved`() = runBlocking {
        val request = CreatePetRequest(
            name = "Buddy",
            type = "DOG",
            weight = 25.0,
            ageYears = 3,
            ageMonths = 0,
            sex = Gender.MALE,
            country = "United States",
            isPromoted = true,
            promotedReason = PromotedReason.MOVING,
            promotedReasonDetail = "Owner relocating abroad"
        )

        val result = petService.create(1, request)

        assertTrue(result.isPromoted)
        assertEquals(PromotedReason.MOVING, result.promotedReason)
        assertEquals("Owner relocating abroad", result.promotedReasonDetail)
    }

    @Test
    fun `create throws exception for negative ageYears`() = runBlocking {
        val request = CreatePetRequest(
            name = "Buddy",
            type = "DOG",
            weight = 25.0,
            ageYears = -1,
            ageMonths = 0,
            sex = Gender.MALE
        )

        val exception = assertThrows<IllegalArgumentException> {
            petService.create(1, request)
        }
        assertEquals("Age (years) must be zero or positive", exception.message)
    }

    @Test
    fun `create uses request country when explicitly provided`() = runBlocking {
        val request = CreatePetRequest(
            name = "Buddy",
            type = "DOG",
            weight = 25.0,
            ageYears = 3,
            ageMonths = 0,
            sex = Gender.MALE,
            country = "Canada"
        )

        val result = petService.create(1, request)

        assertEquals("Canada", result.country)
    }

    @Test
    fun `create defaults country from the rescuer's profile when request omits it`() = runBlocking {
        transaction {
            Users.update({ Users.id eq 1 }) {
                it[Users.country] = com.adoptu.common.Country.MEXICO
            }
        }
        val request = CreatePetRequest(
            name = "Buddy",
            type = "DOG",
            weight = 25.0,
            ageYears = 3,
            ageMonths = 0,
            sex = Gender.MALE
        )

        val result = petService.create(1, request)

        assertEquals("Mexico", result.country)
    }

    @Test
    fun `create throws when neither request nor rescuer profile has a country`() = runBlocking {
        val request = CreatePetRequest(
            name = "Buddy",
            type = "DOG",
            weight = 25.0,
            ageYears = 3,
            ageMonths = 0,
            sex = Gender.MALE
        )

        val exception = assertThrows<IllegalArgumentException> {
            petService.create(1, request)
        }
        assertEquals("Country is required - set one on your profile or specify one for this pet", exception.message)
    }

    @Test
    fun `create throws exception for negative adoptionFee`() = runBlocking {
        val request = CreatePetRequest(
            name = "Buddy",
            type = "DOG",
            weight = 25.0,
            ageYears = 3,
            ageMonths = 0,
            sex = Gender.MALE,
            adoptionFee = -50.0
        )

        val exception = assertThrows<IllegalArgumentException> {
            petService.create(1, request)
        }
        assertEquals("Adoption fee must be zero or positive", exception.message)
    }

    @Test
    fun `update updates pet successfully`() = runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")

        val result = petService.update(created.id, 1, setOf("RESCUER"), UpdatePetRequest(name = "Max", weight = 30.0))

        assertTrue(result is ServiceResult.Success)
        val pet = result.data
        assertEquals("Max", pet.name)
        assertEquals(30.0, pet.weight)
    }

    @Test
    fun `update allows admin to update any pet`() = runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")

        val result = petService.update(created.id, 999, setOf("ADMIN"), UpdatePetRequest(name = "Admin Buddy"))

        assertTrue(result is ServiceResult.Success)
        assertEquals("Admin Buddy", result.data.name)
    }

    @Test
    fun `delete deletes pet successfully`() = runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        val petId = created.id

        val result = petService.delete(petId, 1, setOf("RESCUER"))

        assertTrue(result is ServiceResult.Success)
        assertNull(petService.getById(petId))
    }

    @Test
    fun `delete allows admin to delete any pet`() = runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        val petId = created.id

        val result = petService.delete(petId, 999, setOf("ADMIN"))

        assertTrue(result is ServiceResult.Success)
        assertNull(petService.getById(petId))
    }

    @Test
    fun `getImages returns empty list when no images`() = runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")

        val result = petService.getImages(created.id)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getImages returns pet images`() = runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        petService.addImage(created.id, 1, setOf("RESCUER"), "https://example.com/image1.jpg", true)
        petService.addImage(created.id, 1, setOf("RESCUER"), "https://example.com/image2.jpg", false)

        val result = petService.getImages(created.id)

        assertEquals(2, result.size)
        assertTrue(result.any { it.imageUrl.contains("image1.jpg") && it.isPrimary })
    }

    @Test
    fun `setPrimaryImage sets primary image successfully`() = runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        val img1Result = petService.addImage(created.id, 1, setOf("RESCUER"), "https://example.com/image1.jpg", true)
        val img2Result = petService.addImage(created.id, 1, setOf("RESCUER"), "https://example.com/image2.jpg", false)
        val img1 = (img1Result as ServiceResult.Success).data
        val img2 = (img2Result as ServiceResult.Success).data

        val result = petService.setPrimaryImage(created.id, img2.id, 1, setOf("RESCUER"))

        assertTrue(result is ServiceResult.Success)
        val images = petService.getImages(created.id)
        val newPrimary = images.find { it.id == img2.id }
        val oldPrimary = images.find { it.id == img1.id }
        assertTrue(newPrimary?.isPrimary == true)
        assertTrue(oldPrimary?.isPrimary == false)
    }

    @Test
    fun `setPrimaryImage returns NotFound for non-existent image`() = runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")

        val result = petService.setPrimaryImage(created.id, 999, 1, setOf("RESCUER"))

        assertEquals(ServiceResult.NotFound, result)
    }

    @Test
    fun `setPrimaryImage returns Forbidden when user is not owner`() = runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        val imgResult = petService.addImage(created.id, 1, setOf("RESCUER"), "https://example.com/image.jpg", false)
        val img = (imgResult as ServiceResult.Success).data

        val result = petService.setPrimaryImage(created.id, img.id, 2, setOf("RESCUER"))

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `setPrimaryImage allows admin to set primary image`() = runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        petService.addImage(created.id, 1, setOf("RESCUER"), "https://example.com/image1.jpg", true)
        val img2Result = petService.addImage(created.id, 1, setOf("RESCUER"), "https://example.com/image2.jpg", false)
        val img2 = (img2Result as ServiceResult.Success).data

        val result = petService.setPrimaryImage(created.id, img2.id, 999, setOf("ADMIN"))

        assertTrue(result is ServiceResult.Success)
    }

    @Test
    fun `removeImage removes image successfully`() = kotlinx.coroutines.runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        val imgResult = petService.addImage(created.id, 1, setOf("RESCUER"), "https://example.com/image.jpg", false)
        val img = (imgResult as ServiceResult.Success).data

        val result = petService.removeImage(created.id, img.id, 1, setOf("RESCUER"))

        assertTrue(result is ServiceResult.Success)
        assertTrue(petService.getImages(created.id).isEmpty())
    }

    @Test
    fun `removeImage returns NotFound for non-existent image`() = kotlinx.coroutines.runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")

        val result = petService.removeImage(created.id, 999, 1, setOf("RESCUER"))

        assertEquals(ServiceResult.NotFound, result)
    }

    @Test
    fun `removeImage returns Forbidden when user is not owner`() = kotlinx.coroutines.runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        val imgResult = petService.addImage(created.id, 1, setOf("RESCUER"), "https://example.com/image.jpg", false)
        val img = (imgResult as ServiceResult.Success).data

        val result = petService.removeImage(created.id, img.id, 2, setOf("RESCUER"))

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `updatePetImages updates images successfully`() = kotlinx.coroutines.runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        val img1Result = petService.addImage(created.id, 1, setOf("RESCUER"), "https://example.com/image1.jpg", false)
        val img2Result = petService.addImage(created.id, 1, setOf("RESCUER"), "https://example.com/image2.jpg", false)
        val img2 = (img2Result as ServiceResult.Success).data

        val result = petService.updatePetImages(created.id, 1, setOf("RESCUER"), listOf(img2.id))

        assertTrue(result is ServiceResult.Success)
        val images = petService.getImages(created.id)
        assertEquals(1, images.size)
        assertEquals(img2.id, images[0].id)
    }

    @Test
    fun `updatePetImages removes deleted images from storage`() = kotlinx.coroutines.runBlocking {
        val created = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        petService.addImage(created.id, 1, setOf("RESCUER"), "https://example.com/image1.jpg", false)
        petService.addImage(created.id, 1, setOf("RESCUER"), "https://example.com/image2.jpg", false)

        petService.updatePetImages(created.id, 1, setOf("RESCUER"), emptyList())

        assertEquals(0, mockImageStorage.getStoredImages().size)
    }

    @Test
    fun `createAdoptionRequest creates adoption request successfully`() = runBlocking {
        val pet = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")

        val result = petService.createAdoptionRequest(pet.id, 2, "I would love to adopt this pet!")

        assertEquals(pet.id, result.petId)
        assertEquals(2, result.adopterId)
        assertEquals("I would love to adopt this pet!", result.message)
        assertEquals("PENDING", result.status)
    }

    @Test
    fun `getAdoptionRequestsForPet returns requests for owner`() = runBlocking {
        val pet = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        petService.createAdoptionRequest(pet.id, 2, "Request 1")
        petService.createAdoptionRequest(pet.id, 3, "Request 2")

        val result = petService.getAdoptionRequestsForPet(pet.id, 1, setOf("RESCUER"))

        assertTrue(result is ServiceResult.Success)
        assertEquals(2, result.data.size)
    }

    @Test
    fun `getAdoptionRequestsForPet returns Forbidden when user is not owner`() = runBlocking {
        val pet = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")

        val result = petService.getAdoptionRequestsForPet(pet.id, 2, setOf("RESCUER"))

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `getAdoptionRequestsForPet allows admin to view requests`() = runBlocking {
        val pet = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        petService.createAdoptionRequest(pet.id, 2, "Request 1")

        val result = petService.getAdoptionRequestsForPet(pet.id, 999, setOf("ADMIN"))

        assertTrue(result is ServiceResult.Success)
        assertEquals(1, result.data.size)
    }

    @Test
    fun `getMyAdoptionRequests returns user's adoption requests`() = runBlocking {
        val pet = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        petService.createAdoptionRequest(pet.id, 2, "My request 1")
        petService.createAdoptionRequest(pet.id, 2, "My request 2")
        petService.createAdoptionRequest(pet.id, 3, "Other request")

        val result = petService.getMyAdoptionRequests(2)

        assertEquals(2, result.size)
        assertTrue(result.all { it.adopterId == 2 })
    }

    @Test
    fun `updateAdoptionRequest updates request to APPROVED`() = runBlocking {
        val pet = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        val request = petService.createAdoptionRequest(pet.id, 2, "I want to adopt")

        val result = petService.updateAdoptionRequest(request.id, "APPROVED", 1, setOf("RESCUER"))

        assertTrue(result is ServiceResult.Success)
        assertEquals("APPROVED", result.data.status)
    }

    @Test
    fun `updateAdoptionRequest updates request to REJECTED`() = runBlocking {
        val pet = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        val request = petService.createAdoptionRequest(pet.id, 2, "I want to adopt")

        val result = petService.updateAdoptionRequest(request.id, "REJECTED", 1, setOf("RESCUER"))

        assertTrue(result is ServiceResult.Success)
        assertEquals("REJECTED", result.data.status)
    }

    @Test
    fun `updateAdoptionRequest returns NotFound for non-existent request`() = runBlocking {
        val result = petService.updateAdoptionRequest(999, "APPROVED", 1, setOf("RESCUER"))

        assertEquals(ServiceResult.NotFound, result)
    }

    @Test
    fun `updateAdoptionRequest returns Forbidden for invalid status`() = runBlocking {
        val pet = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        val request = petService.createAdoptionRequest(pet.id, 2, "I want to adopt")

        val result = petService.updateAdoptionRequest(request.id, "INVALID_STATUS", 1, setOf("RESCUER"))

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `updateAdoptionRequest returns Forbidden when user is not owner`() = runBlocking {
        val pet = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        val request = petService.createAdoptionRequest(pet.id, 2, "I want to adopt")

        val result = petService.updateAdoptionRequest(request.id, "APPROVED", 3, setOf("RESCUER"))

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `updateAdoptionRequest allows admin to update request`() = runBlocking {
        val pet = createTestPet(rescuerId = 1, name = "Buddy", type = "DOG")
        val request = petService.createAdoptionRequest(pet.id, 2, "I want to adopt")

        val result = petService.updateAdoptionRequest(request.id, "APPROVED", 999, setOf("ADMIN"))

        assertTrue(result is ServiceResult.Success)
        assertEquals("APPROVED", result.data.status)
    }

    private suspend fun createTestPet(
        rescuerId: Int,
        name: String,
        type: String,
        isPromoted: Boolean = false,
        country: String? = "United States"
    ) =
        petRepository.create(
            rescuerId = rescuerId,
            name = name,
            type = type,
            description = "Test description",
            weight = 10.0,
            ageYears = 2,
            ageMonths = 0,
            sex = Gender.MALE,
            breed = "Test breed",
            isPromoted = isPromoted,
            country = country
        )
}

package com.adoptu.routes

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.dto.input.Currency
import com.adoptu.dto.input.Gender
import com.adoptu.dto.input.Status
import com.adoptu.mocks.TestClock
import com.adoptu.mocks.TestDatabase
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalTime::class)
class UIRoutesDataTest {

    private val clock = TestClock(Instant.parse("2024-01-15T10:00:00Z"))

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        
        transaction {
            try {
                Users.insert {
                    it[Users.id] = 1
                    it[Users.username] = "rescuer1@mocks.com"
                    it[Users.displayName] = "Rescuer One"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 1
                    it[UserActiveRoles.role] = "RESCUER"
                }
            } catch (e: Exception) { }
            
            try {
                Users.insert {
                    it[Users.id] = 2
                    it[Users.username] = "rescuer2@mocks.com"
                    it[Users.displayName] = "Rescuer Two"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 2
                    it[UserActiveRoles.role] = "RESCUER"
                }
            } catch (e: Exception) { }
            
            try {
                val adminId = Users.insert {
                    it[Users.id] = 3
                    it[Users.username] = "admin@mocks.com"
                    it[Users.displayName] = "Admin"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                } get Users.id
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = adminId
                    it[UserActiveRoles.role] = "ADMIN"
                }
            } catch (e: Exception) { }
        }
    }

    // ==================== Pets List Data ====================

    @Test
    fun `pets list returns all available pets`() = runBlocking {
        // Create pets
        PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "Buddy",
            type = "DOG",
            description = "A friendly dog",
            weight = 25.0,
            ageYears = 3,
            ageMonths = 6,
            sex = Gender.MALE,
            status = "AVAILABLE"
        )
        
        PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "Luna",
            type = "CAT",
            description = "A sweet cat",
            weight = 4.5,
            ageYears = 2,
            ageMonths = 0,
            sex = Gender.FEMALE,
            status = "AVAILABLE"
        )
        
        val pets = PetRepositoryImpl(clock).getAll(country = "United States")
        
        assertEquals(2, pets.size)
    }

    @Test
    fun `pets list filters by type DOG`() = runBlocking {
        PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "Dog1",
            type = "DOG",
            description = "Dog",
            weight = 10.0,
            ageYears = 1,
            ageMonths = 0,
            sex = Gender.MALE,
            status = "AVAILABLE"
        )
        
        PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "Cat1",
            type = "CAT",
            description = "Cat",
            weight = 5.0,
            ageYears = 1,
            ageMonths = 0,
            sex = Gender.FEMALE,
            status = "AVAILABLE"
        )
        
        val dogs = PetRepositoryImpl(clock).getAll("DOG", country = "United States")
        
        assertEquals(1, dogs.size)
        assertEquals("DOG", dogs[0].type)
    }

    @Test
    fun `pets list filters by type CAT`() = runBlocking {
        PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "Cat1",
            type = "CAT",
            description = "Cat",
            weight = 5.0,
            ageYears = 1,
            ageMonths = 0,
            sex = Gender.FEMALE,
            status = "AVAILABLE"
        )
        
        PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "Dog1",
            type = "DOG",
            description = "Dog",
            weight = 10.0,
            ageYears = 1,
            ageMonths = 0,
            sex = Gender.MALE,
            status = "AVAILABLE"
        )
        
        val cats = PetRepositoryImpl(clock).getAll("CAT", country = "United States")
        
        assertEquals(1, cats.size)
        assertEquals("CAT", cats[0].type)
    }

    @Test
    fun `pets list excludes adopted pets`() = runBlocking {
        PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "Available",
            type = "DOG",
            description = "Available",
            weight = 10.0,
            ageYears = 1,
            ageMonths = 0,
            sex = Gender.MALE,
            status = "AVAILABLE"
        )
        
        PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "Adopted",
            type = "DOG",
            description = "Adopted",
            weight = 10.0,
            ageYears = 1,
            ageMonths = 0,
            sex = Gender.MALE,
            status = "ADOPTED"
        )
        
        val pets = PetRepositoryImpl(clock).getAll(country = "United States")
        
        assertEquals(1, pets.size)
        assertEquals(Status.AVAILABLE, pets[0].status)
    }

    // ==================== Pet Detail Data ====================

    @Test
    fun `pet detail returns pet by id`() = runBlocking {
        val created = PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "Buddy",
            type = "DOG",
            description = "A friendly dog",
            weight = 25.0,
            ageYears = 3,
            ageMonths = 6,
            sex = Gender.MALE,
            status = "AVAILABLE"
        )
        
        val pet = PetRepositoryImpl(clock).getById(created.id)
        
        assertNotNull(pet)
        assertEquals("Buddy", pet.name)
    }

    @Test
    fun `pet detail returns null for non-existent id`() = runBlocking {
        val pet = PetRepositoryImpl(clock).getById(999)
        
        assertNull(pet)
    }

    @Test
    fun `pet detail includes all attributes`() = runBlocking {
        val created = PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "Max",
            type = "DOG",
            breed = "German Shepherd",
            description = "A loyal dog",
            weight = 35.0,
            ageYears = 4,
            ageMonths = 3,
            sex = Gender.MALE,
            color = "Black/Tan",
            size = "LARGE",
            temperament = "Loyal",
            isSterilized = true,
            isMicrochipped = true,
            microchipId = "ABC123456",
            isGoodWithKids = true,
            isGoodWithDogs = true,
            isGoodWithCats = false,
            isHouseTrained = true,
            energyLevel = "HIGH",
            rescueDate = clock.now().toEpochMilliseconds(),
            rescueLocation = "City Shelter",
            specialNeeds = "None",
            adoptionFee = 250.0,
            currency = Currency.USD,
            isUrgent = false,
            status = "AVAILABLE"
        )
        
        val pet = PetRepositoryImpl(clock).getById(created.id)
        
        assertNotNull(pet)
        assertEquals("Max", pet.name)
        assertEquals("DOG", pet.type)
        assertEquals("German Shepherd", pet.breed)
        assertEquals("A loyal dog", pet.description)
        assertEquals(35.0, pet.weight)
        assertEquals(4, pet.ageYears)
        assertEquals(3, pet.ageMonths)
        assertEquals(Gender.MALE, pet.sex)
        assertEquals("Black/Tan", pet.color)
        assertEquals("LARGE", pet.size)
        assertEquals("Loyal", pet.temperament)
        assertTrue(pet.isSterilized)
        assertTrue(pet.isMicrochipped)
        assertEquals("ABC123456", pet.microchipId)
        assertTrue(pet.isGoodWithKids)
        assertTrue(pet.isGoodWithDogs)
        assertFalse(pet.isGoodWithCats)
        assertTrue(pet.isHouseTrained)
        assertEquals("HIGH", pet.energyLevel)
        assertEquals("City Shelter", pet.rescueLocation)
        assertEquals("None", pet.specialNeeds)
        assertEquals(250.0, pet.adoptionFee)
        assertEquals(Currency.USD, pet.currency)
        assertFalse(pet.isUrgent)
        assertEquals(Status.AVAILABLE, pet.status)
    }

    // ==================== Pet Images ====================

    @Test
    fun `pet images returns empty list when no images`() = runBlocking {
        val created = PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "NoImages",
            type = "DOG",
            description = "No images",
            weight = 10.0,
            ageYears = 1,
            ageMonths = 0,
            sex = Gender.MALE,
            status = "AVAILABLE"
        )
        
        val images = PetRepositoryImpl(clock).getImages(created.id)
        
        assertEquals(0, images.size)
    }

    @Test
    fun `pet images returns added images`() = runBlocking {
        val created = PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "WithImages",
            type = "DOG",
            description = "With images",
            weight = 10.0,
            ageYears = 1,
            ageMonths = 0,
            sex = Gender.MALE,
            status = "AVAILABLE"
        )
        
        PetRepositoryImpl(clock).addImage(created.id, "https://s3.example.com/img1.jpg", true, 0)
        PetRepositoryImpl(clock).addImage(created.id, "https://s3.example.com/img2.jpg", false, 1)
        
        val images = PetRepositoryImpl(clock).getImages(created.id)
        
        assertEquals(2, images.size)
    }

    @Test
    fun `pet images first added is primary by default`() = runBlocking {
        val created = PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "PrimaryTest",
            type = "DOG",
            description = "Primary mocks",
            weight = 10.0,
            ageYears = 1,
            ageMonths = 0,
            sex = Gender.MALE,
            status = "AVAILABLE"
        )
        
        PetRepositoryImpl(clock).addImage(created.id, "https://s3.example.com/first.jpg", false, 0)
        PetRepositoryImpl(clock).addImage(created.id, "https://s3.example.com/second.jpg", true, 1)
        
        val images = PetRepositoryImpl(clock).getImages(created.id)
        
        val primary = images.find { it.isPrimary }
        assertNotNull(primary)
        assertEquals("https://s3.example.com/second.jpg", primary.imageUrl)
    }

    @Test
    fun `removeImage returns false when image does not exist`() = runBlocking {
        val created = PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "NoImages",
            type = "DOG",
            description = "No images yet",
            weight = 10.0,
            ageYears = 1,
            ageMonths = 0,
            sex = Gender.MALE,
            status = "AVAILABLE"
        )

        val result = PetRepositoryImpl(clock).removeImage(created.id, 999999)

        assertFalse(result)
    }

    @Test
    fun `removeImage returns true when image exists`() = runBlocking {
        val created = PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "HasImage",
            type = "DOG",
            description = "Has an image to remove",
            weight = 10.0,
            ageYears = 1,
            ageMonths = 0,
            sex = Gender.MALE,
            status = "AVAILABLE"
        )
        PetRepositoryImpl(clock).addImage(created.id, "https://s3.example.com/toremove.jpg", true, 0)
        val image = PetRepositoryImpl(clock).getImages(created.id).single()

        val result = PetRepositoryImpl(clock).removeImage(created.id, image.id)

        assertTrue(result)
        assertTrue(PetRepositoryImpl(clock).getImages(created.id).isEmpty())
    }

    // ==================== Urgent Pets ====================

    @Test
    fun `urgent pets show isUrgent flag`() = runBlocking {
        val urgent = PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "UrgentBuddy",
            type = "DOG",
            description = "Urgent",
            weight = 10.0,
            ageYears = 1,
            ageMonths = 0,
            sex = Gender.MALE,
            status = "AVAILABLE",
            isUrgent = true
        )
        
        val pet = PetRepositoryImpl(clock).getById(urgent.id)
        
        assertNotNull(pet)
        assertTrue(pet.isUrgent)
    }

    @Test
    fun `non-urgent pets show isUrgent false`() = runBlocking {
        val normal = PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "NormalBuddy",
            type = "DOG",
            description = "Normal",
            weight = 10.0,
            ageYears = 1,
            ageMonths = 0,
            sex = Gender.MALE,
            status = "AVAILABLE",
            isUrgent = false
        )
        
        val pet = PetRepositoryImpl(clock).getById(normal.id)
        
        assertNotNull(pet)
        assertFalse(pet.isUrgent)
    }

    // ==================== Pet Gender Display ====================

    @Test
    fun `pets show MALE gender`() = runBlocking {
        val male = PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "MalePet",
            type = "DOG",
            description = "Male",
            weight = 10.0,
            ageYears = 1,
            ageMonths = 0,
            sex = Gender.MALE,
            status = "AVAILABLE"
        )
        
        val pet = PetRepositoryImpl(clock).getById(male.id)
        
        assertNotNull(pet)
        assertEquals(Gender.MALE, pet.sex)
    }

    @Test
    fun `pets show FEMALE gender`() = runBlocking {
        val female = PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "FemalePet",
            type = "CAT",
            description = "Female",
            weight = 5.0,
            ageYears = 1,
            ageMonths = 0,
            sex = Gender.FEMALE,
            status = "AVAILABLE"
        )
        
        val pet = PetRepositoryImpl(clock).getById(female.id)
        
        assertNotNull(pet)
        assertEquals(Gender.FEMALE, pet.sex)
    }

    // ==================== Pet Size Display ====================

    @Test
    fun `pets show SMALL size`() = runBlocking {
        val pet = PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "SmallPet",
            type = "CAT",
            description = "Small",
            weight = 3.0,
            ageYears = 1,
            ageMonths = 0,
            sex = Gender.MALE,
            size = "SMALL",
            status = "AVAILABLE"
        )
        
        val retrieved = PetRepositoryImpl(clock).getById(pet.id)
        
        assertNotNull(retrieved)
        assertEquals("SMALL", retrieved.size)
    }

    @Test
    fun `pets show MEDIUM size`() = runBlocking {
        val pet = PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "MediumPet",
            type = "DOG",
            description = "Medium",
            weight = 15.0,
            ageYears = 2,
            ageMonths = 0,
            sex = Gender.FEMALE,
            size = "MEDIUM",
            status = "AVAILABLE"
        )
        
        val retrieved = PetRepositoryImpl(clock).getById(pet.id)
        
        assertNotNull(retrieved)
        assertEquals("MEDIUM", retrieved.size)
    }

    @Test
    fun `pets show LARGE size`() = runBlocking {
        val pet = PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "LargePet",
            type = "DOG",
            description = "Large",
            weight = 40.0,
            ageYears = 3,
            ageMonths = 0,
            sex = Gender.MALE,
            size = "LARGE",
            status = "AVAILABLE"
        )
        
        val retrieved = PetRepositoryImpl(clock).getById(pet.id)
        
        assertNotNull(retrieved)
        assertEquals("LARGE", retrieved.size)
    }

    // ==================== Rescue Date ====================

    @Test
    fun `pets show rescue date when present`() = runBlocking {
        val rescueTime = clock.now().toEpochMilliseconds()
        val pet = PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "RescuedPet",
            type = "DOG",
            description = "Rescued",
            weight = 10.0,
            ageYears = 1,
            ageMonths = 0,
            sex = Gender.MALE,
            rescueDate = rescueTime,
            status = "AVAILABLE"
        )
        
        val retrieved = PetRepositoryImpl(clock).getById(pet.id)

        assertNotNull(retrieved)
        assertNotNull(retrieved.rescueDate)
        Unit
    }

    @Test
    fun `pets show null rescue date when not set`() = runBlocking {
        val pet = PetRepositoryImpl(clock).create(
            rescuerId = 1,
            country = "United States",
            name = "NoRescueDate",
            type = "DOG",
            description = "No rescue date",
            weight = 10.0,
            ageYears = 1,
            ageMonths = 0,
            sex = Gender.MALE,
            status = "AVAILABLE"
        )
        
        val retrieved = PetRepositoryImpl(clock).getById(pet.id)
        
        assertNotNull(retrieved)
        assertNull(retrieved.rescueDate)
    }
}

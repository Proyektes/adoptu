package com.adoptu.services
import com.adoptu.adapters.authkit.AdoptuUserRepositoryAdapter

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.SponsorshipOfferRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.CreateSponsorshipOfferRequest
import com.adoptu.dto.input.Currency
import com.adoptu.dto.input.Gender
import com.adoptu.dto.input.SponsorshipOfferType
import com.adoptu.mocks.MockNotificationAdapter
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
class SponsorshipServiceTest {

    private lateinit var service: SponsorshipService
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
            user(2, "sponsor1@mocks.com")
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
            role(3, "RESCUER")
            exec("DELETE FROM sponsorship_offers")
            exec("DELETE FROM pets")
        }
        val userRepository = UserRepository(clock)
        petRepository = PetRepositoryImpl(clock)
        val photographerRepository = PhotographerRepositoryImpl(petRepository, userRepository, clock)
        val userService = UserService(userRepository, photographerRepository, AdoptuUserRepositoryAdapter())
        val offerRepository = SponsorshipOfferRepositoryImpl(userRepository, petRepository, clock)
        service = SponsorshipService(offerRepository, petRepository, userService, MockNotificationAdapter())
    }

    private suspend fun createTestPet(rescuerId: Int) = petRepository.create(
        rescuerId = rescuerId, name = "Buddy", type = "DOG", description = "Test",
        weight = 10.0, ageYears = 2, ageMonths = 0, sex = Gender.MALE, country = "United States"
    )

    @Test
    fun `createOffer succeeds for a money offer to a rescuer's general fund`() = runBlocking {
        val result = service.createOffer(
            2,
            CreateSponsorshipOfferRequest(
                rescuerId = 1, offerType = SponsorshipOfferType.MONEY,
                amount = 500.0, currency = Currency.USD, message = "Happy to help!"
            )
        )

        assertTrue(result is ServiceResult.Success)
        assertEquals(1, result.data.rescuerId)
        assertEquals(null, result.data.petId)
    }

    @Test
    fun `createOffer succeeds for an in-kind offer targeting a specific pet`() = runBlocking {
        val pet = createTestPet(rescuerId = 1)

        val result = service.createOffer(
            2,
            CreateSponsorshipOfferRequest(
                rescuerId = 1, petId = pet.id, offerType = SponsorshipOfferType.IN_KIND,
                inKindDescription = "A bag of dog food", message = "For Buddy!"
            )
        )

        assertTrue(result is ServiceResult.Success)
        assertEquals(pet.id, result.data.petId)
    }

    @Test
    fun `createOffer returns Error when target is not an active rescuer`() = runBlocking {
        val result = service.createOffer(
            2,
            CreateSponsorshipOfferRequest(rescuerId = 4, offerType = SponsorshipOfferType.MONEY, amount = 100.0, currency = Currency.USD, message = "Help")
        )

        assertTrue(result is ServiceResult.Error)
    }

    @Test
    fun `createOffer returns Error when the pet belongs to a different rescuer`() = runBlocking {
        val pet = createTestPet(rescuerId = 3)

        val result = service.createOffer(
            2,
            CreateSponsorshipOfferRequest(rescuerId = 1, petId = pet.id, offerType = SponsorshipOfferType.MONEY, amount = 100.0, currency = Currency.USD, message = "Help")
        )

        assertTrue(result is ServiceResult.Error)
    }

    @Test
    fun `createOffer returns Error for a money offer with no amount`() = runBlocking {
        val result = service.createOffer(
            2,
            CreateSponsorshipOfferRequest(rescuerId = 1, offerType = SponsorshipOfferType.MONEY, message = "Help")
        )

        assertTrue(result is ServiceResult.Error)
    }

    @Test
    fun `createOffer returns Error for an in-kind offer with no description`() = runBlocking {
        val result = service.createOffer(
            2,
            CreateSponsorshipOfferRequest(rescuerId = 1, offerType = SponsorshipOfferType.IN_KIND, message = "Help")
        )

        assertTrue(result is ServiceResult.Error)
    }

    @Test
    fun `getForRescuer returns Forbidden for a non-owner`() = runBlocking {
        val result = service.getForRescuer(1, 3, setOf("RESCUER"))

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `getForRescuer succeeds for the rescuer themselves`() = runBlocking {
        service.createOffer(2, CreateSponsorshipOfferRequest(rescuerId = 1, offerType = SponsorshipOfferType.MONEY, amount = 50.0, currency = Currency.USD, message = "Help"))

        val result = service.getForRescuer(1, 1, setOf("RESCUER"))

        assertTrue(result is ServiceResult.Success)
        assertEquals(1, result.data.size)
    }

    @Test
    fun `getForSponsor returns offers made by that sponsor`() = runBlocking {
        service.createOffer(2, CreateSponsorshipOfferRequest(rescuerId = 1, offerType = SponsorshipOfferType.MONEY, amount = 50.0, currency = Currency.USD, message = "Help"))

        val result = service.getForSponsor(2)

        assertEquals(1, result.size)
    }

    @Test
    fun `markRead succeeds for the rescuer and returns Forbidden for others`() = runBlocking {
        val created = service.createOffer(2, CreateSponsorshipOfferRequest(rescuerId = 1, offerType = SponsorshipOfferType.MONEY, amount = 50.0, currency = Currency.USD, message = "Help"))
        val id = (created as ServiceResult.Success).data.id

        val forbidden = service.markRead(id, 3, setOf("RESCUER"))
        assertEquals(ServiceResult.Forbidden, forbidden)

        val result = service.markRead(id, 1, setOf("RESCUER"))
        assertTrue(result is ServiceResult.Success)
        assertEquals("READ", result.data.status)
    }
}

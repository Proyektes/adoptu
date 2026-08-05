package com.adoptu.adapters.dynamodb

import com.adoptu.dto.input.Currency
import com.adoptu.dto.input.Gender
import com.adoptu.dto.input.PetDto
import com.adoptu.dto.input.PhotographerDto
import com.adoptu.dto.input.ShelterDto
import com.adoptu.dto.input.Status
import com.adoptu.dto.input.SterilizationLocationDto
import com.adoptu.dto.input.TemporalHomeDto
import com.adoptu.dto.input.UserDto
import com.adoptu.dto.input.UserRole
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest
import software.amazon.awssdk.services.dynamodb.model.DeleteItemResponse
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse
import software.amazon.awssdk.services.dynamodb.model.ScanRequest
import software.amazon.awssdk.services.dynamodb.model.ScanResponse
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamoDBAdapterTest {

    private lateinit var client: DynamoDbAsyncClient
    private lateinit var adapter: DynamoDBAdapter

    @BeforeEach
    fun setup() {
        client = mockk()
        adapter = DynamoDBAdapter(client)
    }

    // ---------------------------------------------------------------
    // Users
    // ---------------------------------------------------------------

    @Nested
    inner class Users {

        private val sampleUser = UserDto(
            id = 1,
            username = "jdoe",
            email = "jdoe@example.com",
            displayName = "John Doe",
            language = "en",
            isEmailVerified = true,
            activeRoles = setOf(UserRole.RESCUER, UserRole.ADOPTER),
            lastAcceptedPrivacyPolicy = 1000L,
            lastAcceptedTermsAndConditions = 2000L,
            isBanned = false,
            banReason = null
        )

        @Test
        fun `createUser succeeds and returns user`() {
            every { client.putItem(any<PutItemRequest>()) } returns CompletableFuture.completedFuture(PutItemResponse.builder().build())

            val result = runBlocking { adapter.createUser(sampleUser) }

            assertEquals(sampleUser, result)
            verify { client.putItem(any<PutItemRequest>()) }
        }

        @Test
        fun `createUser returns null on exception`() {
            every { client.putItem(any<PutItemRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.createUser(sampleUser) }

            assertNull(result)
        }

        @Test
        fun `getUserById returns mapped dto when item present`() {
            val item = mapOf(
                "id" to AttributeValue.builder().n("1").build(),
                "username" to AttributeValue.builder().s("jdoe").build(),
                "email" to AttributeValue.builder().s("jdoe@example.com").build(),
                "display_name" to AttributeValue.builder().s("John Doe").build(),
                "language" to AttributeValue.builder().s("en").build(),
                "is_email_verified" to AttributeValue.builder().bool(true).build(),
                "active_roles" to AttributeValue.builder().ss(listOf("RESCUER", "ADOPTER")).build(),
                "last_accepted_privacy_policy" to AttributeValue.builder().n("1000").build(),
                "last_accepted_terms_and_conditions" to AttributeValue.builder().n("2000").build(),
                "is_banned" to AttributeValue.builder().bool(false).build(),
                "photographer_fee" to AttributeValue.builder().n("50.0").build(),
                "photographer_currency" to AttributeValue.builder().s("USD").build(),
                "photographer_country" to AttributeValue.builder().s("US").build(),
                "photographer_state" to AttributeValue.builder().s("CA").build()
            )
            every { client.getItem(any<GetItemRequest>()) } returns CompletableFuture.completedFuture(
                GetItemResponse.builder().item(item).build()
            )

            val result = runBlocking { adapter.getUserById(1) }

            assertEquals(sampleUser, result)
        }

        @Test
        fun `getUserById returns null when not found`() {
            every { client.getItem(any<GetItemRequest>()) } returns CompletableFuture.completedFuture(
                GetItemResponse.builder().build()
            )

            val result = runBlocking { adapter.getUserById(999) }

            assertNull(result)
        }

        @Test
        fun `getUserById returns null on exception`() {
            every { client.getItem(any<GetItemRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.getUserById(1) }

            assertNull(result)
        }

        @Test
        fun `getUserByEmail returns first matching item`() {
            val item = mapOf(
                "id" to AttributeValue.builder().n("1").build(),
                "username" to AttributeValue.builder().s("jdoe").build(),
                "display_name" to AttributeValue.builder().s("John Doe").build()
            )
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(listOf(item)).build()
            )

            val result = runBlocking { adapter.getUserByEmail("jdoe@example.com") }

            assertEquals(1, result?.id)
        }

        @Test
        fun `getUserByEmail returns null when no items`() {
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(emptyList()).build()
            )

            val result = runBlocking { adapter.getUserByEmail("missing@example.com") }

            assertNull(result)
        }

        @Test
        fun `getUserByEmail returns null on exception`() {
            every { client.scan(any<ScanRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.getUserByEmail("jdoe@example.com") }

            assertNull(result)
        }

        @Test
        fun `updateUser delegates to createUser`() {
            every { client.putItem(any<PutItemRequest>()) } returns CompletableFuture.completedFuture(PutItemResponse.builder().build())

            val result = runBlocking { adapter.updateUser(sampleUser) }

            assertEquals(sampleUser, result)
            verify { client.putItem(any<PutItemRequest>()) }
        }

        @Test
        fun `deleteUser returns true on success`() {
            every { client.deleteItem(any<DeleteItemRequest>()) } returns CompletableFuture.completedFuture(DeleteItemResponse.builder().build())

            val result = runBlocking { adapter.deleteUser(1) }

            assertTrue(result)
        }

        @Test
        fun `deleteUser returns false on exception`() {
            every { client.deleteItem(any<DeleteItemRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.deleteUser(1) }

            assertFalse(result)
        }

        @Test
        fun `getAllUsers returns mapped list`() {
            val item = mapOf(
                "id" to AttributeValue.builder().n("1").build(),
                "username" to AttributeValue.builder().s("jdoe").build(),
                "display_name" to AttributeValue.builder().s("John Doe").build()
            )
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(listOf(item)).build()
            )

            val result = runBlocking { adapter.getAllUsers() }

            assertEquals(1, result.size)
            assertEquals("jdoe", result[0].username)
        }

        @Test
        fun `getAllUsers returns empty list on exception`() {
            every { client.scan(any<ScanRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.getAllUsers() }

            assertTrue(result.isEmpty())
        }

        @Test
        fun `getUsersByRole filters by role`() {
            val rescuerItem = mapOf(
                "id" to AttributeValue.builder().n("1").build(),
                "username" to AttributeValue.builder().s("rescuer1").build(),
                "display_name" to AttributeValue.builder().s("Rescuer One").build(),
                "active_roles" to AttributeValue.builder().ss(listOf("RESCUER")).build()
            )
            val adopterItem = mapOf(
                "id" to AttributeValue.builder().n("2").build(),
                "username" to AttributeValue.builder().s("adopter1").build(),
                "display_name" to AttributeValue.builder().s("Adopter One").build(),
                "active_roles" to AttributeValue.builder().ss(listOf("ADOPTER")).build()
            )
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(listOf(rescuerItem, adopterItem)).build()
            )

            val result = runBlocking { adapter.getUsersByRole(UserRole.RESCUER) }

            assertEquals(1, result.size)
            assertEquals("rescuer1", result[0].username)
        }

        @Test
        fun `toUserDto falls back to defaults for missing or invalid fields`() {
            val item = mapOf(
                "active_roles" to AttributeValue.builder().ss(listOf("RESCUER", "NOT_A_ROLE")).build()
            )
            every { client.getItem(any<GetItemRequest>()) } returns CompletableFuture.completedFuture(
                GetItemResponse.builder().item(item).build()
            )

            val result = runBlocking { adapter.getUserById(1) }

            assertNotNull(result)
            assertEquals(0, result.id)
            assertEquals("", result.username)
            assertNull(result.email)
            assertEquals("", result.displayName)
            assertEquals("en", result.language)
            assertFalse(result.isEmailVerified)
            // invalid role string is filtered out via mapNotNull/catch
            assertEquals(setOf(UserRole.RESCUER), result.activeRoles)
            assertNull(result.lastAcceptedPrivacyPolicy)
            assertFalse(result.isBanned)
        }
    }

    // ---------------------------------------------------------------
    // Pets
    // ---------------------------------------------------------------

    @Nested
    inner class Pets {

        private val samplePet = PetDto(
            id = 1,
            rescuerId = 2,
            name = "Rex",
            type = "DOG",
            breed = "Labrador",
            description = "Friendly dog",
            weight = 25.5,
            ageYears = 3,
            ageMonths = 2,
            sex = Gender.MALE,
            status = Status.AVAILABLE,
            color = "Brown",
            size = "Large",
            temperament = "Calm",
            isSterilized = true,
            isMicrochipped = true,
            microchipId = "ABC123",
            vaccinations = "Rabies",
            isGoodWithKids = true,
            isGoodWithDogs = true,
            isGoodWithCats = false,
            isHouseTrained = true,
            energyLevel = "High",
            rescueDate = 5000L,
            rescueLocation = "Park",
            specialNeeds = "None",
            adoptionFee = 100.0,
            currency = Currency.USD,
            isUrgent = false,
            isPromoted = true,
            createdAt = 6000L
        )

        @Test
        fun `createPet succeeds and returns pet`() {
            every { client.putItem(any<PutItemRequest>()) } returns CompletableFuture.completedFuture(PutItemResponse.builder().build())

            val result = runBlocking { adapter.createPet(samplePet) }

            assertEquals(samplePet, result)
        }

        @Test
        fun `createPet returns null on exception`() {
            every { client.putItem(any<PutItemRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.createPet(samplePet) }

            assertNull(result)
        }

        @Test
        fun `getPetById returns mapped dto when item present`() {
            val item = mapOf(
                "id" to AttributeValue.builder().n("1").build(),
                "rescuer_id" to AttributeValue.builder().n("2").build(),
                "name" to AttributeValue.builder().s("Rex").build(),
                "type" to AttributeValue.builder().s("DOG").build(),
                "breed" to AttributeValue.builder().s("Labrador").build(),
                "description" to AttributeValue.builder().s("Friendly dog").build(),
                "weight" to AttributeValue.builder().n("25.5").build(),
                "age_years" to AttributeValue.builder().n("3").build(),
                "age_months" to AttributeValue.builder().n("2").build(),
                "sex" to AttributeValue.builder().s("MALE").build(),
                "status" to AttributeValue.builder().s("AVAILABLE").build(),
                "color" to AttributeValue.builder().s("Brown").build(),
                "size" to AttributeValue.builder().s("Large").build(),
                "temperament" to AttributeValue.builder().s("Calm").build(),
                "is_sterilized" to AttributeValue.builder().bool(true).build(),
                "is_microchipped" to AttributeValue.builder().bool(true).build(),
                "microchip_id" to AttributeValue.builder().s("ABC123").build(),
                "vaccinations" to AttributeValue.builder().s("Rabies").build(),
                "is_good_with_kids" to AttributeValue.builder().bool(true).build(),
                "is_good_with_dogs" to AttributeValue.builder().bool(true).build(),
                "is_good_with_cats" to AttributeValue.builder().bool(false).build(),
                "is_house_trained" to AttributeValue.builder().bool(true).build(),
                "energy_level" to AttributeValue.builder().s("High").build(),
                "rescue_date" to AttributeValue.builder().n("5000").build(),
                "rescue_location" to AttributeValue.builder().s("Park").build(),
                "special_needs" to AttributeValue.builder().s("None").build(),
                "adoption_fee" to AttributeValue.builder().n("100.0").build(),
                "currency" to AttributeValue.builder().s("USD").build(),
                "is_urgent" to AttributeValue.builder().bool(false).build(),
                "is_promoted" to AttributeValue.builder().bool(true).build(),
                "created_at" to AttributeValue.builder().n("6000").build()
            )
            every { client.getItem(any<GetItemRequest>()) } returns CompletableFuture.completedFuture(
                GetItemResponse.builder().item(item).build()
            )

            val result = runBlocking { adapter.getPetById(1) }

            assertEquals(samplePet, result)
        }

        @Test
        fun `getPetById returns null when not found`() {
            every { client.getItem(any<GetItemRequest>()) } returns CompletableFuture.completedFuture(
                GetItemResponse.builder().build()
            )

            val result = runBlocking { adapter.getPetById(999) }

            assertNull(result)
        }

        @Test
        fun `getPetById returns null on exception`() {
            every { client.getItem(any<GetItemRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.getPetById(1) }

            assertNull(result)
        }

        @Test
        fun `updatePet delegates to createPet`() {
            every { client.putItem(any<PutItemRequest>()) } returns CompletableFuture.completedFuture(PutItemResponse.builder().build())

            val result = runBlocking { adapter.updatePet(samplePet) }

            assertEquals(samplePet, result)
            verify { client.putItem(any<PutItemRequest>()) }
        }

        @Test
        fun `deletePet returns true on success`() {
            every { client.deleteItem(any<DeleteItemRequest>()) } returns CompletableFuture.completedFuture(DeleteItemResponse.builder().build())

            val result = runBlocking { adapter.deletePet(1) }

            assertTrue(result)
        }

        @Test
        fun `deletePet returns false on exception`() {
            every { client.deleteItem(any<DeleteItemRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.deletePet(1) }

            assertFalse(result)
        }

        @Test
        fun `getAllPets returns mapped list`() {
            val item = mapOf(
                "id" to AttributeValue.builder().n("1").build(),
                "rescuer_id" to AttributeValue.builder().n("2").build(),
                "name" to AttributeValue.builder().s("Rex").build(),
                "description" to AttributeValue.builder().s("Friendly dog").build(),
                "created_at" to AttributeValue.builder().n("6000").build()
            )
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(listOf(item)).build()
            )

            val result = runBlocking { adapter.getAllPets() }

            assertEquals(1, result.size)
            assertEquals("Rex", result[0].name)
        }

        @Test
        fun `getAllPets returns empty list on exception`() {
            every { client.scan(any<ScanRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.getAllPets() }

            assertTrue(result.isEmpty())
        }

        @Test
        fun `getPetsByRescuer returns mapped list`() {
            val item = mapOf(
                "id" to AttributeValue.builder().n("1").build(),
                "rescuer_id" to AttributeValue.builder().n("2").build(),
                "name" to AttributeValue.builder().s("Rex").build(),
                "description" to AttributeValue.builder().s("Friendly dog").build(),
                "created_at" to AttributeValue.builder().n("6000").build()
            )
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(listOf(item)).build()
            )

            val result = runBlocking { adapter.getPetsByRescuer(2) }

            assertEquals(1, result.size)
            assertEquals(2, result[0].rescuerId)
        }

        @Test
        fun `getPetsByRescuer returns empty list on exception`() {
            every { client.scan(any<ScanRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.getPetsByRescuer(2) }

            assertTrue(result.isEmpty())
        }

        @Test
        fun `toPetDto falls back to defaults and parses enums with fallback on invalid values`() {
            val item = mapOf(
                "sex" to AttributeValue.builder().s("NOT_A_GENDER").build(),
                "status" to AttributeValue.builder().s("NOT_A_STATUS").build(),
                "currency" to AttributeValue.builder().s("NOT_A_CURRENCY").build()
            )
            every { client.getItem(any<GetItemRequest>()) } returns CompletableFuture.completedFuture(
                GetItemResponse.builder().item(item).build()
            )

            val result = runBlocking { adapter.getPetById(1) }

            assertNotNull(result)
            assertEquals(0, result.id)
            assertEquals("", result.name)
            assertEquals("DOG", result.type)
            assertEquals("", result.description)
            assertEquals(0.0, result.weight)
            // sex falls back: only "FEMALE" maps to FEMALE explicitly, anything else (including
            // invalid strings) maps to MALE
            assertEquals(Gender.MALE, result.sex)
            // status/currency use valueOf in try/catch with fallback to defaults
            assertEquals(Status.AVAILABLE, result.status)
            assertEquals(Currency.USD, result.currency)
            assertTrue(result.isGoodWithKids)
            assertTrue(result.isGoodWithDogs)
            assertTrue(result.isGoodWithCats)
            assertFalse(result.isHouseTrained)
        }

        @Test
        fun `toPetDto maps FEMALE sex explicitly`() {
            val item = mapOf(
                "sex" to AttributeValue.builder().s("FEMALE").build(),
                "status" to AttributeValue.builder().s("ADOPTED").build(),
                "currency" to AttributeValue.builder().s("EUR").build()
            )
            every { client.getItem(any<GetItemRequest>()) } returns CompletableFuture.completedFuture(
                GetItemResponse.builder().item(item).build()
            )

            val result = runBlocking { adapter.getPetById(1) }

            assertNotNull(result)
            assertEquals(Gender.FEMALE, result.sex)
            assertEquals(Status.ADOPTED, result.status)
            assertEquals(Currency.EUR, result.currency)
        }
    }

    // ---------------------------------------------------------------
    // Photographers
    // ---------------------------------------------------------------

    @Nested
    inner class Photographers {

        private val samplePhotographer = PhotographerDto(
            userId = 1,
            displayName = "Jane Photo",
            username = "janephoto",
            photographerFee = 75.0,
            photographerCurrency = "USD",
            country = "US",
            state = "NY"
        )

        @Test
        fun `createPhotographer succeeds and returns photographer`() {
            every { client.putItem(any<PutItemRequest>()) } returns CompletableFuture.completedFuture(PutItemResponse.builder().build())

            val result = runBlocking { adapter.createPhotographer(samplePhotographer) }

            assertEquals(samplePhotographer, result)
        }

        @Test
        fun `createPhotographer returns null on exception`() {
            every { client.putItem(any<PutItemRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.createPhotographer(samplePhotographer) }

            assertNull(result)
        }

        @Test
        fun `getPhotographerByUserId returns mapped dto when present`() {
            val item = mapOf(
                "user_id" to AttributeValue.builder().n("1").build(),
                "display_name" to AttributeValue.builder().s("Jane Photo").build(),
                "username" to AttributeValue.builder().s("janephoto").build(),
                "photographer_fee" to AttributeValue.builder().n("75.0").build(),
                "photographer_currency" to AttributeValue.builder().s("USD").build(),
                "country" to AttributeValue.builder().s("US").build(),
                "state" to AttributeValue.builder().s("NY").build()
            )
            every { client.getItem(any<GetItemRequest>()) } returns CompletableFuture.completedFuture(
                GetItemResponse.builder().item(item).build()
            )

            val result = runBlocking { adapter.getPhotographerByUserId(1) }

            assertEquals(samplePhotographer, result)
        }

        @Test
        fun `getPhotographerByUserId returns null when not found`() {
            every { client.getItem(any<GetItemRequest>()) } returns CompletableFuture.completedFuture(
                GetItemResponse.builder().build()
            )

            val result = runBlocking { adapter.getPhotographerByUserId(999) }

            assertNull(result)
        }

        @Test
        fun `getPhotographerByUserId returns null on exception`() {
            every { client.getItem(any<GetItemRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.getPhotographerByUserId(1) }

            assertNull(result)
        }

        @Test
        fun `updatePhotographer delegates to createPhotographer`() {
            every { client.putItem(any<PutItemRequest>()) } returns CompletableFuture.completedFuture(PutItemResponse.builder().build())

            val result = runBlocking { adapter.updatePhotographer(samplePhotographer) }

            assertEquals(samplePhotographer, result)
            verify { client.putItem(any<PutItemRequest>()) }
        }

        @Test
        fun `deletePhotographer returns true on success`() {
            every { client.deleteItem(any<DeleteItemRequest>()) } returns CompletableFuture.completedFuture(DeleteItemResponse.builder().build())

            val result = runBlocking { adapter.deletePhotographer(1) }

            assertTrue(result)
        }

        @Test
        fun `deletePhotographer returns false on exception`() {
            every { client.deleteItem(any<DeleteItemRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.deletePhotographer(1) }

            assertFalse(result)
        }

        @Test
        fun `getAllPhotographers returns mapped list`() {
            val item = mapOf(
                "user_id" to AttributeValue.builder().n("1").build(),
                "display_name" to AttributeValue.builder().s("Jane Photo").build()
            )
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(listOf(item)).build()
            )

            val result = runBlocking { adapter.getAllPhotographers() }

            assertEquals(1, result.size)
            assertEquals("Jane Photo", result[0].displayName)
        }

        @Test
        fun `getAllPhotographers returns empty list on exception`() {
            every { client.scan(any<ScanRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.getAllPhotographers() }

            assertTrue(result.isEmpty())
        }

        @Test
        fun `toPhotographerDto falls back to defaults for missing fields`() {
            val item = emptyMap<String, AttributeValue>()
            every { client.getItem(any<GetItemRequest>()) } returns CompletableFuture.completedFuture(
                GetItemResponse.builder().item(item).build()
            )

            val result = runBlocking { adapter.getPhotographerByUserId(1) }

            assertNotNull(result)
            assertEquals(0, result.userId)
            assertEquals("", result.displayName)
            assertNull(result.username)
            assertNull(result.photographerFee)
        }
    }

    // ---------------------------------------------------------------
    // Shelters
    // ---------------------------------------------------------------

    @Nested
    inner class Shelters {

        private val sampleShelter = ShelterDto(
            id = 1,
            userId = 2,
            name = "Happy Paws",
            country = "US",
            state = "CA",
            city = "LA",
            neighborhood = "Downtown",
            address = "123 Main St",
            zip = "90001",
            phone = "555-1234",
            email = "shelter@example.com",
            website = "https://shelter.example.com",
            fiscalId = "FID123",
            bankName = "Bank of Pets",
            accountHolderName = "Happy Paws Inc",
            accountNumber = "ACC123",
            iban = "IBAN123",
            swiftBic = "SWIFT123",
            currency = "USD",
            description = "A loving shelter",
            createdAt = 1000L,
            updatedAt = 2000L
        )

        @Test
        fun `createShelter succeeds and returns shelter`() {
            every { client.putItem(any<PutItemRequest>()) } returns CompletableFuture.completedFuture(PutItemResponse.builder().build())

            val result = runBlocking { adapter.createShelter(sampleShelter) }

            assertEquals(sampleShelter, result)
        }

        @Test
        fun `createShelter returns null on exception`() {
            every { client.putItem(any<PutItemRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.createShelter(sampleShelter) }

            assertNull(result)
        }

        @Test
        fun `getShelterByUserId returns mapped dto when present`() {
            val item = mapOf(
                "id" to AttributeValue.builder().n("1").build(),
                "user_id" to AttributeValue.builder().n("2").build(),
                "name" to AttributeValue.builder().s("Happy Paws").build(),
                "country" to AttributeValue.builder().s("US").build(),
                "city" to AttributeValue.builder().s("LA").build(),
                "address" to AttributeValue.builder().s("123 Main St").build(),
                "currency" to AttributeValue.builder().s("USD").build(),
                "created_at" to AttributeValue.builder().n("1000").build(),
                "updated_at" to AttributeValue.builder().n("2000").build()
            )
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(listOf(item)).build()
            )

            val result = runBlocking { adapter.getShelterByUserId(2) }

            assertNotNull(result)
            assertEquals("Happy Paws", result.name)
        }

        @Test
        fun `getShelterByUserId returns null when no items`() {
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(emptyList()).build()
            )

            val result = runBlocking { adapter.getShelterByUserId(999) }

            assertNull(result)
        }

        @Test
        fun `getShelterByUserId returns null on exception`() {
            every { client.scan(any<ScanRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.getShelterByUserId(2) }

            assertNull(result)
        }

        @Test
        fun `updateShelter delegates to createShelter`() {
            every { client.putItem(any<PutItemRequest>()) } returns CompletableFuture.completedFuture(PutItemResponse.builder().build())

            val result = runBlocking { adapter.updateShelter(sampleShelter) }

            assertEquals(sampleShelter, result)
            verify { client.putItem(any<PutItemRequest>()) }
        }

        @Test
        fun `deleteShelter returns true when shelter found and delete succeeds`() {
            val item = mapOf(
                "id" to AttributeValue.builder().n("1").build(),
                "user_id" to AttributeValue.builder().n("2").build(),
                "name" to AttributeValue.builder().s("Happy Paws").build(),
                "country" to AttributeValue.builder().s("US").build(),
                "city" to AttributeValue.builder().s("LA").build(),
                "address" to AttributeValue.builder().s("123 Main St").build(),
                "created_at" to AttributeValue.builder().n("1000").build(),
                "updated_at" to AttributeValue.builder().n("2000").build()
            )
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(listOf(item)).build()
            )
            every { client.deleteItem(any<DeleteItemRequest>()) } returns CompletableFuture.completedFuture(DeleteItemResponse.builder().build())

            val result = runBlocking { adapter.deleteShelter(2) }

            assertTrue(result)
        }

        @Test
        fun `deleteShelter returns false when shelter not found`() {
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(emptyList()).build()
            )

            val result = runBlocking { adapter.deleteShelter(999) }

            assertFalse(result)
        }

        @Test
        fun `deleteShelter returns false on exception during delete`() {
            val item = mapOf(
                "id" to AttributeValue.builder().n("1").build(),
                "user_id" to AttributeValue.builder().n("2").build(),
                "name" to AttributeValue.builder().s("Happy Paws").build(),
                "country" to AttributeValue.builder().s("US").build(),
                "city" to AttributeValue.builder().s("LA").build(),
                "address" to AttributeValue.builder().s("123 Main St").build(),
                "created_at" to AttributeValue.builder().n("1000").build(),
                "updated_at" to AttributeValue.builder().n("2000").build()
            )
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(listOf(item)).build()
            )
            every { client.deleteItem(any<DeleteItemRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.deleteShelter(2) }

            assertFalse(result)
        }

        @Test
        fun `getAllShelters returns mapped list`() {
            val item = mapOf(
                "id" to AttributeValue.builder().n("1").build(),
                "name" to AttributeValue.builder().s("Happy Paws").build(),
                "country" to AttributeValue.builder().s("US").build(),
                "city" to AttributeValue.builder().s("LA").build(),
                "address" to AttributeValue.builder().s("123 Main St").build(),
                "created_at" to AttributeValue.builder().n("1000").build(),
                "updated_at" to AttributeValue.builder().n("2000").build()
            )
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(listOf(item)).build()
            )

            val result = runBlocking { adapter.getAllShelters() }

            assertEquals(1, result.size)
            assertEquals("Happy Paws", result[0].name)
        }

        @Test
        fun `getAllShelters returns empty list on exception`() {
            every { client.scan(any<ScanRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.getAllShelters() }

            assertTrue(result.isEmpty())
        }

        @Test
        fun `toShelterDto falls back to defaults for missing fields`() {
            val item = emptyMap<String, AttributeValue>()
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(listOf(item)).build()
            )

            val result = runBlocking { adapter.getShelterByUserId(1) }

            assertNotNull(result)
            assertEquals(0, result.id)
            assertNull(result.userId)
            assertEquals("", result.name)
            assertEquals("", result.country)
            assertEquals("", result.city)
            assertEquals("", result.address)
            assertEquals("USD", result.currency)
            assertEquals(0L, result.createdAt)
            assertEquals(0L, result.updatedAt)
        }
    }

    // ---------------------------------------------------------------
    // Sterilization Locations
    // ---------------------------------------------------------------

    @Nested
    inner class SterilizationLocations {

        private val sampleLocation = SterilizationLocationDto(
            id = 1,
            userId = 2,
            name = "VetCare",
            country = "US",
            state = "TX",
            city = "Austin",
            neighborhood = "Central",
            address = "456 Oak Ave",
            zip = "78701",
            phone = "555-5678",
            email = "vetcare@example.com",
            website = "https://vetcare.example.com",
            description = "Sterilization clinic",
            createdAt = 3000L,
            updatedAt = 4000L
        )

        @Test
        fun `createSterilizationLocation succeeds and returns location`() {
            every { client.putItem(any<PutItemRequest>()) } returns CompletableFuture.completedFuture(PutItemResponse.builder().build())

            val result = runBlocking { adapter.createSterilizationLocation(sampleLocation) }

            assertEquals(sampleLocation, result)
        }

        @Test
        fun `createSterilizationLocation returns null on exception`() {
            every { client.putItem(any<PutItemRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.createSterilizationLocation(sampleLocation) }

            assertNull(result)
        }

        @Test
        fun `getSterilizationLocationByUserId returns mapped dto when present`() {
            val item = mapOf(
                "id" to AttributeValue.builder().n("1").build(),
                "user_id" to AttributeValue.builder().n("2").build(),
                "name" to AttributeValue.builder().s("VetCare").build(),
                "country" to AttributeValue.builder().s("US").build(),
                "city" to AttributeValue.builder().s("Austin").build(),
                "address" to AttributeValue.builder().s("456 Oak Ave").build(),
                "created_at" to AttributeValue.builder().n("3000").build(),
                "updated_at" to AttributeValue.builder().n("4000").build()
            )
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(listOf(item)).build()
            )

            val result = runBlocking { adapter.getSterilizationLocationByUserId(2) }

            assertNotNull(result)
            assertEquals("VetCare", result.name)
        }

        @Test
        fun `getSterilizationLocationByUserId returns null when no items`() {
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(emptyList()).build()
            )

            val result = runBlocking { adapter.getSterilizationLocationByUserId(999) }

            assertNull(result)
        }

        @Test
        fun `getSterilizationLocationByUserId returns null on exception`() {
            every { client.scan(any<ScanRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.getSterilizationLocationByUserId(2) }

            assertNull(result)
        }

        @Test
        fun `updateSterilizationLocation delegates to createSterilizationLocation`() {
            every { client.putItem(any<PutItemRequest>()) } returns CompletableFuture.completedFuture(PutItemResponse.builder().build())

            val result = runBlocking { adapter.updateSterilizationLocation(sampleLocation) }

            assertEquals(sampleLocation, result)
            verify { client.putItem(any<PutItemRequest>()) }
        }

        @Test
        fun `deleteSterilizationLocation returns true when location found and delete succeeds`() {
            val item = mapOf(
                "id" to AttributeValue.builder().n("1").build(),
                "user_id" to AttributeValue.builder().n("2").build(),
                "name" to AttributeValue.builder().s("VetCare").build(),
                "country" to AttributeValue.builder().s("US").build(),
                "city" to AttributeValue.builder().s("Austin").build(),
                "address" to AttributeValue.builder().s("456 Oak Ave").build(),
                "created_at" to AttributeValue.builder().n("3000").build(),
                "updated_at" to AttributeValue.builder().n("4000").build()
            )
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(listOf(item)).build()
            )
            every { client.deleteItem(any<DeleteItemRequest>()) } returns CompletableFuture.completedFuture(DeleteItemResponse.builder().build())

            val result = runBlocking { adapter.deleteSterilizationLocation(2) }

            assertTrue(result)
        }

        @Test
        fun `deleteSterilizationLocation returns false when location not found`() {
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(emptyList()).build()
            )

            val result = runBlocking { adapter.deleteSterilizationLocation(999) }

            assertFalse(result)
        }

        @Test
        fun `deleteSterilizationLocation returns false on exception during delete`() {
            val item = mapOf(
                "id" to AttributeValue.builder().n("1").build(),
                "user_id" to AttributeValue.builder().n("2").build(),
                "name" to AttributeValue.builder().s("VetCare").build(),
                "country" to AttributeValue.builder().s("US").build(),
                "city" to AttributeValue.builder().s("Austin").build(),
                "address" to AttributeValue.builder().s("456 Oak Ave").build(),
                "created_at" to AttributeValue.builder().n("3000").build(),
                "updated_at" to AttributeValue.builder().n("4000").build()
            )
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(listOf(item)).build()
            )
            every { client.deleteItem(any<DeleteItemRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.deleteSterilizationLocation(2) }

            assertFalse(result)
        }

        @Test
        fun `getAllSterilizationLocations returns mapped list`() {
            val item = mapOf(
                "id" to AttributeValue.builder().n("1").build(),
                "name" to AttributeValue.builder().s("VetCare").build(),
                "country" to AttributeValue.builder().s("US").build(),
                "city" to AttributeValue.builder().s("Austin").build(),
                "address" to AttributeValue.builder().s("456 Oak Ave").build(),
                "created_at" to AttributeValue.builder().n("3000").build(),
                "updated_at" to AttributeValue.builder().n("4000").build()
            )
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(listOf(item)).build()
            )

            val result = runBlocking { adapter.getAllSterilizationLocations() }

            assertEquals(1, result.size)
            assertEquals("VetCare", result[0].name)
        }

        @Test
        fun `getAllSterilizationLocations returns empty list on exception`() {
            every { client.scan(any<ScanRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.getAllSterilizationLocations() }

            assertTrue(result.isEmpty())
        }

        @Test
        fun `toSterilizationLocationDto falls back to defaults for missing fields`() {
            val item = emptyMap<String, AttributeValue>()
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(listOf(item)).build()
            )

            val result = runBlocking { adapter.getSterilizationLocationByUserId(1) }

            assertNotNull(result)
            assertEquals(0, result.id)
            assertNull(result.userId)
            assertEquals("", result.name)
            assertEquals("", result.country)
            assertEquals("", result.city)
            assertEquals("", result.address)
            assertEquals(0L, result.createdAt)
            assertEquals(0L, result.updatedAt)
        }
    }

    // ---------------------------------------------------------------
    // Temporal Homes
    // ---------------------------------------------------------------

    @Nested
    inner class TemporalHomes {

        private val sampleTemporalHome = TemporalHomeDto(
            userId = 1,
            alias = "Cozy Home",
            country = "US",
            state = "WA",
            city = "Seattle",
            zip = "98101",
            neighborhood = "Capitol Hill",
            createdAt = 7000L
        )

        @Test
        fun `createTemporalHome succeeds and returns temporal home`() {
            every { client.putItem(any<PutItemRequest>()) } returns CompletableFuture.completedFuture(PutItemResponse.builder().build())

            val result = runBlocking { adapter.createTemporalHome(sampleTemporalHome) }

            assertEquals(sampleTemporalHome, result)
        }

        @Test
        fun `createTemporalHome returns null on exception`() {
            every { client.putItem(any<PutItemRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.createTemporalHome(sampleTemporalHome) }

            assertNull(result)
        }

        @Test
        fun `getTemporalHomeByUserId returns mapped dto when present`() {
            val item = mapOf(
                "user_id" to AttributeValue.builder().n("1").build(),
                "alias" to AttributeValue.builder().s("Cozy Home").build(),
                "country" to AttributeValue.builder().s("US").build(),
                "state" to AttributeValue.builder().s("WA").build(),
                "city" to AttributeValue.builder().s("Seattle").build(),
                "zip" to AttributeValue.builder().s("98101").build(),
                "neighborhood" to AttributeValue.builder().s("Capitol Hill").build(),
                "created_at" to AttributeValue.builder().n("7000").build()
            )
            every { client.getItem(any<GetItemRequest>()) } returns CompletableFuture.completedFuture(
                GetItemResponse.builder().item(item).build()
            )

            val result = runBlocking { adapter.getTemporalHomeByUserId(1) }

            assertEquals(sampleTemporalHome, result)
        }

        @Test
        fun `getTemporalHomeByUserId returns null when not found`() {
            every { client.getItem(any<GetItemRequest>()) } returns CompletableFuture.completedFuture(
                GetItemResponse.builder().build()
            )

            val result = runBlocking { adapter.getTemporalHomeByUserId(999) }

            assertNull(result)
        }

        @Test
        fun `getTemporalHomeByUserId returns null on exception`() {
            every { client.getItem(any<GetItemRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.getTemporalHomeByUserId(1) }

            assertNull(result)
        }

        @Test
        fun `updateTemporalHome delegates to createTemporalHome`() {
            every { client.putItem(any<PutItemRequest>()) } returns CompletableFuture.completedFuture(PutItemResponse.builder().build())

            val result = runBlocking { adapter.updateTemporalHome(sampleTemporalHome) }

            assertEquals(sampleTemporalHome, result)
            verify { client.putItem(any<PutItemRequest>()) }
        }

        @Test
        fun `deleteTemporalHome returns true on success`() {
            every { client.deleteItem(any<DeleteItemRequest>()) } returns CompletableFuture.completedFuture(DeleteItemResponse.builder().build())

            val result = runBlocking { adapter.deleteTemporalHome(1) }

            assertTrue(result)
        }

        @Test
        fun `deleteTemporalHome returns false on exception`() {
            every { client.deleteItem(any<DeleteItemRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.deleteTemporalHome(1) }

            assertFalse(result)
        }

        @Test
        fun `getAllTemporalHomes returns mapped list`() {
            val item = mapOf(
                "user_id" to AttributeValue.builder().n("1").build(),
                "alias" to AttributeValue.builder().s("Cozy Home").build(),
                "country" to AttributeValue.builder().s("US").build(),
                "city" to AttributeValue.builder().s("Seattle").build(),
                "created_at" to AttributeValue.builder().n("7000").build()
            )
            every { client.scan(any<ScanRequest>()) } returns CompletableFuture.completedFuture(
                ScanResponse.builder().items(listOf(item)).build()
            )

            val result = runBlocking { adapter.getAllTemporalHomes() }

            assertEquals(1, result.size)
            assertEquals("Cozy Home", result[0].alias)
        }

        @Test
        fun `getAllTemporalHomes returns empty list on exception`() {
            every { client.scan(any<ScanRequest>()) } throws RuntimeException("boom")

            val result = runBlocking { adapter.getAllTemporalHomes() }

            assertTrue(result.isEmpty())
        }

        @Test
        fun `toTemporalHomeDto falls back to defaults for missing fields`() {
            val item = mapOf(
                "user_id" to AttributeValue.builder().n("1").build()
            )
            every { client.getItem(any<GetItemRequest>()) } returns CompletableFuture.completedFuture(
                GetItemResponse.builder().item(item).build()
            )

            val result = runBlocking { adapter.getTemporalHomeByUserId(1) }

            assertNotNull(result)
            assertEquals(1, result.userId)
            assertEquals("", result.alias)
            assertEquals("", result.country)
            assertEquals("", result.city)
            assertNull(result.zip)
            assertNull(result.neighborhood)
            assertEquals(0L, result.createdAt)
        }
    }

    // ---------------------------------------------------------------
    // Table name prefix
    // ---------------------------------------------------------------

    @Nested
    inner class TableNamePrefix {

        @Test
        fun `prefixed adapter uses prefixed table name on put`() {
            val prefixedClient = mockk<DynamoDbAsyncClient>()
            val prefixedAdapter = DynamoDBAdapter(prefixedClient, tableNamePrefix = "test_")
            val requestSlot = mutableListOf<PutItemRequest>()
            every { prefixedClient.putItem(capture(requestSlot)) } returns CompletableFuture.completedFuture(PutItemResponse.builder().build())

            val user = UserDto(id = 1, username = "u", displayName = "U")
            runBlocking { prefixedAdapter.createUser(user) }

            assertEquals("test_users", requestSlot.first().tableName())
        }
    }
}

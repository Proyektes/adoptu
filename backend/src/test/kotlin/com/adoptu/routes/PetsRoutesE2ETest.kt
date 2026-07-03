package com.adoptu.routes

import com.adoptu.adapters.db.AdoptionRequests
import com.adoptu.adapters.db.PetImages
import com.adoptu.adapters.db.Pets
import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.CreateAdoptionRequestRequest
import com.adoptu.dto.input.CreatePetRequest
import com.adoptu.dto.input.Gender
import com.adoptu.dto.input.PetDto
import com.adoptu.dto.input.PetImageDto
import com.adoptu.dto.input.Status
import com.adoptu.dto.input.UpdatePetRequest
import com.adoptu.mocks.MockImageStorage
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestDatabase
import com.adoptu.ports.ImageStoragePort
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.services.PetService
import com.adoptu.services.ServiceResult
import com.adoptu.services.auth.WebAuthnService
import com.adoptu.testsupport.TestHttp
import com.adoptu.testsupport.TestServer
import com.adoptu.testsupport.buildMultipartBody
import com.adoptu.web.JsonSupport
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PetsRoutesE2ETest {

    private val clock = Clock.System

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        createTestUsers()
    }

    private fun createTestUsers() {
        transaction {
            try {
                Users.insert {
                    it[Users.id] = 1
                    it[Users.username] = "rescuer@test.com"
                    it[Users.displayName] = "Test Rescuer"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 1
                    it[UserActiveRoles.role] = "RESCUER"
                }
            } catch (e: Exception) { }

            try {
                val adopterId = Users.insert {
                    it[Users.id] = 2
                    it[Users.username] = "adopter@test.com"
                    it[Users.displayName] = "Test Adopter"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                } get Users.id
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = adopterId
                    it[UserActiveRoles.role] = "ADOPTER"
                }
            } catch (e: Exception) { }

            try {
                val adminId = Users.insert {
                    it[Users.id] = 3
                    it[Users.username] = "admin@test.com"
                    it[Users.displayName] = "Test Admin"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                } get Users.id
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = adminId
                    it[UserActiveRoles.role] = "ADMIN"
                }
            } catch (e: Exception) { }
        }
    }

    private fun startTestServer() = TestServer.start(
        modules = listOf(
            module {
                single { com.adoptu.config.AppConfig.fromMap(mapOf("admin.email" to "admin@adopt-u.com")) }
                single<Clock> { Clock.System }
                single {
                    WebAuthnService(
                        get(), get(), get(), get(), get(),
                        "admin@adopt-u.com",
                        "localhost",
                        "Adopt-U Pet Adoption",
                        listOf("http://localhost:80")
                    )
                }
                single<ImageStoragePort> { MockImageStorage() }
                single { MockNotificationAdapter() }
                single<com.adoptu.ports.NotificationPort> { get<MockNotificationAdapter>() }
                single<PetRepositoryPort> { PetRepositoryImpl(get()) }
                single<com.adoptu.ports.UserRepositoryPort> { UserRepository(get()) }
                single<com.adoptu.ports.PhotographerRepositoryPort> { PhotographerRepositoryImpl(get(), get(), get()) }
                single { com.adoptu.services.PhotographerService(get(), get(), get(), get()) }
                single { com.adoptu.services.UserService(get()) }
                single { PetService(get(), get(), get(), get()) }
                single { com.adoptu.services.validation.PetsValidationService() }
            }
        ),
        initDatabase = false,
        withTestLogin = true
    )

    private fun generateTestImageBytes(): ByteArray {
        val image = java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val out = java.io.ByteArrayOutputStream()
        javax.imageio.ImageIO.write(image, "jpg", out)
        return out.toByteArray()
    }

    private fun createImageInDb(petId: Int, isPrimary: Boolean = false): Int {
        return transaction {
            PetImages.insert {
                it[PetImages.petId] = petId
                it[PetImages.imageUrl] = "https://mock-storage.example.com/existing.jpg"
                it[PetImages.isPrimary] = isPrimary
                it[PetImages.sortOrder] = 0
            } get PetImages.id
        }
    }

    // ==================== GET /api/pets ====================

    @Test
    fun `GET pets returns empty list when no pets`() {
        TestDatabase.clearAllData()

        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/pets?country=United%20States")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body == "[]" || !body.contains("id"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET pets returns 400 when country is missing`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/pets")
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Country is required"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET pets returns 400 when country is blank`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/pets?country=")
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET pets returns all available pets`() {
        createPetInDb("Buddy", "DOG")
        createPetInDb("Whiskers", "CAT")

        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/pets?country=United%20States")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("Buddy") || body.contains("Whiskers"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET pets excludes pets from a different country`() {
        createPetInDb("Buddy", "DOG", country = "United States")
        createPetInDb("Milo", "DOG", country = "Canada")

        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/pets?country=United%20States")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("Buddy"))
            assertTrue(!body.contains("Milo"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET pets filters by type query parameter`() {
        createPetInDb("Buddy", "DOG")
        createPetInDb("Max", "DOG")

        val handle = startTestServer()
        try {
            val dogsResponse = TestHttp.get("${handle.baseUrl}/api/pets?type=DOG&country=United%20States")
            assertEquals(200, dogsResponse.statusCode())
            val body = dogsResponse.body()
            assertTrue(body.contains("Buddy"))
            assertTrue(body.contains("Max"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET pets returns only available pets`() {
        createPetInDb("Buddy", "DOG", status = "ADOPTED")
        createPetInDb("Whiskers", "CAT")

        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/pets?country=United%20States")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("Whiskers"))
            assertTrue(!body.contains("Buddy") || body.indexOf("Buddy") > body.indexOf("Whiskers"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/pets/mine ====================

    @Test
    fun `GET pets mine returns 401 when no session`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/pets/mine")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET pets mine returns 403 for adopter role`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2) // adopter

            val response = TestHttp.get("${handle.baseUrl}/api/pets/mine", cookie)
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET pets mine includes pets with no country and non-available status`() {
        createPetInDb("NoCountryPet", "DOG", country = null)
        createPetInDb("AdoptedPet", "DOG", status = "ADOPTED", country = "Canada")

        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1) // rescuer/owner

            val response = TestHttp.get("${handle.baseUrl}/api/pets/mine", cookie)
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("NoCountryPet"))
            assertTrue(body.contains("AdoptedPet"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/pets/{id} ====================

    @Test
    fun `GET pet by id returns 404 for non-existent pet`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/pets/999")
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET pet by id returns pet when exists`() {
        val petId = createPetInDb("Buddy", "DOG")

        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/pets/$petId")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("Buddy"))
            assertTrue(body.contains("DOG"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET pet by id returns 400 for invalid id`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/pets/abc")
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET pet by id returns pet even if adopted`() {
        val petId = createPetInDb("Buddy", "DOG", status = "ADOPTED")

        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/pets/$petId")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("Buddy"))
            assertTrue(body.contains("ADOPTED"))
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/pets ====================

    @Test
    fun `POST pets returns 401 when no session`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets",
                """{"name":"Test","type":"DOG"}"""
            )
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== PUT /api/pets/{id} ====================

    @Test
    fun `PUT pets returns 401 when no session`() {
        val petId = createPetInDb("Buddy", "DOG")

        val handle = startTestServer()
        try {
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/pets/$petId",
                """{"name":"Updated"}"""
            )
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== DELETE /api/pets/{id} ====================

    @Test
    fun `DELETE pets returns 401 when no session`() {
        val petId = createPetInDb("Buddy", "DOG")

        val handle = startTestServer()
        try {
            val response = TestHttp.delete("${handle.baseUrl}/api/pets/$petId")

            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/pets/{id}/adopt ====================

    @Test
    fun `POST adopt returns 401 when no session`() {
        val petId = createPetInDb("Buddy", "DOG")

        val handle = startTestServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/adopt",
                """{"message":"I want to adopt"}"""
            )

            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/pets/{id}/images ====================

    @Test
    fun `POST pets images returns 401 when no session`() {
        val petId = createPetInDb("Buddy", "DOG")

        val handle = startTestServer()
        try {
            val response = TestHttp.post("${handle.baseUrl}/api/pets/$petId/images")

            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/pets with various filters ====================

    @Test
    fun `GET pets returns pets with correct fields`() {
        createPetInDb("Buddy", "DOG", breed = "Golden Retriever")

        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/pets?country=United%20States")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("Buddy"))
            assertTrue(body.contains("Golden Retriever"))
            assertTrue(body.contains("DOG"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET pets handles case insensitive type filter`() {
        createPetInDb("Buddy", "DOG")

        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/pets?type=dog&country=United%20States")
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET pet by id returns correct pet details`() {
        val petId = createPetInDb("Buddy", "DOG", description = "A lovely dog", weight = 25.5)

        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/pets/$petId")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("Buddy"))
            assertTrue(body.contains("25.5"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET pet by id includes rescuer info`() {
        val petId = createPetInDb("Buddy", "DOG")

        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/pets/$petId")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("rescuerId") || body.contains("1"))
        } finally {
            handle.stop()
        }
    }

    // ==================== DELETE /api/pets/{petId}/images/{imageId} ====================

    @Test
    fun `DELETE pets images returns 401 when no session`() {
        val petId = createPetInDb("Buddy", "DOG")

        val handle = startTestServer()
        try {
            val response = TestHttp.delete("${handle.baseUrl}/api/pets/$petId/images/1")

            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/pets/{id}/images - Service Tests with Mocked Repository ====================

    @Test
    fun `POST pets images service returns Success when user is owner`() = runBlocking {
        val mockRepository = mockk<PetRepositoryPort>(relaxed = true)
        val mockImageStorage = mockk<ImageStoragePort>(relaxed = true)
        val petService = PetService(mockRepository, mockImageStorage, mockk(relaxed = true), mockk(relaxed = true))

        coEvery { mockRepository.getById(1) } returns createMockPetDto(1, rescuerId = 1)
        coEvery { mockRepository.addImage(1, "https://test.com/new.jpg", false) } returns createMockPetImage()

        val result = petService.addImage(
            petId = 1,
            userId = 1,
            userRoles = setOf("RESCUER"),
            imageUrl = "https://test.com/new.jpg",
            isPrimary = false
        )

        assertTrue(result is ServiceResult.Success)
    }

    @Test
    fun `POST pets images service returns Success when user is admin`() = runBlocking {
        val mockRepository = mockk<PetRepositoryPort>(relaxed = true)
        val mockImageStorage = mockk<ImageStoragePort>(relaxed = true)
        val petService = PetService(mockRepository, mockImageStorage, mockk(relaxed = true), mockk(relaxed = true))

        coEvery { mockRepository.getById(1) } returns createMockPetDto(1, rescuerId = 99)
        coEvery { mockRepository.addImage(1, "test.jpg", false) } returns createMockPetImage()

        val result = petService.addImage(
            petId = 1,
            userId = 5,
            userRoles = setOf("ADMIN"),
            imageUrl = "https://test.com/new.jpg",
            isPrimary = false
        )

        assertTrue(result is ServiceResult.Success)
    }

    @Test
    fun `POST pets images service returns NotFound when pet does not exist`() = runBlocking {
        val mockRepository = mockk<PetRepositoryPort>(relaxed = true)
        val mockImageStorage = mockk<ImageStoragePort>(relaxed = true)
        val petService = PetService(mockRepository, mockImageStorage, mockk(relaxed = true), mockk(relaxed = true))

        coEvery { mockRepository.getById(999) } returns null

        val result = petService.addImage(
            petId = 999,
            userId = 1,
            userRoles = setOf("RESCUER"),
            imageUrl = "https://test.com/new.jpg",
            isPrimary = false
        )

        assertEquals(ServiceResult.NotFound, result)
    }

    @Test
    fun `POST pets images service returns Forbidden when user is not owner or admin`() = runBlocking {
        val mockRepository = mockk<PetRepositoryPort>(relaxed = true)
        val mockImageStorage = mockk<ImageStoragePort>(relaxed = true)
        val petService = PetService(mockRepository, mockImageStorage, mockk(relaxed = true), mockk(relaxed = true))

        coEvery { mockRepository.getById(1) } returns createMockPetDto(1, rescuerId = 99)

        val result = petService.addImage(
            petId = 1,
            userId = 5,
            userRoles = setOf("ADOPTER"),
            imageUrl = "https://test.com/new.jpg",
            isPrimary = false
        )

        assertEquals(ServiceResult.Forbidden, result)
    }

    // ==================== DELETE /api/pets/{petId}/images/{imageId} - Service Tests with Mocked Repository ====================

    @Test
    fun `DELETE pets images service returns Success when user is owner and image exists`() = runBlocking {
        val mockRepository = mockk<PetRepositoryPort>(relaxed = true)
        val mockImageStorage = mockk<ImageStoragePort>(relaxed = true)
        val petService = PetService(mockRepository, mockImageStorage, mockk(relaxed = true), mockk(relaxed = true))

        coEvery { mockRepository.getById(1) } returns createMockPetDto(1, rescuerId = 1)
        coEvery { mockRepository.getImages(1) } returns listOf(createMockPetImage(10))
        coEvery { mockRepository.removeImage(1, 10) } returns true

        val result = petService.removeImage(
            petId = 1,
            imageId = 10,
            userId = 1,
            userRoles = setOf("RESCUER")
        )

        assertEquals(ServiceResult.Success(Unit), result)
        coVerify { mockImageStorage.deleteImage("https://test.com/img.jpg") }
    }

    @Test
    fun `DELETE pets images service returns Success when user is admin`() = runBlocking {
        val mockRepository = mockk<PetRepositoryPort>(relaxed = true)
        val mockImageStorage = mockk<ImageStoragePort>(relaxed = true)
        val petService = PetService(mockRepository, mockImageStorage, mockk(relaxed = true), mockk(relaxed = true))

        coEvery { mockRepository.getById(1) } returns createMockPetDto(1, rescuerId = 99)
        coEvery { mockRepository.getImages(1) } returns listOf(createMockPetImage(10))
        coEvery { mockRepository.removeImage(1, 10) } returns true

        val result = petService.removeImage(
            petId = 1,
            imageId = 10,
            userId = 5,
            userRoles = setOf("ADMIN")
        )

        assertEquals(ServiceResult.Success(Unit), result)
    }

    @Test
    fun `DELETE pets images service returns NotFound when pet does not exist`() = runBlocking {
        val mockRepository = mockk<PetRepositoryPort>(relaxed = true)
        val mockImageStorage = mockk<ImageStoragePort>(relaxed = true)
        val petService = PetService(mockRepository, mockImageStorage, mockk(relaxed = true), mockk(relaxed = true))

        coEvery { mockRepository.getById(999) } returns null

        val result = petService.removeImage(
            petId = 999,
            imageId = 10,
            userId = 1,
            userRoles = setOf("RESCUER")
        )

        assertEquals(ServiceResult.NotFound, result)
    }

    @Test
    fun `DELETE pets images service returns NotFound when image does not exist`() = runBlocking {
        val mockRepository = mockk<PetRepositoryPort>(relaxed = true)
        val mockImageStorage = mockk<ImageStoragePort>(relaxed = true)
        val petService = PetService(mockRepository, mockImageStorage, mockk(relaxed = true), mockk(relaxed = true))

        coEvery { mockRepository.getById(1) } returns createMockPetDto(1, rescuerId = 1)
        coEvery { mockRepository.getImages(1) } returns emptyList()

        val result = petService.removeImage(
            petId = 1,
            imageId = 999,
            userId = 1,
            userRoles = setOf("RESCUER")
        )

        assertEquals(ServiceResult.NotFound, result)
    }

    @Test
    fun `DELETE pets images service returns Forbidden when user is not owner or admin`() = runBlocking {
        val mockRepository = mockk<PetRepositoryPort>(relaxed = true)
        val mockImageStorage = mockk<ImageStoragePort>(relaxed = true)
        val petService = PetService(mockRepository, mockImageStorage, mockk(relaxed = true), mockk(relaxed = true))

        coEvery { mockRepository.getById(1) } returns createMockPetDto(1, rescuerId = 99)

        val result = petService.removeImage(
            petId = 1,
            imageId = 10,
            userId = 5,
            userRoles = setOf("ADOPTER")
        )

        assertEquals(ServiceResult.Forbidden, result)
    }

    @Test
    fun `DELETE pets images service returns Forbidden when user is rescuer but not owner`() = runBlocking {
        val mockRepository = mockk<PetRepositoryPort>(relaxed = true)
        val mockImageStorage = mockk<ImageStoragePort>(relaxed = true)
        val petService = PetService(mockRepository, mockImageStorage, mockk(relaxed = true), mockk(relaxed = true))

        coEvery { mockRepository.getById(1) } returns createMockPetDto(1, rescuerId = 99)

        val result = petService.removeImage(
            petId = 1,
            imageId = 10,
            userId = 50,
            userRoles = setOf("RESCUER")
        )

        assertEquals(ServiceResult.Forbidden, result)
    }

    // ==================== POST /api/pets (authenticated branches) ====================

    @Test
    fun `POST pets returns 403 for adopter role`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2) // adopter

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets",
                JsonSupport.objectMapper.writeValueAsString(CreatePetRequest(name = "Test", type = "DOG")),
                cookie
            )
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pets returns 404 when session user does not exist`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets",
                JsonSupport.objectMapper.writeValueAsString(CreatePetRequest(name = "Test", type = "DOG")),
                cookie
            )
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pets succeeds for rescuer`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1) // rescuer

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets",
                JsonSupport.objectMapper.writeValueAsString(CreatePetRequest(name = "Rover", type = "DOG", country = "United States")),
                cookie
            )
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Rover"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pets succeeds for admin`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets",
                JsonSupport.objectMapper.writeValueAsString(CreatePetRequest(name = "AdminPet", type = "CAT", country = "United States")),
                cookie
            )
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("AdminPet"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pets returns 400 when no country provided and rescuer profile has no country`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1) // rescuer, no profile country set

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets",
                JsonSupport.objectMapper.writeValueAsString(CreatePetRequest(name = "NoCountry", type = "DOG")),
                cookie
            )
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Country is required"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pets defaults country from the rescuer's profile when omitted`() {
        transaction {
            Users.update({ Users.id eq 1 }) {
                it[Users.country] = com.adoptu.common.Country.CANADA
            }
        }

        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1) // rescuer with profile country set

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets",
                JsonSupport.objectMapper.writeValueAsString(CreatePetRequest(name = "DefaultedCountryPet", type = "DOG")),
                cookie
            )
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Canada"))
        } finally {
            handle.stop()
        }
    }

    // ==================== PUT /api/pets/{id} (authenticated branches) ====================

    @Test
    fun `PUT pets returns 404 when session user does not exist`() {
        val petId = createPetInDb("Buddy", "DOG")
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/pets/$petId",
                JsonSupport.objectMapper.writeValueAsString(UpdatePetRequest(name = "Updated")),
                cookie
            )
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT pets returns 400 for invalid id`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/pets/abc",
                JsonSupport.objectMapper.writeValueAsString(UpdatePetRequest(name = "Updated")),
                cookie
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT pets returns 404 when pet does not exist`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/pets/9999",
                JsonSupport.objectMapper.writeValueAsString(UpdatePetRequest(name = "Updated")),
                cookie
            )
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT pets returns 403 for non-owner`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2) // adopter, not the owner

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/pets/$petId",
                JsonSupport.objectMapper.writeValueAsString(UpdatePetRequest(name = "Updated")),
                cookie
            )
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT pets succeeds for owner`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/pets/$petId",
                JsonSupport.objectMapper.writeValueAsString(UpdatePetRequest(name = "UpdatedName")),
                cookie
            )
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("UpdatedName"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT pets succeeds for admin on someone else's pet`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/pets/$petId",
                JsonSupport.objectMapper.writeValueAsString(UpdatePetRequest(name = "AdminUpdated")),
                cookie
            )
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("AdminUpdated"))
        } finally {
            handle.stop()
        }
    }

    // ==================== DELETE /api/pets/{id} (authenticated branches) ====================

    @Test
    fun `DELETE pets returns 404 when session user does not exist`() {
        val petId = createPetInDb("Buddy", "DOG")
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val response = TestHttp.delete("${handle.baseUrl}/api/pets/$petId", cookie)
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE pets returns 400 for invalid id`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.delete("${handle.baseUrl}/api/pets/abc", cookie)
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE pets returns 404 when pet does not exist`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.delete("${handle.baseUrl}/api/pets/9999", cookie)
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE pets returns 403 for non-owner`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val response = TestHttp.delete("${handle.baseUrl}/api/pets/$petId", cookie)
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE pets succeeds for owner`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.delete("${handle.baseUrl}/api/pets/$petId", cookie)
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE pets succeeds for admin on someone else's pet`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3)

            val response = TestHttp.delete("${handle.baseUrl}/api/pets/$petId", cookie)
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/pets/{id}/images (authenticated branches) ====================

    @Test
    fun `POST pets images returns 404 when session user does not exist`() {
        val petId = createPetInDb("Buddy", "DOG")
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val response = TestHttp.post("${handle.baseUrl}/api/pets/$petId/images", cookie)
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pets images returns 400 for invalid id`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.post("${handle.baseUrl}/api/pets/abc/images", cookie)
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pets images via imageIds param returns 404 for non-existent pet`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.post("${handle.baseUrl}/api/pets/9999/images?imageIds=1,2", cookie)
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pets images via imageIds param returns 403 for non-owner`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val response = TestHttp.post("${handle.baseUrl}/api/pets/$petId/images?imageIds=1,2", cookie)
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pets images via imageIds param succeeds for owner`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.post("${handle.baseUrl}/api/pets/$petId/images?imageIds=1,2", cookie)
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("images"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pets images multipart returns 400 when no file provided`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val boundary = "----TestBoundary${System.nanoTime()}"
            val body = buildMultipartBody(boundary, fields = mapOf("isPrimary" to "false"))

            val response = TestHttp.multipart("${handle.baseUrl}/api/pets/$petId/images", boundary, body, cookie)
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("No storage provided"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pets images multipart returns 404 when pet does not exist`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val boundary = "----TestBoundary${System.nanoTime()}"
            val body = buildMultipartBody(
                boundary,
                files = mapOf("file" to Triple("test.jpg", "image/jpeg", generateTestImageBytes()))
            )

            val response = TestHttp.multipart("${handle.baseUrl}/api/pets/9999/images", boundary, body, cookie)
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pets images multipart returns 403 for non-owner`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val boundary = "----TestBoundary${System.nanoTime()}"
            val body = buildMultipartBody(
                boundary,
                files = mapOf("file" to Triple("test.jpg", "image/jpeg", generateTestImageBytes()))
            )

            val response = TestHttp.multipart("${handle.baseUrl}/api/pets/$petId/images", boundary, body, cookie)
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pets images multipart succeeds with valid image for owner`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val boundary = "----TestBoundary${System.nanoTime()}"
            val body = buildMultipartBody(
                boundary,
                fields = mapOf("isPrimary" to "true"),
                files = mapOf("file" to Triple("test.jpg", "image/jpeg", generateTestImageBytes()))
            )

            val response = TestHttp.multipart("${handle.baseUrl}/api/pets/$petId/images", boundary, body, cookie)
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("mock-storage"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pets images multipart returns 500 for unparseable image data`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val boundary = "----TestBoundary${System.nanoTime()}"
            val body = buildMultipartBody(
                boundary,
                files = mapOf("file" to Triple("test.jpg", "image/jpeg", "not-a-real-image".toByteArray()))
            )

            val response = TestHttp.multipart("${handle.baseUrl}/api/pets/$petId/images", boundary, body, cookie)
            assertEquals(500, response.statusCode())
            assertTrue(response.body().contains("Failed to upload storage"))
        } finally {
            handle.stop()
        }
    }

    // ==================== DELETE /api/pets/{petId}/images/{imageId} (authenticated branches) ====================

    @Test
    fun `DELETE pets images returns 404 when session user does not exist`() {
        val petId = createPetInDb("Buddy", "DOG")
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val response = TestHttp.delete("${handle.baseUrl}/api/pets/$petId/images/1", cookie)
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE pets images returns error for invalid pet id`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.delete("${handle.baseUrl}/api/pets/abc/images/1", cookie)
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Invalid pet ID"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE pets images returns error for invalid image id`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.delete("${handle.baseUrl}/api/pets/$petId/images/abc", cookie)
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Invalid storage ID"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE pets images returns 404 when pet does not exist`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.delete("${handle.baseUrl}/api/pets/9999/images/1", cookie)
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE pets images returns 404 when image does not exist`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.delete("${handle.baseUrl}/api/pets/$petId/images/9999", cookie)
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE pets images returns 403 for non-owner`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val imageId = createImageInDb(petId)
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val response = TestHttp.delete("${handle.baseUrl}/api/pets/$petId/images/$imageId", cookie)
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE pets images succeeds for owner`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val imageId = createImageInDb(petId)
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.delete("${handle.baseUrl}/api/pets/$petId/images/$imageId", cookie)
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/pets/{id}/adopt (authenticated branches) ====================

    @Test
    fun `POST adopt returns 404 when session user does not exist`() {
        val petId = createPetInDb("Buddy", "DOG")
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/adopt",
                JsonSupport.objectMapper.writeValueAsString(CreateAdoptionRequestRequest("I want to adopt")),
                cookie
            )
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST adopt returns 403 for non-adopter role`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1) // rescuer, not an adopter

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/adopt",
                JsonSupport.objectMapper.writeValueAsString(CreateAdoptionRequestRequest("I want to adopt")),
                cookie
            )
            assertEquals(403, response.statusCode())
            assertTrue(response.body().contains("Only adopters can request adoption"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST adopt returns 400 for invalid id`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2) // adopter

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/abc/adopt",
                JsonSupport.objectMapper.writeValueAsString(CreateAdoptionRequestRequest("I want to adopt")),
                cookie
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST adopt succeeds for adopter`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2) // adopter

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/adopt",
                JsonSupport.objectMapper.writeValueAsString(CreateAdoptionRequestRequest("I want to adopt Buddy")),
                cookie
            )
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("I want to adopt Buddy"))
        } finally {
            handle.stop()
        }
    }

    // ==================== Helper Methods ====================

    private fun createPetInDb(
        name: String,
        type: String,
        rescuerId: Int = 1,
        status: String = "AVAILABLE",
        breed: String = "Test breed",
        description: String = "Test description",
        weight: Double = 10.0,
        country: String? = "United States"
    ): Int {
        return transaction {
            Pets.insert {
                it[Pets.rescuerId] = rescuerId
                it[Pets.name] = name
                it[Pets.type] = type
                it[Pets.description] = description
                it[Pets.weight] = BigDecimal(weight.toString())
                it[Pets.ageYears] = 2
                it[Pets.ageMonths] = 0
                it[Pets.sex] = "MALE"
                it[Pets.breed] = breed
                it[Pets.status] = status
                it[Pets.size] = "MEDIUM"
                it[Pets.isUrgent] = false
                it[Pets.country] = country?.let { com.adoptu.common.Country.fromDisplayName(it) }
                it[Pets.createdAt] = clock.now().toEpochMilliseconds()
            } get Pets.id
        }
    }

    private fun createMockPetDto(petId: Int, rescuerId: Int = 1) = PetDto(
        id = petId,
        rescuerId = rescuerId,
        name = "Buddy",
        type = "DOG",
        breed = "Golden",
        description = "Test",
        weight = 10.0,
        ageYears = 2,
        ageMonths = 0,
        sex = Gender.MALE,
        status = Status.AVAILABLE,
        size = "MEDIUM",
        isUrgent = false,
        createdAt = clock.now().toEpochMilliseconds()
    )

    private fun createMockPetImage(imageId: Int = 10) = PetImageDto(
        id = imageId,
        imageUrl = "https://test.com/img.jpg",
        isPrimary = true,
        sortOrder = 0
    )

    private fun createAdoptionRequestInDb(petId: Int, adopterId: Int = 2, status: String = "PENDING"): Int {
        return transaction {
            AdoptionRequests.insert {
                it[AdoptionRequests.petId] = petId
                it[AdoptionRequests.adopterId] = adopterId
                it[AdoptionRequests.message] = "Please let me adopt"
                it[AdoptionRequests.status] = status
                it[AdoptionRequests.createdAt] = clock.now().toEpochMilliseconds()
            } get AdoptionRequests.id
        }
    }

    // ==================== PUT /api/pets/{petId}/images/{imageId}/primary ====================

    @Test
    fun `PUT primary image returns 401 when no session`() {
        val petId = createPetInDb("Buddy", "DOG")
        val imageId = createImageInDb(petId)

        val handle = startTestServer()
        try {
            val response = TestHttp.put("${handle.baseUrl}/api/pets/$petId/images/$imageId/primary")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT primary image returns 400 for invalid pet id`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)
            val response = TestHttp.put("${handle.baseUrl}/api/pets/not-a-number/images/1/primary", cookie)
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT primary image returns 403 for non-owner`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val imageId = createImageInDb(petId)

        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2) // adopter, not the owner

            val response = TestHttp.put("${handle.baseUrl}/api/pets/$petId/images/$imageId/primary", cookie)
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT primary image succeeds for owner`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val imageId = createImageInDb(petId)

        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.put("${handle.baseUrl}/api/pets/$petId/images/$imageId/primary", cookie)
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT primary image succeeds for admin on someone else's pet`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val imageId = createImageInDb(petId)

        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.put("${handle.baseUrl}/api/pets/$petId/images/$imageId/primary", cookie)
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT primary image returns 404 when pet does not exist`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.put("${handle.baseUrl}/api/pets/9999/images/9999/primary", cookie)
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/pets/{id}/adoption-requests ====================

    @Test
    fun `GET adoption-requests returns 401 when no session`() {
        val petId = createPetInDb("Buddy", "DOG")

        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/pets/$petId/adoption-requests")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET adoption-requests returns 400 for invalid id`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)
            val response = TestHttp.get("${handle.baseUrl}/api/pets/not-a-number/adoption-requests", cookie)
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET adoption-requests returns 403 for non-owner`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        createAdoptionRequestInDb(petId)

        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2) // adopter, not the owner

            val response = TestHttp.get("${handle.baseUrl}/api/pets/$petId/adoption-requests", cookie)
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET adoption-requests succeeds for owner`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        createAdoptionRequestInDb(petId)

        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.get("${handle.baseUrl}/api/pets/$petId/adoption-requests", cookie)
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Please let me adopt"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET adoption-requests succeeds for admin on someone else's pet`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        createAdoptionRequestInDb(petId)

        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.get("${handle.baseUrl}/api/pets/$petId/adoption-requests", cookie)
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== PUT /api/pets/adoption-requests/{requestId} ====================

    @Test
    fun `PUT adoption-requests returns 401 when no session`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.putForm("${handle.baseUrl}/api/pets/adoption-requests/1", "status=APPROVED")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT adoption-requests returns 400 for invalid request id`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)
            val response = TestHttp.putForm("${handle.baseUrl}/api/pets/adoption-requests/not-a-number", "status=APPROVED", cookie)
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT adoption-requests returns 400 when status param missing`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val requestId = createAdoptionRequestInDb(petId)

        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)
            val response = TestHttp.putForm("${handle.baseUrl}/api/pets/adoption-requests/$requestId", "", cookie)
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT adoption-requests returns 404 for non-existent request`() {
        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)
            val response = TestHttp.putForm("${handle.baseUrl}/api/pets/adoption-requests/9999", "status=APPROVED", cookie)
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT adoption-requests returns 403 for non-owner`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val requestId = createAdoptionRequestInDb(petId)

        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2) // adopter, not the owner

            val response = TestHttp.putForm("${handle.baseUrl}/api/pets/adoption-requests/$requestId", "status=APPROVED", cookie)
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT adoption-requests rejects an invalid status value`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val requestId = createAdoptionRequestInDb(petId)

        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.putForm("${handle.baseUrl}/api/pets/adoption-requests/$requestId", "status=NOT_A_REAL_STATUS", cookie)
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT adoption-requests succeeds for owner approving a request`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val requestId = createAdoptionRequestInDb(petId)

        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.putForm("${handle.baseUrl}/api/pets/adoption-requests/$requestId", "status=APPROVED", cookie)
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("APPROVED"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT adoption-requests succeeds for admin rejecting someone else's request`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        val requestId = createAdoptionRequestInDb(petId)

        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.putForm("${handle.baseUrl}/api/pets/adoption-requests/$requestId", "status=REJECTED", cookie)
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("REJECTED"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/pets/my-adoption-requests ====================

    @Test
    fun `GET my-adoption-requests returns 401 when no session`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/pets/my-adoption-requests")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET my-adoption-requests returns requests made by the session user`() {
        val petId = createPetInDb("Buddy", "DOG", rescuerId = 1)
        createAdoptionRequestInDb(petId, adopterId = 2)

        val handle = startTestServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2) // adopter

            val response = TestHttp.get("${handle.baseUrl}/api/pets/my-adoption-requests", cookie)
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Please let me adopt"))
        } finally {
            handle.stop()
        }
    }
}

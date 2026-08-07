package com.adoptu.routes

import com.adoptu.adapters.db.Pets
import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.PetMedicalEventRepositoryImpl
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.CreatePetMedicalEventRequest
import com.adoptu.dto.input.MedicalEventCategory
import com.adoptu.mocks.TestDatabase
import com.adoptu.ports.PetMedicalEventRepositoryPort
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.ports.PhotographerRepositoryPort
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.services.PetMedicalEventService
import com.adoptu.services.UserService
import com.adoptu.services.validation.PetsValidationService
import com.adoptu.testsupport.TestHttp
import com.adoptu.testsupport.TestServer
import com.adoptu.web.JsonSupport
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PetMedicalEventRoutesE2ETest {

    private val clock = Clock.System

    private val testModules = listOf(
        module {
            single<Clock> { Clock.System }
            single<UserRepositoryPort> { UserRepository(get()) }
            single<PetRepositoryPort> { PetRepositoryImpl(get()) }
            single<PhotographerRepositoryPort> { PhotographerRepositoryImpl(get(), get(), get()) }
            single { UserService(get(), get()) }
            single { PetsValidationService() }
            single<PetMedicalEventRepositoryPort> { PetMedicalEventRepositoryImpl(get()) }
            single { PetMedicalEventService(get(), get()) }
        }
    )

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
                    it[Users.displayName] = "Owning Rescuer"
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
                    it[Users.username] = "other-rescuer@test.com"
                    it[Users.displayName] = "Other Rescuer"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 2
                    it[UserActiveRoles.role] = "RESCUER"
                }
            } catch (e: Exception) { }

            try {
                Users.insert {
                    it[Users.id] = 3
                    it[Users.username] = "admin@test.com"
                    it[Users.displayName] = "Test Admin"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 3
                    it[UserActiveRoles.role] = "ADMIN"
                }
            } catch (e: Exception) { }
        }
    }

    private fun startServer() = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)

    private fun createPetInDb(rescuerId: Int = 1): Int {
        return transaction {
            Pets.insert {
                it[Pets.rescuerId] = rescuerId
                it[Pets.name] = "Buddy"
                it[Pets.type] = "DOG"
                it[Pets.description] = "Test description"
                it[Pets.weight] = BigDecimal("10.0")
                it[Pets.ageYears] = 2
                it[Pets.ageMonths] = 0
                it[Pets.sex] = "MALE"
                it[Pets.status] = "AVAILABLE"
                it[Pets.size] = "MEDIUM"
                it[Pets.isUrgent] = false
                it[Pets.country] = com.adoptu.common.Country.fromDisplayName("United States")
                it[Pets.createdAt] = clock.now().toEpochMilliseconds()
            } get Pets.id
        }
    }

    private fun sampleRequestJson(name: String = "Rabies", category: MedicalEventCategory = MedicalEventCategory.VACCINATION) =
        JsonSupport.objectMapper.writeValueAsString(
            CreatePetMedicalEventRequest(
                category = category,
                name = name,
                administeredDate = clock.now().toEpochMilliseconds(),
                nextDueDate = null,
                notes = null
            )
        )

    // ==================== GET /api/pets/{id}/medical-events ====================

    @Test
    fun `GET pet medical-events is public and returns the event list`() {
        val petId = createPetInDb(rescuerId = 1)

        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)
            val createResponse = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/medical-events",
                sampleRequestJson(),
                cookie
            )
            assertEquals(200, createResponse.statusCode())

            val response = TestHttp.get("${handle.baseUrl}/api/pets/$petId/medical-events")

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Rabies"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET pet medical-events returns empty list for a pet with no events`() {
        val petId = createPetInDb(rescuerId = 1)

        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/pets/$petId/medical-events")

            assertEquals(200, response.statusCode())
            assertEquals("[]", response.body())
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/pets/{id}/medical-events ====================

    @Test
    fun `POST pet medical-events returns 401 when no session`() {
        val petId = createPetInDb(rescuerId = 1)

        val handle = startServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/medical-events",
                sampleRequestJson()
            )

            assertEquals(401, response.statusCode())
            assertTrue(response.body().contains("Unauthorized"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pet medical-events creates event when caller is the owning rescuer`() {
        val petId = createPetInDb(rescuerId = 1)

        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/medical-events",
                sampleRequestJson(),
                cookie
            )

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Rabies"))

            val followUp = TestHttp.get("${handle.baseUrl}/api/pets/$petId/medical-events")
            assertEquals(200, followUp.statusCode())
            assertTrue(followUp.body().contains("Rabies"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pet medical-events returns 403 when caller is not the owner and not admin`() {
        val petId = createPetInDb(rescuerId = 1)

        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/medical-events",
                sampleRequestJson(),
                cookie
            )

            assertEquals(403, response.statusCode())
            assertTrue(response.body().contains("Forbidden"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pet medical-events succeeds for admin who is not the owner`() {
        val petId = createPetInDb(rescuerId = 1)

        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/medical-events",
                sampleRequestJson(),
                cookie
            )

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Rabies"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pet medical-events returns 400 for an invalid pet id`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/not-a-number/medical-events",
                sampleRequestJson(),
                cookie
            )

            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Invalid ID"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pet medical-events returns 404 when the session user no longer exists`() {
        val petId = createPetInDb(rescuerId = 1)

        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/medical-events",
                sampleRequestJson(),
                cookie
            )

            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST pet medical-events returns 404 for a non-existent pet`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/999999/medical-events",
                sampleRequestJson(),
                cookie
            )

            assertEquals(404, response.statusCode())
            assertTrue(response.body().contains("Not found"))
        } finally {
            handle.stop()
        }
    }

    // ==================== DELETE /api/pets/medical-events/{eventId} ====================

    private fun createEvent(handle: com.adoptu.testsupport.TestServerHandle, petId: Int, ownerUserId: Int): Int {
        val cookie = TestHttp.loginAs(handle.baseUrl, ownerUserId)
        val response = TestHttp.postJson(
            "${handle.baseUrl}/api/pets/$petId/medical-events",
            sampleRequestJson(),
            cookie
        )
        val body = response.body()
        val idMatch = Regex("\"id\"\\s*:\\s*(\\d+)").find(body)
            ?: throw IllegalStateException("Could not find id in response body: $body")
        return idMatch.groupValues[1].toInt()
    }

    @Test
    fun `DELETE pet medical-events returns 401 when no session`() {
        val response0Handle = startServer()
        try {
            val response = TestHttp.delete("${response0Handle.baseUrl}/api/pets/medical-events/1")
            assertEquals(401, response.statusCode())
            assertTrue(response.body().contains("Unauthorized"))
        } finally {
            response0Handle.stop()
        }
    }

    @Test
    fun `DELETE pet medical-events succeeds for the owning rescuer`() {
        val petId = createPetInDb(rescuerId = 1)

        val handle = startServer()
        try {
            val eventId = createEvent(handle, petId, ownerUserId = 1)
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.delete("${handle.baseUrl}/api/pets/medical-events/$eventId", cookie)

            assertEquals(200, response.statusCode())

            val followUp = TestHttp.get("${handle.baseUrl}/api/pets/$petId/medical-events")
            assertEquals("[]", followUp.body())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE pet medical-events succeeds for admin who is not the owner`() {
        val petId = createPetInDb(rescuerId = 1)

        val handle = startServer()
        try {
            val eventId = createEvent(handle, petId, ownerUserId = 1)
            val cookie = TestHttp.loginAs(handle.baseUrl, 3)

            val response = TestHttp.delete("${handle.baseUrl}/api/pets/medical-events/$eventId", cookie)

            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE pet medical-events returns 403 when caller is not the owner and not admin`() {
        val petId = createPetInDb(rescuerId = 1)

        val handle = startServer()
        try {
            val eventId = createEvent(handle, petId, ownerUserId = 1)
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val response = TestHttp.delete("${handle.baseUrl}/api/pets/medical-events/$eventId", cookie)

            assertEquals(403, response.statusCode())
            assertTrue(response.body().contains("Forbidden"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE pet medical-events returns 400 for an invalid event id`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.delete("${handle.baseUrl}/api/pets/medical-events/not-a-number", cookie)

            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Invalid ID"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE pet medical-events returns 404 for a non-existent event`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.delete("${handle.baseUrl}/api/pets/medical-events/999999", cookie)

            assertEquals(404, response.statusCode())
            assertTrue(response.body().contains("Not found"))
        } finally {
            handle.stop()
        }
    }
}

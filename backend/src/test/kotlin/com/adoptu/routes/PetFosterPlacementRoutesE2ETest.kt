package com.adoptu.routes

import com.adoptu.adapters.db.Pets
import com.adoptu.adapters.db.TemporalHomes
import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.PetFosterPlacementRepositoryImpl
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.TemporalHomeRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.common.Country
import com.adoptu.dto.input.CreateFosterPlacementRequest
import com.adoptu.mocks.TestDatabase
import com.adoptu.ports.PetFosterPlacementRepositoryPort
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.ports.PhotographerRepositoryPort
import com.adoptu.ports.TemporalHomeRepositoryPort
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.services.PetFosterPlacementService
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

// Covers PetFosterPlacementRoutes.kt end-to-end (previously only unit-tested at the service
// layer via PetFosterPlacementServiceTest). Re-verifies the same authorization/business-rule
// outcomes through the real HTTP route rather than duplicating the service test's internals.
@OptIn(ExperimentalTime::class)
class PetFosterPlacementRoutesE2ETest {

    private val clock = Clock.System

    private val testModules = listOf(
        module {
            single<Clock> { Clock.System }
            single<PetRepositoryPort> { PetRepositoryImpl(get()) }
            single<UserRepositoryPort> { UserRepository(get()) }
            single<PhotographerRepositoryPort> { PhotographerRepositoryImpl(get(), get(), get()) }
            single<TemporalHomeRepositoryPort> { TemporalHomeRepositoryImpl(get(), get(), get()) }
            single<PetFosterPlacementRepositoryPort> { PetFosterPlacementRepositoryImpl(get(), get(), get()) }
            single { UserService(get(), get()) }
            single { PetFosterPlacementService(get(), get(), get(), get()) }
            single { PetsValidationService() }
        }
    )

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        createTestUsers()
    }

    // User 1: RESCUER, owns the test pet. User 2: RESCUER, non-owner (also doubles as a valid
    // user who is NOT an active temporal home, for the "invalid target" case). User 3: ADMIN.
    // User 4: TEMPORAL_HOME role + a backing TemporalHomes row (createPlacement's capacity check
    // reads via temporalHomeRepository.getTemporalHome, not just the role).
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

            try {
                Users.insert {
                    it[Users.id] = 4
                    it[Users.username] = "temporalhome@test.com"
                    it[Users.displayName] = "Test Temporal Home"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 4
                    it[UserActiveRoles.role] = "TEMPORAL_HOME"
                }
                TemporalHomes.insert {
                    it[TemporalHomes.userId] = 4
                    it[TemporalHomes.alias] = "Casa Feliz"
                    it[TemporalHomes.country] = Country.fromDisplayName("United States")!!
                    it[TemporalHomes.state] = "CA"
                    it[TemporalHomes.city] = "LA"
                    it[TemporalHomes.createdAt] = clock.now().toEpochMilliseconds()
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
                it[Pets.breed] = "Test breed"
                it[Pets.status] = "AVAILABLE"
                it[Pets.size] = "MEDIUM"
                it[Pets.isUrgent] = false
                it[Pets.country] = Country.fromDisplayName("United States")
                it[Pets.createdAt] = clock.now().toEpochMilliseconds()
            } get Pets.id
        }
    }

    private fun createRequestJson(temporalHomeId: Int = 4, notes: String? = null) =
        JsonSupport.objectMapper.writeValueAsString(CreateFosterPlacementRequest(temporalHomeId = temporalHomeId, notes = notes))

    // ==================== 401 (no session) ====================

    @Test
    fun `POST foster-placements returns 401 when no session`() {
        val handle = startServer()
        try {
            val petId = createPetInDb()

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/foster-placements",
                createRequestJson()
            )

            assertEquals(401, response.statusCode())
            assertTrue(response.body().contains("Unauthorized"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET foster-placements returns 401 when no session`() {
        val handle = startServer()
        try {
            val petId = createPetInDb()

            val response = TestHttp.get("${handle.baseUrl}/api/pets/$petId/foster-placements")

            assertEquals(401, response.statusCode())
            assertTrue(response.body().contains("Unauthorized"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT foster-placements end returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.put("${handle.baseUrl}/api/pets/foster-placements/1/end")

            assertEquals(401, response.statusCode())
            assertTrue(response.body().contains("Unauthorized"))
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/pets/{id}/foster-placements ====================

    @Test
    fun `POST foster-placements succeeds for the owning rescuer and shows up in GET history`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1)
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val createResponse = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/foster-placements",
                createRequestJson(temporalHomeId = 4, notes = "Trial run"),
                cookie
            )

            assertEquals(200, createResponse.statusCode())
            val createBody = createResponse.body()
            assertTrue(createBody.contains("\"temporalHomeId\": 4"))
            assertTrue(createBody.contains("Casa Feliz"))

            val historyResponse = TestHttp.get("${handle.baseUrl}/api/pets/$petId/foster-placements", cookie)

            assertEquals(200, historyResponse.statusCode())
            assertTrue(historyResponse.body().contains("Casa Feliz"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST foster-placements returns Forbidden for a non-owning rescuer`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1)
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/foster-placements",
                createRequestJson(),
                cookie
            )

            assertEquals(403, response.statusCode())
            assertTrue(response.body().contains("Forbidden"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST foster-placements succeeds for ADMIN on a pet they do not own`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1)
            val cookie = TestHttp.loginAs(handle.baseUrl, 3)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/foster-placements",
                createRequestJson(),
                cookie
            )

            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST foster-placements returns an error when the target user is not an active temporal home`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1)
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            // User 2 exists but only has the RESCUER role, not TEMPORAL_HOME.
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/foster-placements",
                createRequestJson(temporalHomeId = 2),
                cookie
            )

            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("not an active temporal home"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST foster-placements returns an error when the pet already has an active placement`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1)
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)
            val requestJson = createRequestJson()

            val firstResponse = TestHttp.postJson("${handle.baseUrl}/api/pets/$petId/foster-placements", requestJson, cookie)
            assertEquals(200, firstResponse.statusCode())

            val secondResponse = TestHttp.postJson("${handle.baseUrl}/api/pets/$petId/foster-placements", requestJson, cookie)

            assertEquals(400, secondResponse.statusCode())
            assertTrue(secondResponse.body().contains("already has an active foster placement"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST foster-placements returns 400 for a non-numeric pet id`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/not-a-number/foster-placements",
                createRequestJson(),
                cookie
            )

            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Invalid ID"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST foster-placements returns 404 for a non-existent pet`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/9999/foster-placements",
                createRequestJson(),
                cookie
            )

            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/pets/{id}/foster-placements ====================

    @Test
    fun `GET foster-placements returns Forbidden for a non-owner`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1)
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val response = TestHttp.get("${handle.baseUrl}/api/pets/$petId/foster-placements", cookie)

            assertEquals(403, response.statusCode())
            assertTrue(response.body().contains("Forbidden"))
        } finally {
            handle.stop()
        }
    }

    // ==================== PUT /api/pets/foster-placements/{id}/end ====================

    @Test
    fun `PUT foster-placements end succeeds for the owning rescuer and rejects ending it again`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1)
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val createResponse = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/foster-placements",
                createRequestJson(),
                cookie
            )
            val placementId = JsonSupport.objectMapper.readTree(createResponse.body())["id"].asInt()

            val endResponse = TestHttp.put("${handle.baseUrl}/api/pets/foster-placements/$placementId/end", cookie)
            assertEquals(200, endResponse.statusCode())

            val secondEndResponse = TestHttp.put("${handle.baseUrl}/api/pets/foster-placements/$placementId/end", cookie)
            assertEquals(400, secondEndResponse.statusCode())
            assertTrue(secondEndResponse.body().contains("already ended"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT foster-placements end returns Forbidden for a non-owner`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1)
            val ownerCookie = TestHttp.loginAs(handle.baseUrl, 1)
            val otherCookie = TestHttp.loginAs(handle.baseUrl, 2)

            val createResponse = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/foster-placements",
                createRequestJson(),
                ownerCookie
            )
            val placementId = JsonSupport.objectMapper.readTree(createResponse.body())["id"].asInt()

            val response = TestHttp.put("${handle.baseUrl}/api/pets/foster-placements/$placementId/end", otherCookie)

            assertEquals(403, response.statusCode())
            assertTrue(response.body().contains("Forbidden"))
        } finally {
            handle.stop()
        }
    }
}

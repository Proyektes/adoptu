package com.adoptu.routes

import com.adoptu.adapters.db.Pets
import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.Volunteers
import com.adoptu.adapters.db.repositories.PetEditSuggestionRepositoryImpl
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.adapters.db.repositories.VolunteerRepositoryImpl
import com.adoptu.dto.input.CreatePetEditSuggestionRequest
import com.adoptu.mocks.TestDatabase
import com.adoptu.ports.PetEditSuggestionRepositoryPort
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.ports.PhotographerRepositoryPort
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.ports.VolunteerRepositoryPort
import com.adoptu.services.PetEditSuggestionService
import com.adoptu.services.UserService
import com.adoptu.services.VolunteerService
import com.adoptu.services.validation.UsersValidationService
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

// Fixtures: user 1 = RESCUER (owns the test pet), user 2 = ADOPTER acting as the active volunteer
// who submits suggestions, user 3 = ADMIN, user 4 = a second RESCUER with no relationship to the
// pet (used to prove ownership is actually enforced, not just "any rescuer").
@OptIn(ExperimentalTime::class)
class PetEditSuggestionRoutesE2ETest {

    private val clock = Clock.System

    private val testModules = listOf(
        module {
            single<Clock> { Clock.System }
            single<UserRepositoryPort> { UserRepository(get()) }
            single<PhotographerRepositoryPort> { PhotographerRepositoryImpl(get(), get(), get()) }
            single { UserService(get(), get(), get()) }
            single { UsersValidationService() }
            single<PetRepositoryPort> { PetRepositoryImpl(get()) }
            single<VolunteerRepositoryPort> { VolunteerRepositoryImpl(get(), get()) }
            single { VolunteerService(get(), get()) }
            single<PetEditSuggestionRepositoryPort> { PetEditSuggestionRepositoryImpl(get(), get(), get()) }
            single { PetEditSuggestionService(get(), get(), get(), get()) }
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
            fun user(id: Int, username: String) {
                try {
                    Users.insert {
                        it[Users.id] = id
                        it[Users.username] = username
                        it[Users.displayName] = username
                        it[Users.createdAt] = clock.now().toEpochMilliseconds()
                    }
                } catch (e: Exception) { }
            }
            fun role(userId: Int, role: String) {
                try {
                    UserActiveRoles.insert {
                        it[UserActiveRoles.userId] = userId
                        it[UserActiveRoles.role] = role
                    }
                } catch (e: Exception) { }
            }

            user(1, "rescuer@test.com")
            role(1, "RESCUER")

            user(2, "adopter@test.com")
            role(2, "ADOPTER")

            user(3, "admin@test.com")
            role(3, "ADMIN")

            user(4, "otherrescuer@test.com")
            role(4, "RESCUER")
        }
    }

    private fun startServer() = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)

    private fun createPetInDb(rescuerId: Int = 1, name: String = "Buddy"): Int {
        return transaction {
            Pets.insert {
                it[Pets.rescuerId] = rescuerId
                it[Pets.name] = name
                it[Pets.type] = "DOG"
                it[Pets.description] = "Original description"
                it[Pets.weight] = BigDecimal("10.0")
                it[Pets.ageYears] = 2
                it[Pets.ageMonths] = 0
                it[Pets.sex] = "MALE"
                it[Pets.breed] = "Test breed"
                it[Pets.status] = "AVAILABLE"
                it[Pets.size] = "MEDIUM"
                it[Pets.isUrgent] = false
                it[Pets.country] = com.adoptu.common.Country.fromDisplayName("United States")
                it[Pets.createdAt] = clock.now().toEpochMilliseconds()
            } get Pets.id
        }
    }

    private fun makeActiveVolunteer(rescuerId: Int, volunteerId: Int) {
        transaction {
            Volunteers.insert {
                it[Volunteers.rescuerId] = rescuerId
                it[Volunteers.volunteerId] = volunteerId
                it[Volunteers.status] = "ACTIVE"
                it[Volunteers.createdAt] = clock.now().toEpochMilliseconds()
            }
        }
    }

    // ==================== POST /api/pets/{id}/edit-suggestions ====================

    @Test
    fun `POST edit-suggestions returns 401 when no session`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/edit-suggestions",
                JsonSupport.objectMapper.writeValueAsString(CreatePetEditSuggestionRequest(description = "New description"))
            )

            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST edit-suggestions succeeds for an active volunteer of the pet's rescuer`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1)
            makeActiveVolunteer(rescuerId = 1, volunteerId = 2)
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/edit-suggestions",
                JsonSupport.objectMapper.writeValueAsString(CreatePetEditSuggestionRequest(description = "New description")),
                cookie
            )

            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("New description"))
            assertTrue(body.contains("PENDING"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST edit-suggestions returns 400 for an invalid pet id`() {
        val handle = startServer()
        try {
            makeActiveVolunteer(rescuerId = 1, volunteerId = 2)
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/not-a-number/edit-suggestions",
                JsonSupport.objectMapper.writeValueAsString(CreatePetEditSuggestionRequest(description = "New description")),
                cookie
            )

            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST edit-suggestions returns 403 for a user who is not an active volunteer for the pet's rescuer`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1)
            // user 2 has no volunteer relationship with rescuer 1 at all.
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/edit-suggestions",
                JsonSupport.objectMapper.writeValueAsString(CreatePetEditSuggestionRequest(description = "New description")),
                cookie
            )

            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== PUT /api/pets/edit-suggestions/{id}/status ====================

    @Test
    fun `PUT edit-suggestions status returns 401 when no session`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1)
            makeActiveVolunteer(rescuerId = 1, volunteerId = 2)
            val volunteerCookie = TestHttp.loginAs(handle.baseUrl, 2)
            val created = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/edit-suggestions",
                JsonSupport.objectMapper.writeValueAsString(CreatePetEditSuggestionRequest(description = "New description")),
                volunteerCookie
            )
            val suggestionId = extractId(created.body())

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/pets/edit-suggestions/$suggestionId/status",
                """{"status":"APPROVED"}"""
            )

            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT edit-suggestions status succeeds for the pet's owning rescuer`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1)
            makeActiveVolunteer(rescuerId = 1, volunteerId = 2)
            val volunteerCookie = TestHttp.loginAs(handle.baseUrl, 2)
            val created = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/edit-suggestions",
                JsonSupport.objectMapper.writeValueAsString(CreatePetEditSuggestionRequest(description = "New description")),
                volunteerCookie
            )
            val suggestionId = extractId(created.body())

            val ownerCookie = TestHttp.loginAs(handle.baseUrl, 1)
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/pets/edit-suggestions/$suggestionId/status",
                """{"status":"APPROVED"}""",
                ownerCookie
            )

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("APPROVED"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT edit-suggestions status succeeds for an ADMIN who does not own the pet`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1)
            makeActiveVolunteer(rescuerId = 1, volunteerId = 2)
            val volunteerCookie = TestHttp.loginAs(handle.baseUrl, 2)
            val created = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/edit-suggestions",
                JsonSupport.objectMapper.writeValueAsString(CreatePetEditSuggestionRequest(description = "New description")),
                volunteerCookie
            )
            val suggestionId = extractId(created.body())

            val adminCookie = TestHttp.loginAs(handle.baseUrl, 3)
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/pets/edit-suggestions/$suggestionId/status",
                """{"status":"REJECTED"}""",
                adminCookie
            )

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("REJECTED"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT edit-suggestions status is forbidden for a rescuer who does not own the pet`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1)
            makeActiveVolunteer(rescuerId = 1, volunteerId = 2)
            val volunteerCookie = TestHttp.loginAs(handle.baseUrl, 2)
            val created = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/edit-suggestions",
                JsonSupport.objectMapper.writeValueAsString(CreatePetEditSuggestionRequest(description = "New description")),
                volunteerCookie
            )
            val suggestionId = extractId(created.body())

            val otherRescuerCookie = TestHttp.loginAs(handle.baseUrl, 4)
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/pets/edit-suggestions/$suggestionId/status",
                """{"status":"APPROVED"}""",
                otherRescuerCookie
            )

            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT edit-suggestions status returns 400 for an unknown status value`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1)
            makeActiveVolunteer(rescuerId = 1, volunteerId = 2)
            val volunteerCookie = TestHttp.loginAs(handle.baseUrl, 2)
            val created = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/edit-suggestions",
                JsonSupport.objectMapper.writeValueAsString(CreatePetEditSuggestionRequest(description = "New description")),
                volunteerCookie
            )
            val suggestionId = extractId(created.body())

            val ownerCookie = TestHttp.loginAs(handle.baseUrl, 1)
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/pets/edit-suggestions/$suggestionId/status",
                """{"status":"BOGUS"}""",
                ownerCookie
            )

            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // Different shape from the "session user that does not exist" cases in
    // UserShelterRoutesE2ETest/UserSterilizationLocationRoutesE2ETest: those routes need a
    // DB-sourced displayName AuthPrincipal doesn't carry, and deliberately treat "no such user" as
    // unauthenticated (401) as part of that lookup. This route has no such lookup on the happy
    // path - currentPrincipal() resolves fine even for a userId with no DB row (TestServer's
    // /test/login/{userId} mints a real, empty-roles AuthUser for exactly this case - see its own
    // doc comment), so the request reaches validationService.validateUserById(), which does its
    // own separate DB query and correctly falls into ServiceResult.NotFound -> 404.
    @Test
    fun `PUT edit-suggestions status returns 404 for a session user that does not exist`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1)
            makeActiveVolunteer(rescuerId = 1, volunteerId = 2)
            val volunteerCookie = TestHttp.loginAs(handle.baseUrl, 2)
            val created = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/edit-suggestions",
                JsonSupport.objectMapper.writeValueAsString(CreatePetEditSuggestionRequest(description = "New description")),
                volunteerCookie
            )
            val suggestionId = extractId(created.body())

            val ghostCookie = TestHttp.loginAs(handle.baseUrl, 9999)
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/pets/edit-suggestions/$suggestionId/status",
                """{"status":"APPROVED"}""",
                ghostCookie
            )

            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/users/rescuer/edit-suggestions ====================

    @Test
    fun `GET rescuer edit-suggestions returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/users/rescuer/edit-suggestions")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET rescuer edit-suggestions returns the pending suggestions for the caller's own pets`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1, name = "Rex")
            makeActiveVolunteer(rescuerId = 1, volunteerId = 2)
            val volunteerCookie = TestHttp.loginAs(handle.baseUrl, 2)
            TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/edit-suggestions",
                JsonSupport.objectMapper.writeValueAsString(CreatePetEditSuggestionRequest(description = "New description")),
                volunteerCookie
            )

            val ownerCookie = TestHttp.loginAs(handle.baseUrl, 1)
            val response = TestHttp.get("${handle.baseUrl}/api/users/rescuer/edit-suggestions", ownerCookie)

            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("Rex"))
            assertTrue(body.contains("PENDING"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET rescuer edit-suggestions returns 404 for a session user that does not exist`() {
        val handle = startServer()
        try {
            val ghostCookie = TestHttp.loginAs(handle.baseUrl, 9999)
            val response = TestHttp.get("${handle.baseUrl}/api/users/rescuer/edit-suggestions", ghostCookie)

            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET rescuer edit-suggestions excludes suggestions already reviewed`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1)
            makeActiveVolunteer(rescuerId = 1, volunteerId = 2)
            val volunteerCookie = TestHttp.loginAs(handle.baseUrl, 2)
            val created = TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/edit-suggestions",
                JsonSupport.objectMapper.writeValueAsString(CreatePetEditSuggestionRequest(description = "New description")),
                volunteerCookie
            )
            val suggestionId = extractId(created.body())

            val ownerCookie = TestHttp.loginAs(handle.baseUrl, 1)
            TestHttp.putJson(
                "${handle.baseUrl}/api/pets/edit-suggestions/$suggestionId/status",
                """{"status":"APPROVED"}""",
                ownerCookie
            )

            val response = TestHttp.get("${handle.baseUrl}/api/users/rescuer/edit-suggestions", ownerCookie)

            assertEquals(200, response.statusCode())
            assertEquals("[]", response.body())
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/users/volunteer/edit-suggestions ====================

    @Test
    fun `GET volunteer edit-suggestions returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/users/volunteer/edit-suggestions")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET volunteer edit-suggestions returns suggestions the caller submitted`() {
        val handle = startServer()
        try {
            val petId = createPetInDb(rescuerId = 1, name = "Milo")
            makeActiveVolunteer(rescuerId = 1, volunteerId = 2)
            val volunteerCookie = TestHttp.loginAs(handle.baseUrl, 2)
            TestHttp.postJson(
                "${handle.baseUrl}/api/pets/$petId/edit-suggestions",
                JsonSupport.objectMapper.writeValueAsString(CreatePetEditSuggestionRequest(description = "New description")),
                volunteerCookie
            )

            val response = TestHttp.get("${handle.baseUrl}/api/users/volunteer/edit-suggestions", volunteerCookie)

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Milo"))
        } finally {
            handle.stop()
        }
    }

    // ==================== Helper ====================

    private fun extractId(body: String): Int {
        val node = JsonSupport.objectMapper.readTree(body)
        return node.get("id").asInt()
    }
}

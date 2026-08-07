package com.adoptu.routes

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.Volunteers
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.adapters.db.repositories.VolunteerRepositoryImpl
import com.adoptu.dto.input.CreateVolunteerApplicationRequest
import com.adoptu.dto.input.UpdateVolunteerStatusRequest
import com.adoptu.dto.input.VolunteerStatus
import com.adoptu.mocks.TestDatabase
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.ports.PhotographerRepositoryPort
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.ports.VolunteerRepositoryPort
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class VolunteerRoutesE2ETest {

    private val clock = Clock.System

    private val testModules = listOf(
        module {
            single<Clock> { Clock.System }
            single<PetRepositoryPort> { PetRepositoryImpl(get()) }
            single<UserRepositoryPort> { UserRepository(get()) }
            single<PhotographerRepositoryPort> { PhotographerRepositoryImpl(get(), get(), get()) }
            single { UserService(get(), get()) }
            single<VolunteerRepositoryPort> { VolunteerRepositoryImpl(get(), get()) }
            single { VolunteerService(get(), get()) }
            single { UsersValidationService() }
        }
    )

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        createTestUsers()
    }

    // id 1: RESCUER (receives applications), id 2: plain ADOPTER-role volunteer (applies),
    // id 3: ADMIN.
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
                    it[Users.username] = "volunteer@test.com"
                    it[Users.displayName] = "Test Volunteer"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 2
                    it[UserActiveRoles.role] = "ADOPTER"
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

    private fun createApplicationInDb(rescuerId: Int, volunteerId: Int, status: VolunteerStatus = VolunteerStatus.PENDING): Int {
        return transaction {
            Volunteers.insert {
                it[Volunteers.rescuerId] = rescuerId
                it[Volunteers.volunteerId] = volunteerId
                it[Volunteers.status] = status.name
                it[Volunteers.createdAt] = clock.now().toEpochMilliseconds()
            } get Volunteers.id
        }
    }

    // ==================== POST /api/volunteers ====================

    @Test
    fun `POST volunteers returns 401 when no session`() {
        val handle = startServer()
        try {
            val request = CreateVolunteerApplicationRequest(rescuerId = 1)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/volunteers",
                JsonSupport.objectMapper.writeValueAsString(request)
            )

            assertEquals(401, response.statusCode())
            assertTrue(response.body().contains("Unauthorized"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST volunteers succeeds for an authenticated user applying to a rescuer`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val request = CreateVolunteerApplicationRequest(rescuerId = 1)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/volunteers",
                JsonSupport.objectMapper.writeValueAsString(request),
                cookie
            )

            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("\"rescuerId\": 1"))
            assertTrue(body.contains("\"volunteerId\": 2"))
            assertTrue(body.contains("PENDING"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST volunteers returns 400 when volunteering for yourself`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val request = CreateVolunteerApplicationRequest(rescuerId = 1)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/volunteers",
                JsonSupport.objectMapper.writeValueAsString(request),
                cookie
            )

            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== PUT /api/volunteers/{id}/status ====================

    @Test
    fun `PUT volunteers status returns 401 when no session`() {
        val handle = startServer()
        try {
            val id = createApplicationInDb(rescuerId = 1, volunteerId = 2)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/volunteers/$id/status",
                JsonSupport.objectMapper.writeValueAsString(UpdateVolunteerStatusRequest(status = VolunteerStatus.ACTIVE))
            )

            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT volunteers status succeeds for the target rescuer`() {
        val handle = startServer()
        try {
            val id = createApplicationInDb(rescuerId = 1, volunteerId = 2)
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/volunteers/$id/status",
                JsonSupport.objectMapper.writeValueAsString(UpdateVolunteerStatusRequest(status = VolunteerStatus.ACTIVE)),
                cookie
            )

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("ACTIVE"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT volunteers status succeeds for an admin`() {
        val handle = startServer()
        try {
            val id = createApplicationInDb(rescuerId = 1, volunteerId = 2)
            val cookie = TestHttp.loginAs(handle.baseUrl, 3)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/volunteers/$id/status",
                JsonSupport.objectMapper.writeValueAsString(UpdateVolunteerStatusRequest(status = VolunteerStatus.REJECTED)),
                cookie
            )

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("REJECTED"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT volunteers status returns 403 for an unrelated user`() {
        val handle = startServer()
        try {
            val id = createApplicationInDb(rescuerId = 1, volunteerId = 2)
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/volunteers/$id/status",
                JsonSupport.objectMapper.writeValueAsString(UpdateVolunteerStatusRequest(status = VolunteerStatus.ACTIVE)),
                cookie
            )

            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT volunteers status returns 400 for an invalid id`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/volunteers/not-a-number/status",
                JsonSupport.objectMapper.writeValueAsString(UpdateVolunteerStatusRequest(status = VolunteerStatus.ACTIVE)),
                cookie
            )

            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Invalid id"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/users/volunteer/applications ====================

    @Test
    fun `GET users volunteer applications returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/users/volunteer/applications")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET users volunteer applications returns the caller's own submitted applications`() {
        createApplicationInDb(rescuerId = 1, volunteerId = 2)

        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val response = TestHttp.get("${handle.baseUrl}/api/users/volunteer/applications", cookie)

            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("\"rescuerId\": 1"))
            assertTrue(body.contains("\"volunteerId\": 2"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET users volunteer applications returns empty list when caller has none`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3)

            val response = TestHttp.get("${handle.baseUrl}/api/users/volunteer/applications", cookie)

            assertEquals(200, response.statusCode())
            assertEquals("[]", response.body())
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/users/rescuer/volunteers ====================

    @Test
    fun `GET users rescuer volunteers returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/users/rescuer/volunteers")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET users rescuer volunteers returns applications submitted to the caller as rescuer`() {
        createApplicationInDb(rescuerId = 1, volunteerId = 2)

        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.get("${handle.baseUrl}/api/users/rescuer/volunteers", cookie)

            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("\"rescuerId\": 1"))
            assertTrue(body.contains("\"volunteerId\": 2"))
        } finally {
            handle.stop()
        }
    }

    // ==================== Lifecycle: apply -> list as volunteer -> list as rescuer -> approve ====================

    @Test
    fun `full lifecycle - apply, list as volunteer, list as rescuer, then approve`() {
        val handle = startServer()
        try {
            val volunteerCookie = TestHttp.loginAs(handle.baseUrl, 2)
            val rescuerCookie = TestHttp.loginAs(handle.baseUrl, 1)

            val applyResponse = TestHttp.postJson(
                "${handle.baseUrl}/api/volunteers",
                JsonSupport.objectMapper.writeValueAsString(CreateVolunteerApplicationRequest(rescuerId = 1)),
                volunteerCookie
            )
            assertEquals(200, applyResponse.statusCode())
            val id = JsonSupport.objectMapper.readTree(applyResponse.body()).get("id").asInt()

            val myApplications = TestHttp.get("${handle.baseUrl}/api/users/volunteer/applications", volunteerCookie)
            assertEquals(200, myApplications.statusCode())
            assertTrue(myApplications.body().contains("PENDING"))

            val rescuerVolunteers = TestHttp.get("${handle.baseUrl}/api/users/rescuer/volunteers", rescuerCookie)
            assertEquals(200, rescuerVolunteers.statusCode())
            assertTrue(rescuerVolunteers.body().contains("\"volunteerId\": 2"))

            val approveResponse = TestHttp.putJson(
                "${handle.baseUrl}/api/volunteers/$id/status",
                JsonSupport.objectMapper.writeValueAsString(UpdateVolunteerStatusRequest(status = VolunteerStatus.ACTIVE)),
                rescuerCookie
            )
            assertEquals(200, approveResponse.statusCode())
            assertTrue(approveResponse.body().contains("ACTIVE"))
        } finally {
            handle.stop()
        }
    }
}

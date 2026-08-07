package com.adoptu.routes

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.SavedSearchRepositoryImpl
import com.adoptu.dto.input.CreateSavedSearchRequest
import com.adoptu.mocks.TestDatabase
import com.adoptu.ports.SavedSearchRepositoryPort
import com.adoptu.services.SavedSearchService
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
class SavedSearchRoutesE2ETest {

    private val clock = Clock.System

    private val testModules = listOf(
        module {
            single<Clock> { Clock.System }
            single<SavedSearchRepositoryPort> { SavedSearchRepositoryImpl(get()) }
            single { SavedSearchService(get()) }
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
                    it[Users.username] = "adopter1@test.com"
                    it[Users.displayName] = "Adopter One"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 1
                    it[UserActiveRoles.role] = "ADOPTER"
                }
            } catch (e: Exception) { }

            try {
                Users.insert {
                    it[Users.id] = 2
                    it[Users.username] = "adopter2@test.com"
                    it[Users.displayName] = "Adopter Two"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 2
                    it[UserActiveRoles.role] = "ADOPTER"
                }
            } catch (e: Exception) { }
        }
    }

    private fun startServer() = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)

    // ==================== POST /api/saved-searches ====================

    @Test
    fun `POST saved-searches returns 401 when no session`() {
        val handle = startServer()
        try {
            val request = CreateSavedSearchRequest(type = "DOG", country = "United States")

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/saved-searches",
                JsonSupport.objectMapper.writeValueAsString(request)
            )

            assertEquals(401, response.statusCode())
            assertTrue(response.body().contains("Unauthorized"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST saved-searches creates saved search when authenticated`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val request = CreateSavedSearchRequest(type = "DOG", country = "United States")

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/saved-searches",
                JsonSupport.objectMapper.writeValueAsString(request),
                cookie
            )

            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("United States"))
            assertTrue(body.contains("\"userId\": 1"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST saved-searches returns 400 for invalid country`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val request = CreateSavedSearchRequest(type = "DOG", country = "Narnia")

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/saved-searches",
                JsonSupport.objectMapper.writeValueAsString(request),
                cookie
            )

            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Invalid country"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/saved-searches ====================

    @Test
    fun `GET saved-searches returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/saved-searches")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET saved-searches returns empty list initially`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.get("${handle.baseUrl}/api/saved-searches", cookie)

            assertEquals(200, response.statusCode())
            assertEquals("[]", response.body())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET saved-searches returns created saved search`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            TestHttp.postJson(
                "${handle.baseUrl}/api/saved-searches",
                JsonSupport.objectMapper.writeValueAsString(CreateSavedSearchRequest(type = "DOG", country = "United States")),
                cookie
            )

            val response = TestHttp.get("${handle.baseUrl}/api/saved-searches", cookie)

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("United States"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET saved-searches only returns the requesting user's searches`() {
        val handle = startServer()
        try {
            val cookie1 = TestHttp.loginAs(handle.baseUrl, 1)
            val cookie2 = TestHttp.loginAs(handle.baseUrl, 2)

            TestHttp.postJson(
                "${handle.baseUrl}/api/saved-searches",
                JsonSupport.objectMapper.writeValueAsString(CreateSavedSearchRequest(type = "DOG", country = "United States")),
                cookie1
            )

            val response = TestHttp.get("${handle.baseUrl}/api/saved-searches", cookie2)

            assertEquals(200, response.statusCode())
            assertEquals("[]", response.body())
        } finally {
            handle.stop()
        }
    }

    // ==================== DELETE /api/saved-searches/{id} ====================

    @Test
    fun `DELETE saved-searches returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.delete("${handle.baseUrl}/api/saved-searches/1")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE saved-searches returns 404 for nonexistent id`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.delete("${handle.baseUrl}/api/saved-searches/9999", cookie)

            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE saved-searches deletes own saved search`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val createResponse = TestHttp.postJson(
                "${handle.baseUrl}/api/saved-searches",
                JsonSupport.objectMapper.writeValueAsString(CreateSavedSearchRequest(type = "DOG", country = "United States")),
                cookie
            )
            val createdId = JsonSupport.objectMapper.readTree(createResponse.body())["id"].asInt()

            val response = TestHttp.delete("${handle.baseUrl}/api/saved-searches/$createdId", cookie)

            assertEquals(200, response.statusCode())

            val followUp = TestHttp.get("${handle.baseUrl}/api/saved-searches", cookie)
            assertEquals("[]", followUp.body())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE saved-searches returns 403 for another user's saved search`() {
        val handle = startServer()
        try {
            val ownerCookie = TestHttp.loginAs(handle.baseUrl, 1)
            val otherCookie = TestHttp.loginAs(handle.baseUrl, 2)

            val createResponse = TestHttp.postJson(
                "${handle.baseUrl}/api/saved-searches",
                JsonSupport.objectMapper.writeValueAsString(CreateSavedSearchRequest(type = "DOG", country = "United States")),
                ownerCookie
            )
            val createdId = JsonSupport.objectMapper.readTree(createResponse.body())["id"].asInt()

            val response = TestHttp.delete("${handle.baseUrl}/api/saved-searches/$createdId", otherCookie)

            assertEquals(403, response.statusCode())

            val followUp = TestHttp.get("${handle.baseUrl}/api/saved-searches", ownerCookie)
            assertTrue(followUp.body().contains("United States"))
        } finally {
            handle.stop()
        }
    }
}

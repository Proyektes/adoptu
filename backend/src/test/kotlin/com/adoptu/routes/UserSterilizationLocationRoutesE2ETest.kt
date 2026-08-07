package com.adoptu.routes

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.UserSterilizationLocations
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.adapters.db.repositories.UserSterilizationLocationRepository
import com.adoptu.dto.input.CreateUserSterilizationLocationRequest
import com.adoptu.dto.input.UpdateUserSterilizationLocationRequest
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestDatabase
import com.adoptu.ports.NotificationPort
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.ports.UserSterilizationLocationRepositoryPort
import com.adoptu.services.ProfileEmailVerificationService
import com.adoptu.services.UserSterilizationLocationService
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
class UserSterilizationLocationRoutesE2ETest {

    private val clock = Clock.System

    private val testModules = listOf(
        module {
            single<Clock> { Clock.System }
            single { MockNotificationAdapter() }
            single<NotificationPort> { get<MockNotificationAdapter>() }
            single<UserRepositoryPort> { UserRepository(get()) }
            single { ProfileEmailVerificationService(get(), get(), get()) }
            single<UserSterilizationLocationRepositoryPort> { UserSterilizationLocationRepository(get()) }
            single { UserSterilizationLocationService(get(), get()) }
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
                    it[Users.username] = "other@test.com"
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
                    it[Users.username] = "search@test.com"
                    it[Users.displayName] = "Search Owner"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                }
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = 3
                    it[UserActiveRoles.role] = "RESCUER"
                }
            } catch (e: Exception) { }
        }
    }

    private fun createLocationInDb(userId: Int, country: String = "United States", state: String? = "CA", city: String = "LA") {
        transaction {
            val now = clock.now().toEpochMilliseconds()
            UserSterilizationLocations.insert {
                it[UserSterilizationLocations.userId] = userId
                it[UserSterilizationLocations.name] = "Location $userId"
                it[UserSterilizationLocations.country] = com.adoptu.common.Country.fromDisplayName(country)!!
                it[UserSterilizationLocations.state] = state
                it[UserSterilizationLocations.city] = city
                it[UserSterilizationLocations.address] = "123 Main St"
                it[UserSterilizationLocations.createdAt] = now
                it[UserSterilizationLocations.updatedAt] = now
            }
            try {
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = userId
                    it[UserActiveRoles.role] = "STERILIZATION_SERVICE"
                }
            } catch (e: Exception) { }
        }
    }

    // ==================== POST /api/users/sterilization-location ====================

    @Test
    fun `POST users sterilization-location returns 401 when no session`() {
        val handle = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)
        try {
            val request = CreateUserSterilizationLocationRequest(
                name = "My Location",
                country = "United States",
                city = "LA",
                address = "123 Main St"
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/sterilization-location",
                JsonSupport.objectMapper.writeValueAsString(request)
            )

            assertEquals(401, response.statusCode())
            assertTrue(response.body().contains("Unauthorized"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST users sterilization-location creates location when authenticated`() {
        val handle = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val request = CreateUserSterilizationLocationRequest(
                name = "My Location",
                country = "United States",
                city = "LA",
                address = "123 Main St"
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/sterilization-location",
                JsonSupport.objectMapper.writeValueAsString(request),
                cookie
            )

            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("My Location"))
            assertTrue(body.contains("\"userId\": 1"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST users sterilization-location twice updates existing location instead of failing`() {
        val handle = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val firstRequest = CreateUserSterilizationLocationRequest(
                name = "First Name",
                country = "United States",
                city = "LA",
                address = "123 Main St"
            )
            TestHttp.postJson(
                "${handle.baseUrl}/api/users/sterilization-location",
                JsonSupport.objectMapper.writeValueAsString(firstRequest),
                cookie
            )

            val secondRequest = CreateUserSterilizationLocationRequest(
                name = "Second Name",
                country = "United States",
                city = "LA",
                address = "456 Other St"
            )
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/sterilization-location",
                JsonSupport.objectMapper.writeValueAsString(secondRequest),
                cookie
            )

            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("Second Name"))
        } finally {
            handle.stop()
        }
    }

    // Was "returns 500" pre-AuthKit-cutover: the old HMAC session cookie was accepted purely on
    // signature, so a nonexistent user id reached service.create() and blew up on a DB FK
    // constraint. currentPrincipal() now resolves a displayName via a DB lookup before that point
    // (AuthPrincipal carries no displayName - see UserSterilizationLocationRoutes.kt) and treats
    // "no such user" as unauthenticated, which is the more correct read of a principal whose
    // backing row is gone.
    @Test
    fun `POST users sterilization-location returns 401 for session user that does not exist`() {
        val handle = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val request = CreateUserSterilizationLocationRequest(
                name = "My Location",
                country = "United States",
                city = "LA",
                address = "123 Main St"
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/sterilization-location",
                JsonSupport.objectMapper.writeValueAsString(request),
                cookie
            )

            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST users sterilization-location returns 400 for blank name`() {
        val handle = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val request = CreateUserSterilizationLocationRequest(
                name = "",
                country = "United States",
                city = "LA",
                address = "123 Main St"
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/sterilization-location",
                JsonSupport.objectMapper.writeValueAsString(request),
                cookie
            )

            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Name is required"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/users/sterilization-location ====================

    @Test
    fun `GET users sterilization-location returns 401 when no session`() {
        val handle = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/users/sterilization-location")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET users sterilization-location returns 404 when not found`() {
        val handle = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.get("${handle.baseUrl}/api/users/sterilization-location", cookie)

            assertEquals(404, response.statusCode())
            assertTrue(response.body().contains("Sterilization location not found"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET users sterilization-location returns location when it exists`() {
        createLocationInDb(1)

        val handle = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.get("${handle.baseUrl}/api/users/sterilization-location", cookie)

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Location 1"))
        } finally {
            handle.stop()
        }
    }

    // ==================== PUT /api/users/sterilization-location ====================

    @Test
    fun `PUT users sterilization-location returns 401 when no session`() {
        val handle = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/sterilization-location",
                JsonSupport.objectMapper.writeValueAsString(UpdateUserSterilizationLocationRequest(name = "New"))
            )

            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT users sterilization-location returns 404 when location does not exist`() {
        val handle = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/sterilization-location",
                JsonSupport.objectMapper.writeValueAsString(UpdateUserSterilizationLocationRequest(name = "New")),
                cookie
            )

            assertEquals(404, response.statusCode())
            assertTrue(response.body().contains("Not found"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT users sterilization-location updates location when it exists`() {
        createLocationInDb(1)

        val handle = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/sterilization-location",
                JsonSupport.objectMapper.writeValueAsString(UpdateUserSterilizationLocationRequest(name = "Updated Location")),
                cookie
            )

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Updated Location"))
        } finally {
            handle.stop()
        }
    }

    // ==================== DELETE /api/users/sterilization-location ====================

    @Test
    fun `DELETE users sterilization-location returns 401 when no session`() {
        val handle = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.delete("${handle.baseUrl}/api/users/sterilization-location")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE users sterilization-location returns 404 when location does not exist`() {
        val handle = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.delete("${handle.baseUrl}/api/users/sterilization-location", cookie)

            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE users sterilization-location deletes location when it exists`() {
        createLocationInDb(1)

        val handle = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.delete("${handle.baseUrl}/api/users/sterilization-location", cookie)

            assertEquals(200, response.statusCode())

            val followUp = TestHttp.get("${handle.baseUrl}/api/users/sterilization-location", cookie)
            assertEquals(404, followUp.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/user-sterilization-locations ====================

    @Test
    fun `GET user-sterilization-locations returns 400 when country is missing`() {
        val handle = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/user-sterilization-locations")
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Country is required"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET user-sterilization-locations returns 400 when country is blank`() {
        val handle = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/user-sterilization-locations?country=")
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET user-sterilization-locations returns matching locations`() {
        createLocationInDb(3, country = "United States", state = "CA", city = "LA")

        val handle = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/user-sterilization-locations?country=United%20States&state=CA&city=LA")
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Location 3"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET user-sterilization-locations returns empty list for non-matching country`() {
        createLocationInDb(3, country = "United States")

        val handle = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/user-sterilization-locations?country=Canada")
            assertEquals(200, response.statusCode())
            assertEquals("[]", response.body())
        } finally {
            handle.stop()
        }
    }
}

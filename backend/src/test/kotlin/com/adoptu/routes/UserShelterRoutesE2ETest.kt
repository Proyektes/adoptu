package com.adoptu.routes

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.UserShelters
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.adapters.db.repositories.UserShelterRepository
import com.adoptu.dto.input.CreateUserShelterRequest
import com.adoptu.dto.input.UpdateUserShelterRequest
import com.adoptu.dto.input.UserShelterDto
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestDatabase
import com.adoptu.ports.NotificationPort
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.ports.UserShelterRepositoryPort
import com.adoptu.services.ProfileEmailVerificationService
import com.adoptu.services.UserShelterService
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
class UserShelterRoutesE2ETest {

    private val clock = Clock.System

    // Fault-injection wrapper used by a single test below to force the route's generic
    // `catch (e: Exception)` (500) branch - every legitimate business-rule failure reachable
    // through UserShelterService.create() surfaces as IllegalArgumentException (caught -> 400),
    // so the only way to genuinely exercise the 500 path is an unexpected repository failure.
    private class ThrowingUserShelterRepository(
        private val delegate: UserShelterRepositoryPort
    ) : UserShelterRepositoryPort by delegate {
        override suspend fun create(userId: Int, request: CreateUserShelterRequest, emailVerified: Boolean): UserShelterDto {
            throw RuntimeException("Unexpected database failure")
        }
    }

    private val testModules = listOf(
        module {
            single<Clock> { Clock.System }
            single { MockNotificationAdapter() }
            single<NotificationPort> { get<MockNotificationAdapter>() }
            single<UserRepositoryPort> { UserRepository(get()) }
            single { ProfileEmailVerificationService(get(), get(), get()) }
            single<UserShelterRepositoryPort> { UserShelterRepository(get()) }
            single { UserShelterService(get(), get()) }
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

    private fun startServer() = TestServer.start(modules = testModules, initDatabase = false, withTestLogin = true)

    private fun createShelterInDb(userId: Int, country: String = "United States", state: String? = "CA", city: String = "LA") {
        transaction {
            val now = clock.now().toEpochMilliseconds()
            UserShelters.insert {
                it[UserShelters.userId] = userId
                it[UserShelters.name] = "Shelter $userId"
                it[UserShelters.country] = com.adoptu.common.Country.fromDisplayName(country)!!
                it[UserShelters.state] = state
                it[UserShelters.city] = city
                it[UserShelters.address] = "123 Main St"
                it[UserShelters.currency] = "USD"
                it[UserShelters.createdAt] = now
                it[UserShelters.updatedAt] = now
            }
            try {
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = userId
                    it[UserActiveRoles.role] = "SHELTER"
                }
            } catch (e: Exception) { }
        }
    }

    // ==================== POST /api/users/shelter ====================

    @Test
    fun `POST users shelter returns 401 when no session`() {
        val handle = startServer()
        try {
            val request = CreateUserShelterRequest(
                name = "My Shelter",
                country = "United States",
                city = "LA",
                address = "123 Main St"
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/shelter",
                JsonSupport.objectMapper.writeValueAsString(request)
            )

            assertEquals(401, response.statusCode())
            assertTrue(response.body().contains("Unauthorized"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST users shelter creates shelter when authenticated`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val request = CreateUserShelterRequest(
                name = "My Shelter",
                country = "United States",
                city = "LA",
                address = "123 Main St"
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/shelter",
                JsonSupport.objectMapper.writeValueAsString(request),
                cookie
            )

            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("My Shelter"))
            assertTrue(body.contains("\"userId\": 1"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST users shelter twice updates existing shelter instead of failing`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val firstRequest = CreateUserShelterRequest(
                name = "First Name",
                country = "United States",
                city = "LA",
                address = "123 Main St"
            )
            TestHttp.postJson(
                "${handle.baseUrl}/api/users/shelter",
                JsonSupport.objectMapper.writeValueAsString(firstRequest),
                cookie
            )

            val secondRequest = CreateUserShelterRequest(
                name = "Second Name",
                country = "United States",
                city = "LA",
                address = "456 Other St"
            )
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/shelter",
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
    // (AuthPrincipal carries no displayName - see UserShelterRoutes.kt) and treats "no such user"
    // as unauthenticated, which is the more correct read of a principal whose backing row is gone.
    @Test
    fun `POST users shelter returns 401 for session user that does not exist`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val request = CreateUserShelterRequest(
                name = "My Shelter",
                country = "United States",
                city = "LA",
                address = "123 Main St"
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/shelter",
                JsonSupport.objectMapper.writeValueAsString(request),
                cookie
            )

            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST users shelter returns 400 for blank name`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val request = CreateUserShelterRequest(
                name = "",
                country = "United States",
                city = "LA",
                address = "123 Main St"
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/shelter",
                JsonSupport.objectMapper.writeValueAsString(request),
                cookie
            )

            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Name is required"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST users shelter returns 500 for an unexpected repository failure`() {
        val handle = TestServer.start(
            modules = listOf(
                module {
                    single<Clock> { Clock.System }
                    single { MockNotificationAdapter() }
                    single<NotificationPort> { get<MockNotificationAdapter>() }
                    single<UserRepositoryPort> { UserRepository(get()) }
                    single { ProfileEmailVerificationService(get(), get(), get()) }
                    single<UserShelterRepositoryPort> { ThrowingUserShelterRepository(UserShelterRepository(get())) }
                    single { UserShelterService(get(), get()) }
                }
            ),
            initDatabase = false,
            withTestLogin = true
        )
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val request = CreateUserShelterRequest(
                name = "My Shelter",
                country = "United States",
                city = "LA",
                address = "123 Main St"
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/shelter",
                JsonSupport.objectMapper.writeValueAsString(request),
                cookie
            )

            assertEquals(500, response.statusCode())
            assertTrue(response.body().contains("Unexpected database failure"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/users/shelter ====================

    @Test
    fun `GET users shelter returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/users/shelter")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET users shelter returns 404 when not found`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.get("${handle.baseUrl}/api/users/shelter", cookie)

            assertEquals(404, response.statusCode())
            assertTrue(response.body().contains("Shelter profile not found"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET users shelter returns shelter when it exists`() {
        createShelterInDb(1)

        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.get("${handle.baseUrl}/api/users/shelter", cookie)

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Shelter 1"))
        } finally {
            handle.stop()
        }
    }

    // ==================== PUT /api/users/shelter ====================

    @Test
    fun `PUT users shelter returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/shelter",
                JsonSupport.objectMapper.writeValueAsString(UpdateUserShelterRequest(name = "New"))
            )

            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // Mirrors `POST users shelter returns 401 for session user that does not exist` above - the
    // displayName lookup that guards this null-branch is duplicated per-handler in
    // UserShelterRoutes.kt (POST and PUT each resolve it independently), so each needs its own
    // test.
    @Test
    fun `PUT users shelter returns 401 for session user that does not exist`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/shelter",
                JsonSupport.objectMapper.writeValueAsString(UpdateUserShelterRequest(name = "New")),
                cookie
            )

            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT users shelter returns 404 when shelter does not exist`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/shelter",
                JsonSupport.objectMapper.writeValueAsString(UpdateUserShelterRequest(name = "New")),
                cookie
            )

            assertEquals(404, response.statusCode())
            assertTrue(response.body().contains("Not found"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT users shelter updates shelter when it exists`() {
        createShelterInDb(1)

        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/shelter",
                JsonSupport.objectMapper.writeValueAsString(UpdateUserShelterRequest(name = "Updated Shelter")),
                cookie
            )

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Updated Shelter"))
        } finally {
            handle.stop()
        }
    }

    // ==================== DELETE /api/users/shelter ====================

    @Test
    fun `DELETE users shelter returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.delete("${handle.baseUrl}/api/users/shelter")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE users shelter returns 404 when shelter does not exist`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.delete("${handle.baseUrl}/api/users/shelter", cookie)

            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE users shelter deletes shelter when it exists`() {
        createShelterInDb(1)

        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.delete("${handle.baseUrl}/api/users/shelter", cookie)

            assertEquals(200, response.statusCode())

            val followUp = TestHttp.get("${handle.baseUrl}/api/users/shelter", cookie)
            assertEquals(404, followUp.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/user-shelters ====================

    @Test
    fun `GET user-shelters returns 400 when country is missing`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/user-shelters")
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Country is required"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET user-shelters returns 400 when country is blank`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/user-shelters?country=")
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET user-shelters returns matching shelters`() {
        createShelterInDb(3, country = "United States", state = "CA", city = "LA")

        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/user-shelters?country=United%20States&state=CA&city=LA")
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Shelter 3"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET user-shelters returns empty list for non-matching country`() {
        createShelterInDb(3, country = "United States")

        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/user-shelters?country=Canada")
            assertEquals(200, response.statusCode())
            assertEquals("[]", response.body())
        } finally {
            handle.stop()
        }
    }
}

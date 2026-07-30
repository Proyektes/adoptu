package com.adoptu.routes

import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.UserPasswords
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.WebAuthnCredentials
import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.config.AppConfig
import com.adoptu.dto.input.AcceptTermsRequest
import com.adoptu.dto.input.BanUserRequest
import com.adoptu.dto.input.RoleActivationRequest
import com.adoptu.mocks.MockImageStorage
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestDatabase
import com.adoptu.ports.ImageStoragePort
import com.adoptu.ports.NotificationPort
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.ports.PhotographerRepositoryPort
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.services.EmailChangeService
import com.adoptu.services.PasswordService
import com.adoptu.services.PetService
import com.adoptu.services.PhotographerService
import com.adoptu.services.ProfileEmailVerificationService
import com.adoptu.services.UserService
import com.adoptu.services.auth.WebAuthnService
import com.adoptu.testsupport.TestHttp
import com.adoptu.testsupport.TestServer
import com.adoptu.testsupport.TestServerHandle
import com.adoptu.web.JsonSupport
import com.adoptu.web.SuccessResponse
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class UsersRoutesE2ETest {

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
                val rescuerId = Users.insert {
                    it[Users.id] = 1
                    it[Users.username] = "rescuer@test.com"
                    it[Users.displayName] = "Test Rescuer"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                } get Users.id
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = rescuerId
                    it[UserActiveRoles.role] = "RESCUER"
                }
            } catch (e: Exception) { }

            try {
                val adopterId = Users.insert {
                    it[Users.id] = 2
                    it[Users.username] = "adopter@test.com"
                    it[Users.displayName] = "Test Adopter"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                    // Verified so the temporal-home/photographer/shelter/sterilization
                    // "activates when authenticated" tests below can publish a profile -
                    // publishing now requires a verified account email.
                    it[Users.isEmailVerified] = true
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

            try {
                val bannableId = Users.insert {
                    it[Users.id] = 4
                    it[Users.username] = "bannable@test.com"
                    it[Users.displayName] = "Bannable User"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                } get Users.id
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = bannableId
                    it[UserActiveRoles.role] = "ADOPTER"
                }
            } catch (e: Exception) { }
        }
    }

    /** Verbatim port of the old Ktor test's inline `module { ... }` block of mocked adapters. */
    private fun buildTestModules(): List<Module> {
        val config = AppConfig.fromMap(mapOf("env" to "test", "admin.email" to "admin@adopt-u.com"))
        val mockNotificationAdapter = MockNotificationAdapter()

        return listOf(module {
            single { config }
            single<Clock> { Clock.System }
            single {
                WebAuthnService(
                    get(), get(), get(), get(), get(),
                    config.propertyOrNull("admin.email")?.getString() ?: "admin@adopt-u.com",
                    config.propertyOrNull("webauthn.rpId")?.getString() ?: "localhost",
                    config.propertyOrNull("webauthn.rpName")?.getString() ?: "Adopt-U Pet Adoption",
                    listOf(config.propertyOrNull("webauthn.origin")?.getString() ?: "http://localhost:80")
                )
            }
            single<ImageStoragePort> { MockImageStorage() }
            single { mockNotificationAdapter }
            single<NotificationPort> { mockNotificationAdapter }
            single<PetRepositoryPort> { PetRepositoryImpl(get()) }
            single<UserRepositoryPort> { UserRepository(get()) }
            single<PhotographerRepositoryPort> { PhotographerRepositoryImpl(get(), get(), get()) }
            single { PhotographerService(get(), get(), get(), get()) }
            single { UserService(get(), get()) }
            single { com.universaliun.ratelimit.common.RateLimiter(com.universaliun.ratelimit.common.InMemoryRateLimitStateAdapter()) }
            single { ProfileEmailVerificationService(get(), get(), get(), "http://localhost:80") }
            single { PetService(get(), get(), get(), get()) }
            single { PasswordService(get(), get(), get(), "http://localhost:80", get()) }
            single { EmailChangeService(get(), get(), get(), "http://localhost:80") }
            single { com.adoptu.services.EmailVerificationService(get(), get(), get(), "http://localhost:80", get()) }
            single { com.adoptu.services.MagicLinkService(get(), get(), get(), "http://localhost:80", get(), get()) }
        })
    }

    /**
     * Replaces `TestApplicationBuilder.setupApp()`: starts a real Helidon server through the
     * production `configureRouting` route tree (which includes usersRoutes()/adminUsersRoutes())
     * wired with the mocked Koin module above. `initDatabase = false` because this test manages
     * its own H2 connection via TestDatabase in @BeforeEach. `withTestLogin = true` replaces the
     * old test-only `/test/login/{userId}` route + `client.loginAs()` helper.
     */
    private fun startServer(): TestServerHandle =
        TestServer.start(modules = buildTestModules(), initDatabase = false, withTestLogin = true)

    // ==================== POST /api/users/accept-terms ====================

    @Test
    fun `POST accept-terms returns 401 when no session`() {
        val handle = startServer()
        try {
            val request = AcceptTermsRequest(
                acceptPrivacyPolicy = true,
                acceptTermsAndConditions = false
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/accept-terms",
                JsonSupport.objectMapper.writeValueAsString(request)
            )

            assertEquals(401, response.statusCode())
            assertTrue(response.body().contains("Unauthorized"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST accept-terms returns error for invalid content type`() {
        val handle = startServer()
        try {
            val response = TestHttp.postForm("${handle.baseUrl}/api/users/accept-terms", "invalid body")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST accept-terms returns 404 for non-existent user`() {
        val handle = startServer()
        try {
            val response = TestHttp.post("${handle.baseUrl}/api/users/accept-terms", "user_session=invalid_session")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST accept-terms returns 404 for session user that does not exist`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val request = AcceptTermsRequest(
                acceptPrivacyPolicy = true,
                acceptTermsAndConditions = true
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/accept-terms",
                JsonSupport.objectMapper.writeValueAsString(request),
                cookie
            )

            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST accept-terms succeeds for authenticated user`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val request = AcceptTermsRequest(
                acceptPrivacyPolicy = true,
                acceptTermsAndConditions = true
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/accept-terms",
                JsonSupport.objectMapper.writeValueAsString(request),
                cookie
            )

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("rescuer@test.com"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/admin/users ====================

    @Test
    fun `GET admin users returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/admin/users")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin users returns 403 for non-admin user`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1) // rescuer

            val response = TestHttp.get("${handle.baseUrl}/api/admin/users", cookie)
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin users returns list for admin`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.get("${handle.baseUrl}/api/admin/users", cookie)
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("rescuer@test.com"))
            assertTrue(body.contains("adopter@test.com"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin users returns a paged envelope`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.get("${handle.baseUrl}/api/admin/users?pageSize=2", cookie)
            assertEquals(200, response.statusCode())
            val json = JsonSupport.objectMapper.readTree(response.body())
            assertEquals(1, json.get("page").asInt())
            assertEquals(2, json.get("pageSize").asInt())
            assertEquals(2, json.get("items").size())
            assertTrue(json.get("total").asInt() >= 2)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin users excludes inactive users by default`() {
        val handle = startServer()
        try {
            transaction { Users.update({ Users.id eq 4 }) { it[Users.deactivatedAt] = 123L; it[Users.deactivatedBy] = 3 } }
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.get("${handle.baseUrl}/api/admin/users", cookie)
            assertFalse(response.body().contains("bannable@test.com"))

            val withInactive = TestHttp.get("${handle.baseUrl}/api/admin/users?includeInactive=true", cookie)
            assertTrue(withInactive.body().contains("bannable@test.com"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin users excludes banned users by default`() {
        val handle = startServer()
        try {
            transaction { Users.update({ Users.id eq 4 }) { it[Users.isBanned] = true } }
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.get("${handle.baseUrl}/api/admin/users", cookie)
            assertFalse(response.body().contains("bannable@test.com"))

            val withBanned = TestHttp.get("${handle.baseUrl}/api/admin/users?includeBanned=true", cookie)
            assertTrue(withBanned.body().contains("bannable@test.com"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin users filters by role`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.get("${handle.baseUrl}/api/admin/users?role=RESCUER", cookie)
            assertTrue(response.body().contains("rescuer@test.com"))
            assertFalse(response.body().contains("adopter@test.com"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin users search filters by email or name`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.get("${handle.baseUrl}/api/admin/users?search=rescuer", cookie)
            assertTrue(response.body().contains("rescuer@test.com"))
            assertFalse(response.body().contains("adopter@test.com"))
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/admin/users/{id}/deactivate, /reactivate ====================

    @Test
    fun `POST admin users deactivate returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.post("${handle.baseUrl}/api/admin/users/4/deactivate")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users deactivate returns 403 for non-admin user`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1) // rescuer

            val response = TestHttp.post("${handle.baseUrl}/api/admin/users/4/deactivate", cookie)
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users deactivate returns 400 when targeting self`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.post("${handle.baseUrl}/api/admin/users/3/deactivate", cookie)
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Cannot deactivate yourself"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users deactivate returns 404 for non-existent target`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.post("${handle.baseUrl}/api/admin/users/9999/deactivate", cookie)
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users deactivate then reactivate round-trips deactivatedAt`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val deactivateResponse = TestHttp.post("${handle.baseUrl}/api/admin/users/4/deactivate", cookie)
            assertEquals(200, deactivateResponse.statusCode())
            val afterDeactivate = transaction { Users.selectAll().where { Users.id eq 4 }.first() }
            assertEquals(3, afterDeactivate[Users.deactivatedBy])
            assertTrue(afterDeactivate[Users.deactivatedAt] != null)

            val reactivateResponse = TestHttp.post("${handle.baseUrl}/api/admin/users/4/reactivate", cookie)
            assertEquals(200, reactivateResponse.statusCode())
            val afterReactivate = transaction { Users.selectAll().where { Users.id eq 4 }.first() }
            assertEquals(null, afterReactivate[Users.deactivatedAt])
            assertEquals(null, afterReactivate[Users.deactivatedBy])
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/admin/users/{id} ====================

    @Test
    fun `GET admin users by id returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/admin/users/1")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin users by id returns 403 for non-admin user`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2) // adopter

            val response = TestHttp.get("${handle.baseUrl}/api/admin/users/1", cookie)
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin users by id returns 400 for invalid id`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.get("${handle.baseUrl}/api/admin/users/not-a-number", cookie)
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin users by id returns 404 for non-existent user`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.get("${handle.baseUrl}/api/admin/users/9999", cookie)
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin users by id returns user for admin`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.get("${handle.baseUrl}/api/admin/users/1", cookie)
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("rescuer@test.com"))
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/admin/users/{id}/ban ====================

    @Test
    fun `POST admin users ban returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/admin/users/4/ban",
                JsonSupport.objectMapper.writeValueAsString(BanUserRequest("spam"))
            )
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users ban returns 403 for non-admin user`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1) // rescuer

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/admin/users/4/ban",
                JsonSupport.objectMapper.writeValueAsString(BanUserRequest("spam")),
                cookie
            )
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users ban returns 400 for invalid id`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/admin/users/not-a-number/ban",
                JsonSupport.objectMapper.writeValueAsString(BanUserRequest("spam")),
                cookie
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users ban returns 400 when banning self`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/admin/users/3/ban",
                JsonSupport.objectMapper.writeValueAsString(BanUserRequest("spam")),
                cookie
            )
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Cannot ban yourself"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users ban returns 404 for non-existent target`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/admin/users/9999/ban",
                JsonSupport.objectMapper.writeValueAsString(BanUserRequest("spam")),
                cookie
            )
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users ban returns 400 when target is admin`() {
        val handle = startServer()
        try {
            // Create a second admin to ban
            transaction {
                val secondAdminId = Users.insert {
                    it[Users.id] = 5
                    it[Users.username] = "admin2@test.com"
                    it[Users.displayName] = "Second Admin"
                    it[Users.createdAt] = clock.now().toEpochMilliseconds()
                } get Users.id
                UserActiveRoles.insert {
                    it[UserActiveRoles.userId] = secondAdminId
                    it[UserActiveRoles.role] = "ADMIN"
                }
            }
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/admin/users/5/ban",
                JsonSupport.objectMapper.writeValueAsString(BanUserRequest("spam")),
                cookie
            )
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Cannot ban an admin"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users ban succeeds for valid target`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/admin/users/4/ban",
                JsonSupport.objectMapper.writeValueAsString(BanUserRequest("repeated spam reports")),
                cookie
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessResponse::class.java)
            assertTrue(body.success)

            val banned = transaction { Users.selectAll().where { Users.id eq 4 }.first()[Users.isBanned] }
            assertTrue(banned)
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/admin/users/{id}/unban ====================

    @Test
    fun `POST admin users unban returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.post("${handle.baseUrl}/api/admin/users/4/unban")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users unban returns 403 for non-admin user`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1) // rescuer

            val response = TestHttp.post("${handle.baseUrl}/api/admin/users/4/unban", cookie)
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users unban returns 400 for invalid id`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.post("${handle.baseUrl}/api/admin/users/not-a-number/unban", cookie)
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users unban returns 500 for non-existent target`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.post("${handle.baseUrl}/api/admin/users/9999/unban", cookie)
            assertEquals(500, response.statusCode())
            assertTrue(response.body().contains("Failed to unban user"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users unban succeeds for previously banned user`() {
        val handle = startServer()
        try {
            transaction { Users.update({ Users.id eq 4 }) { it[Users.isBanned] = true; it[Users.banReason] = "test" } }
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.post("${handle.baseUrl}/api/admin/users/4/unban", cookie)
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessResponse::class.java)
            assertTrue(body.success)

            val banned = transaction { Users.selectAll().where { Users.id eq 4 }.first()[Users.isBanned] }
            assertFalse(banned)
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/admin/users/{id}/reactivate (edge cases) ====================

    @Test
    fun `POST admin users reactivate returns 403 for non-admin user`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1) // rescuer

            val response = TestHttp.post("${handle.baseUrl}/api/admin/users/4/reactivate", cookie)
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users reactivate returns 500 for non-existent target`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.post("${handle.baseUrl}/api/admin/users/9999/reactivate", cookie)
            assertEquals(500, response.statusCode())
            assertTrue(response.body().contains("Failed to reactivate user"))
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/admin/users/{id}/reset-password ====================

    @Test
    fun `POST admin users reset-password returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.post("${handle.baseUrl}/api/admin/users/4/reset-password")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users reset-password returns 403 for non-admin user`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1) // rescuer

            val response = TestHttp.post("${handle.baseUrl}/api/admin/users/4/reset-password", cookie)
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users reset-password returns 400 for invalid id`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.post("${handle.baseUrl}/api/admin/users/not-a-number/reset-password", cookie)
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users reset-password returns 400 when targeting self`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.post("${handle.baseUrl}/api/admin/users/3/reset-password", cookie)
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Cannot reset your own password"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users reset-password returns 404 for non-existent target`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.post("${handle.baseUrl}/api/admin/users/9999/reset-password", cookie)
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin users reset-password invalidates credentials without deleting the account`() {
        val handle = startServer()
        try {
            transaction {
                UserPasswords.insert {
                    it[UserPasswords.userId] = 4
                    it[UserPasswords.passwordHash] = "irrelevant-hash"
                    it[UserPasswords.createdAt] = clock.now().toEpochMilliseconds()
                    it[UserPasswords.updatedAt] = clock.now().toEpochMilliseconds()
                }
                WebAuthnCredentials.insert {
                    it[WebAuthnCredentials.userId] = 4
                    it[WebAuthnCredentials.credentialId] = "test-credential-id"
                    it[WebAuthnCredentials.attestedCredentialDataBase64] = "dGVzdA=="
                    it[WebAuthnCredentials.signCount] = 0
                    it[WebAuthnCredentials.transports] = null
                    it[WebAuthnCredentials.createdAt] = clock.now().toEpochMilliseconds()
                }
            }
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin

            val response = TestHttp.post("${handle.baseUrl}/api/admin/users/4/reset-password", cookie)
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessResponse::class.java)
            assertTrue(body.success)

            transaction {
                assertTrue(UserPasswords.selectAll().where { UserPasswords.userId eq 4 }.empty())
                assertTrue(WebAuthnCredentials.selectAll().where { WebAuthnCredentials.userId eq 4 }.empty())
                val user = Users.selectAll().where { Users.id eq 4 }.first()
                assertEquals("bannable@test.com", user[Users.username])
            }
        } finally {
            handle.stop()
        }
    }

    // ==================== PUT /api/users/profile ====================

    @Test
    fun `PUT profile returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/profile",
                JsonSupport.objectMapper.writeValueAsString(UpdateProfileRequest("New Name"))
            )
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT profile updates display name when authenticated`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/profile",
                JsonSupport.objectMapper.writeValueAsString(UpdateProfileRequest("New Name")),
                cookie
            )
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("New Name"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT profile returns 404 for session user that does not exist`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/profile",
                JsonSupport.objectMapper.writeValueAsString(UpdateProfileRequest("New Name")),
                cookie
            )
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT profile returns 400 for blank display name`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/profile",
                JsonSupport.objectMapper.writeValueAsString(UpdateProfileRequest("")),
                cookie
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT profile accepts language query parameter`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/profile?language=es",
                JsonSupport.objectMapper.writeValueAsString(UpdateProfileRequest("Nombre Nuevo")),
                cookie
            )
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== PUT /api/users/language ====================

    @Test
    fun `PUT language returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/language",
                JsonSupport.objectMapper.writeValueAsString(UpdateLanguageRequest("fr"))
            )
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT language updates language when authenticated`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/language",
                JsonSupport.objectMapper.writeValueAsString(UpdateLanguageRequest("fr")),
                cookie
            )
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT language returns 404 for session user that does not exist`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/language",
                JsonSupport.objectMapper.writeValueAsString(UpdateLanguageRequest("fr")),
                cookie
            )
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT language returns 400 for blank language`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/language",
                JsonSupport.objectMapper.writeValueAsString(UpdateLanguageRequest("")),
                cookie
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/users/rescuers ====================

    @Test
    fun `GET rescuers returns rescuer list without auth`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/users/rescuers")
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("rescuer@test.com"))
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/users/rescuer-profile ====================

    @Test
    fun `POST rescuer-profile returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/rescuer-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true))
            )
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST rescuer-profile activates when authenticated`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2) // adopter

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/rescuer-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true)),
                cookie
            )
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST rescuer-profile deactivates when authenticated`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1) // already a rescuer

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/rescuer-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(false)),
                cookie
            )
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST rescuer-profile returns 403 when activating for unverified user`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 4) // bannable@test.com, not verified

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/rescuer-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true)),
                cookie
            )
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/users/temporal-home-profile ====================

    @Test
    fun `POST temporal-home-profile returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/temporal-home-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true))
            )
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST temporal-home-profile activates and deactivates when authenticated`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val activate = TestHttp.postJson(
                "${handle.baseUrl}/api/users/temporal-home-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true)),
                cookie
            )
            assertEquals(200, activate.statusCode())

            val deactivate = TestHttp.postJson(
                "${handle.baseUrl}/api/users/temporal-home-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(false)),
                cookie
            )
            assertEquals(200, deactivate.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST temporal-home-profile returns 404 for session user that does not exist`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/temporal-home-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true)),
                cookie
            )
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST temporal-home-profile returns 403 when activating for unverified user`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1) // rescuer, not verified

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/temporal-home-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true)),
                cookie
            )
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST temporal-home-profile deactivate returns 404 for session user that does not exist`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/temporal-home-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(false)),
                cookie
            )
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== PUT /api/users/photographer-settings ====================

    @Test
    fun `PUT photographer-settings returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/photographer-settings",
                """{"photographerFee":50.0,"photographerCurrency":"USD","country":"United States","state":"NY"}"""
            )
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT photographer-settings returns 404 when no photographer profile exists`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/photographer-settings",
                """{"photographerFee":50.0,"photographerCurrency":"USD","country":"United States","state":"NY"}""",
                cookie
            )
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT photographer-settings succeeds when photographer profile exists`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            TestHttp.postJson(
                "${handle.baseUrl}/api/users/photographer-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true)),
                cookie
            )

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/photographer-settings",
                """{"photographerFee":50.0,"photographerCurrency":"USD","country":"United States","state":"NY"}""",
                cookie
            )
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/users/photographer-profile ====================

    @Test
    fun `POST photographer-profile returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/photographer-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true))
            )
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST photographer-profile activates and deactivates when authenticated`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val activate = TestHttp.postJson(
                "${handle.baseUrl}/api/users/photographer-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true)),
                cookie
            )
            assertEquals(200, activate.statusCode())

            val deactivate = TestHttp.postJson(
                "${handle.baseUrl}/api/users/photographer-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(false)),
                cookie
            )
            assertEquals(200, deactivate.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST photographer-profile returns 403 when activating for unverified user`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1) // rescuer, not verified

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/photographer-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true)),
                cookie
            )
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST photographer-profile deactivate returns 404 for session user that does not exist`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/photographer-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(false)),
                cookie
            )
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/users/shelter-profile ====================

    @Test
    fun `POST shelter-profile returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/shelter-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true))
            )
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST shelter-profile activates and deactivates when authenticated`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val activate = TestHttp.postJson(
                "${handle.baseUrl}/api/users/shelter-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true)),
                cookie
            )
            assertEquals(200, activate.statusCode())

            val deactivate = TestHttp.postJson(
                "${handle.baseUrl}/api/users/shelter-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(false)),
                cookie
            )
            assertEquals(200, deactivate.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST shelter-profile returns 404 for session user that does not exist`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/shelter-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true)),
                cookie
            )
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST shelter-profile returns 403 when activating for unverified account email`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1) // rescuer, not verified

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/shelter-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true)),
                cookie
            )
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST shelter-profile returns 403 when shelter contact email is unverified`() {
        val handle = startServer()
        try {
            transaction {
                com.adoptu.adapters.db.UserShelters.insert {
                    it[com.adoptu.adapters.db.UserShelters.userId] = 2
                    it[com.adoptu.adapters.db.UserShelters.name] = "Test Shelter"
                    it[com.adoptu.adapters.db.UserShelters.country] = com.adoptu.common.Country.UNITED_STATES
                    it[com.adoptu.adapters.db.UserShelters.city] = "Anytown"
                    it[com.adoptu.adapters.db.UserShelters.address] = "123 Main St"
                    it[com.adoptu.adapters.db.UserShelters.email] = "contact@shelter-test.com"
                    it[com.adoptu.adapters.db.UserShelters.emailVerified] = false
                    it[com.adoptu.adapters.db.UserShelters.createdAt] = clock.now().toEpochMilliseconds()
                    it[com.adoptu.adapters.db.UserShelters.updatedAt] = clock.now().toEpochMilliseconds()
                }
            }
            val cookie = TestHttp.loginAs(handle.baseUrl, 2) // adopter, verified account email

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/shelter-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true)),
                cookie
            )
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST shelter-profile deactivate returns 404 for session user that does not exist`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/shelter-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(false)),
                cookie
            )
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/users/sterilization-profile ====================

    @Test
    fun `POST sterilization-profile returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/sterilization-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true))
            )
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST sterilization-profile activates and deactivates when authenticated`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2)

            val activate = TestHttp.postJson(
                "${handle.baseUrl}/api/users/sterilization-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true)),
                cookie
            )
            assertEquals(200, activate.statusCode())

            val deactivate = TestHttp.postJson(
                "${handle.baseUrl}/api/users/sterilization-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(false)),
                cookie
            )
            assertEquals(200, deactivate.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST sterilization-profile returns 404 for session user that does not exist`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/sterilization-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true)),
                cookie
            )
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST sterilization-profile returns 403 when activating for unverified account email`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1) // rescuer, not verified

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/sterilization-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true)),
                cookie
            )
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST sterilization-profile returns 403 when contact email is unverified`() {
        val handle = startServer()
        try {
            transaction {
                com.adoptu.adapters.db.UserSterilizationLocations.insert {
                    it[com.adoptu.adapters.db.UserSterilizationLocations.userId] = 2
                    it[com.adoptu.adapters.db.UserSterilizationLocations.name] = "Test Clinic"
                    it[com.adoptu.adapters.db.UserSterilizationLocations.country] = com.adoptu.common.Country.UNITED_STATES
                    it[com.adoptu.adapters.db.UserSterilizationLocations.city] = "Anytown"
                    it[com.adoptu.adapters.db.UserSterilizationLocations.address] = "123 Main St"
                    it[com.adoptu.adapters.db.UserSterilizationLocations.email] = "contact@clinic-test.com"
                    it[com.adoptu.adapters.db.UserSterilizationLocations.emailVerified] = false
                    it[com.adoptu.adapters.db.UserSterilizationLocations.createdAt] = clock.now().toEpochMilliseconds()
                    it[com.adoptu.adapters.db.UserSterilizationLocations.updatedAt] = clock.now().toEpochMilliseconds()
                }
            }
            val cookie = TestHttp.loginAs(handle.baseUrl, 2) // adopter, verified account email

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/sterilization-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(true)),
                cookie
            )
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST sterilization-profile deactivate returns 404 for session user that does not exist`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/sterilization-profile",
                JsonSupport.objectMapper.writeValueAsString(RoleActivationRequest(false)),
                cookie
            )
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/users/has-password ====================

    @Test
    fun `GET has-password returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/users/has-password")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET has-password returns false when no password set`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.get("${handle.baseUrl}/api/users/has-password", cookie)
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("\"hasPassword\":false") || response.body().contains("\"hasPassword\": false"))
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/users/password ====================

    private fun encryptedCredential(plaintext: String): String {
        com.adoptu.services.crypto.CryptoService.initialize()
        val publicKey = com.adoptu.services.crypto.CryptoService.getPublicKey()
        return com.adoptu.services.crypto.CryptoService.encrypt(plaintext, publicKey)
            ?: error("Encryption failed in test")
    }

    @Test
    fun `POST password returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/password",
                JsonSupport.objectMapper.writeValueAsString(SetPasswordRequest(encryptedCredential("user1@test.com:ValidPass123!")))
            )
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST password succeeds for a strong password`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/password",
                JsonSupport.objectMapper.writeValueAsString(SetPasswordRequest(encryptedCredential("user1@test.com:ValidPass123!"))),
                cookie
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessResponse::class.java)
            assertTrue(body.success)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST password fails for a weak password`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/password",
                JsonSupport.objectMapper.writeValueAsString(SetPasswordRequest(encryptedCredential("user1@test.com:weak"))),
                cookie
            )
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("\"success\":false") || response.body().contains("\"success\": false"))
        } finally {
            handle.stop()
        }
    }

    // ==================== PUT /api/users/password ====================

    @Test
    fun `PUT password returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/password",
                JsonSupport.objectMapper.writeValueAsString(
                    ChangePasswordRequest(encryptedCredential("user1@test.com:Old123!@"), encryptedCredential("user1@test.com:New456!@"))
                )
            )
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT password succeeds when no existing password is set`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/password",
                JsonSupport.objectMapper.writeValueAsString(
                    ChangePasswordRequest(encryptedCredential("user1@test.com:Whatever123!"), encryptedCredential("user1@test.com:New456!@"))
                ),
                cookie
            )
            assertEquals(200, response.statusCode())
            val body = JsonSupport.objectMapper.readValue(response.body(), SuccessResponse::class.java)
            assertTrue(body.success)
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT password fails when new password is weak`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/users/password",
                JsonSupport.objectMapper.writeValueAsString(
                    ChangePasswordRequest(encryptedCredential("user1@test.com:Whatever123!"), encryptedCredential("user1@test.com:weak"))
                ),
                cookie
            )
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("\"success\":false") || response.body().contains("\"success\": false"))
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/users/request-email-change ====================

    @Test
    fun `POST request-email-change returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/request-email-change",
                """{"newEmail":"new@test.com"}"""
            )
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST request-email-change returns 400 for invalid email format`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/request-email-change",
                """{"newEmail":"not-an-email"}""",
                cookie
            )
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST request-email-change returns 404 for session user that does not exist`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 9999)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/request-email-change",
                """{"newEmail":"new@test.com"}""",
                cookie
            )
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST request-email-change returns success false when email already in use`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1) // rescuer

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/request-email-change",
                """{"newEmail":"adopter@test.com"}""",
                cookie
            )
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("\"success\":false") || response.body().contains("\"success\": false"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST request-email-change succeeds for a valid new email`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 1)

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/users/request-email-change",
                """{"newEmail":"brand-new@test.com"}""",
                cookie
            )
            assertEquals(200, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/users/verify-email-change ====================

    @Test
    fun `GET verify-email-change returns 400 when token missing`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/users/verify-email-change")
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET verify-email-change returns failure message for invalid token`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/users/verify-email-change?token=nonexistent")
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("\"success\":false") || response.body().contains("\"success\": false"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/users/verify-profile-email ====================

    @Test
    fun `GET verify-profile-email returns 400 when token missing`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/users/verify-profile-email")
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET verify-profile-email returns failure message for invalid token`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/users/verify-profile-email?token=nonexistent")
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("\"success\":false") || response.body().contains("\"success\": false"))
        } finally {
            handle.stop()
        }
    }
}

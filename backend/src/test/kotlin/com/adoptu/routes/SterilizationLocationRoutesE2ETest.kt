package com.adoptu.routes

import com.adoptu.adapters.db.SterilizationLocations
import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.adapters.db.repositories.SterilizationLocationRepository
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.dto.input.CreateSterilizationLocationRequest
import com.adoptu.dto.input.UpdateSterilizationLocationRequest
import com.adoptu.mocks.TestDatabase
import com.adoptu.ports.SterilizationLocationRepositoryPort
import com.adoptu.ports.UserRepositoryPort
import com.adoptu.services.SterilizationLocationService
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
class SterilizationLocationRoutesE2ETest {

    private val clock = Clock.System

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
        createTestUsers()
    }

    private fun createTestUsers() {
        transaction {
            Users.insert {
                it[Users.id] = 2
                it[Users.username] = "adopter@test.com"
                it[Users.displayName] = "Test Adopter"
                it[Users.createdAt] = clock.now().toEpochMilliseconds()
            }
            UserActiveRoles.insert {
                it[UserActiveRoles.userId] = 2
                it[UserActiveRoles.role] = "ADOPTER"
            }

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
        }
    }

    private fun testModules() = listOf(
        module {
            single<Clock> { Clock.System }
            single<SterilizationLocationRepositoryPort> { SterilizationLocationRepository(get()) }
            single { SterilizationLocationService(get()) }
            single<UserRepositoryPort> { UserRepository(get()) }
        }
    )

    private fun startServer() = TestServer.start(modules = testModules(), initDatabase = false, withTestLogin = true)

    private fun createLocationInDb(
        name: String = "Vet Clinic",
        country: String = "United States",
        state: String? = "CA",
        city: String = "LA",
        neighborhood: String? = null,
        zip: String? = null
    ): Int {
        return transaction {
            val now = clock.now().toEpochMilliseconds()
            SterilizationLocations.insert {
                it[SterilizationLocations.name] = name
                it[SterilizationLocations.country] = com.adoptu.common.Country.fromDisplayName(country)!!
                it[SterilizationLocations.state] = state
                it[SterilizationLocations.city] = city
                it[SterilizationLocations.neighborhood] = neighborhood
                it[SterilizationLocations.zip] = zip
                it[SterilizationLocations.address] = "123 Main St"
                it[SterilizationLocations.createdAt] = now
                it[SterilizationLocations.updatedAt] = now
            } get SterilizationLocations.id
        }
    }

    // ==================== GET /api/sterilization-locations ====================

    @Test
    fun `GET sterilization-locations returns empty list when none exist`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/sterilization-locations")
            assertEquals(200, response.statusCode())
            assertEquals("[]", response.body())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET sterilization-locations returns all locations without filters`() {
        createLocationInDb(name = "Clinic A", country = "United States")
        createLocationInDb(name = "Clinic B", country = "Canada")

        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/sterilization-locations")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("Clinic A"))
            assertTrue(body.contains("Clinic B"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET sterilization-locations filters by country`() {
        createLocationInDb(name = "Clinic A", country = "United States")
        createLocationInDb(name = "Clinic B", country = "Canada")

        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/sterilization-locations?country=United%20States")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("Clinic A"))
            assertTrue(!body.contains("Clinic B"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET sterilization-locations filters by all params together`() {
        createLocationInDb(name = "Clinic A", country = "United States", state = "CA", city = "LA", neighborhood = "Downtown", zip = "90001")
        createLocationInDb(name = "Clinic B", country = "United States", state = "CA", city = "LA", neighborhood = "Uptown", zip = "90002")

        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/sterilization-locations?country=United%20States&state=CA&city=LA&neighborhood=Downtown&zip=90001")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("Clinic A"))
            assertTrue(!body.contains("Clinic B"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/sterilization-locations/grouped ====================

    @Test
    fun `GET sterilization-locations grouped returns grouped structure`() {
        createLocationInDb(name = "Clinic A", country = "United States", state = "CA", city = "LA")

        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/sterilization-locations/grouped")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("United States"))
            assertTrue(body.contains("Clinic A"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET sterilization-locations grouped returns empty list when none exist`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/sterilization-locations/grouped")
            assertEquals(200, response.statusCode())
            assertEquals("[]", response.body())
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/sterilization-locations/countries ====================

    @Test
    fun `GET sterilization-locations countries returns distinct countries`() {
        createLocationInDb(name = "Clinic A", country = "United States")
        createLocationInDb(name = "Clinic B", country = "Canada")

        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/sterilization-locations/countries")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("United States"))
            assertTrue(body.contains("Canada"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/sterilization-locations/countries/{country}/states ====================

    @Test
    fun `GET sterilization-locations states returns states for country`() {
        createLocationInDb(name = "Clinic A", country = "United States", state = "CA")
        createLocationInDb(name = "Clinic B", country = "United States", state = "NY")

        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/sterilization-locations/countries/United%20States/states")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("CA"))
            assertTrue(body.contains("NY"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/sterilization-locations/countries/{country}/states/{state}/cities ====================

    @Test
    fun `GET sterilization-locations cities returns cities for country and state`() {
        createLocationInDb(name = "Clinic A", country = "United States", state = "CA", city = "LA")
        createLocationInDb(name = "Clinic B", country = "United States", state = "CA", city = "SF")

        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/sterilization-locations/countries/United%20States/states/CA/cities")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("LA"))
            assertTrue(body.contains("SF"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/sterilization-locations/{id} ====================

    @Test
    fun `GET sterilization-location by id returns location when it exists`() {
        val id = createLocationInDb(name = "Clinic A")

        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/sterilization-locations/$id")
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Clinic A"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET sterilization-location by id returns 404 when not found`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/sterilization-locations/999")
            assertEquals(404, response.statusCode())
            assertTrue(response.body().contains("Sterilization location not found"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET sterilization-location by id returns 400 for invalid id`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/sterilization-locations/abc")
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Invalid ID"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/admin/sterilization-locations ====================

    @Test
    fun `GET admin sterilization-locations returns 401 when no session`() {
        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/admin/sterilization-locations")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin sterilization-locations returns 403 when not admin`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2) // adopter
            val response = TestHttp.get("${handle.baseUrl}/api/admin/sterilization-locations", cookie)
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin sterilization-locations returns all locations`() {
        createLocationInDb(name = "Clinic A", country = "United States")

        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.get("${handle.baseUrl}/api/admin/sterilization-locations", cookie)
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Clinic A"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin sterilization-locations filters by country`() {
        createLocationInDb(name = "Clinic A", country = "United States")
        createLocationInDb(name = "Clinic B", country = "Canada")

        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.get("${handle.baseUrl}/api/admin/sterilization-locations?country=United%20States", cookie)
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("Clinic A"))
            assertTrue(!body.contains("Clinic B"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/admin/sterilization-locations/{id} ====================

    @Test
    fun `GET admin sterilization-location by id returns 401 when no session`() {
        val id = createLocationInDb(name = "Clinic A")

        val handle = startServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/admin/sterilization-locations/$id")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin sterilization-location by id returns location when it exists`() {
        val id = createLocationInDb(name = "Clinic A")

        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.get("${handle.baseUrl}/api/admin/sterilization-locations/$id", cookie)
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Clinic A"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin sterilization-location by id returns 404 when not found`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.get("${handle.baseUrl}/api/admin/sterilization-locations/999", cookie)
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin sterilization-location by id returns 400 for invalid id`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.get("${handle.baseUrl}/api/admin/sterilization-locations/abc", cookie)
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/admin/sterilization-locations ====================

    @Test
    fun `POST admin sterilization-locations returns 401 when no session`() {
        val handle = startServer()
        try {
            val request = CreateSterilizationLocationRequest(name = "New Clinic", country = "United States", city = "LA", address = "123 Main St")
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/admin/sterilization-locations",
                JsonSupport.objectMapper.writeValueAsString(request)
            )
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin sterilization-locations creates location`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val request = CreateSterilizationLocationRequest(
                name = "New Clinic",
                country = "United States",
                city = "LA",
                address = "123 Main St"
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/admin/sterilization-locations",
                JsonSupport.objectMapper.writeValueAsString(request),
                cookie
            )

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("New Clinic"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin sterilization-locations returns 400 for blank name`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val request = CreateSterilizationLocationRequest(
                name = "",
                country = "United States",
                city = "LA",
                address = "123 Main St"
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/admin/sterilization-locations",
                JsonSupport.objectMapper.writeValueAsString(request),
                cookie
            )

            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Name is required"))
        } finally {
            handle.stop()
        }
    }

    // ==================== PUT /api/admin/sterilization-locations/{id} ====================

    @Test
    fun `PUT admin sterilization-location returns 401 when no session`() {
        val id = createLocationInDb(name = "Old Name")

        val handle = startServer()
        try {
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/admin/sterilization-locations/$id",
                JsonSupport.objectMapper.writeValueAsString(UpdateSterilizationLocationRequest(name = "New Name"))
            )
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT admin sterilization-location updates location when it exists`() {
        val id = createLocationInDb(name = "Old Name")

        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/admin/sterilization-locations/$id",
                JsonSupport.objectMapper.writeValueAsString(
                    UpdateSterilizationLocationRequest(
                        name = "New Name",
                        country = "Canada",
                        state = "ON",
                        city = "Toronto",
                        neighborhood = "Downtown",
                        address = "456 Other St",
                        zip = "M5V 2T6",
                        phone = "555-1234",
                        email = "location@example.com",
                        website = "https://example.com",
                        description = "Updated description"
                    )
                ),
                cookie
            )

            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("New Name"))
            assertTrue(body.contains("Canada"))
            assertTrue(body.contains("Toronto"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT admin sterilization-location returns 404 when not found`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/admin/sterilization-locations/999",
                JsonSupport.objectMapper.writeValueAsString(UpdateSterilizationLocationRequest(name = "New Name")),
                cookie
            )

            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT admin sterilization-location returns 400 for invalid id`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/admin/sterilization-locations/abc",
                JsonSupport.objectMapper.writeValueAsString(UpdateSterilizationLocationRequest(name = "New Name")),
                cookie
            )

            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== DELETE /api/admin/sterilization-locations/{id} ====================

    @Test
    fun `DELETE admin sterilization-location returns 401 when no session`() {
        val id = createLocationInDb(name = "To Delete")

        val handle = startServer()
        try {
            val response = TestHttp.delete("${handle.baseUrl}/api/admin/sterilization-locations/$id")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE admin sterilization-location deletes location when it exists`() {
        val id = createLocationInDb(name = "To Delete")

        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.delete("${handle.baseUrl}/api/admin/sterilization-locations/$id", cookie)

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("\"success\": true"))

            val followUp = TestHttp.get("${handle.baseUrl}/api/admin/sterilization-locations/$id", cookie)
            assertEquals(404, followUp.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE admin sterilization-location returns 404 when not found`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.delete("${handle.baseUrl}/api/admin/sterilization-locations/999", cookie)
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE admin sterilization-location returns 400 for invalid id`() {
        val handle = startServer()
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.delete("${handle.baseUrl}/api/admin/sterilization-locations/abc", cookie)
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }
}

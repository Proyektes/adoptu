package com.adoptu.routes

import com.adoptu.adapters.db.AnimalShelters
import com.adoptu.adapters.db.UserActiveRoles
import com.adoptu.adapters.db.Users
import com.adoptu.dto.input.CreateShelterRequest
import com.adoptu.dto.input.UpdateShelterRequest
import com.adoptu.mocks.TestDatabase
import com.adoptu.testsupport.TestHttp
import com.adoptu.testsupport.TestServer
import com.adoptu.web.JsonSupport
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ShelterRoutesE2ETest {

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

    private fun createShelterInDb(
        name: String = "Animal Rescue",
        country: String = "United States",
        state: String? = "CA",
        city: String = "LA",
        neighborhood: String? = null,
        zip: String? = null
    ): Int {
        return transaction {
            val now = clock.now().toEpochMilliseconds()
            AnimalShelters.insert {
                it[AnimalShelters.name] = name
                it[AnimalShelters.country] = com.adoptu.common.Country.fromDisplayName(country)!!
                it[AnimalShelters.state] = state
                it[AnimalShelters.city] = city
                it[AnimalShelters.neighborhood] = neighborhood
                it[AnimalShelters.zip] = zip
                it[AnimalShelters.address] = "123 Main St"
                it[AnimalShelters.currency] = "USD"
                it[AnimalShelters.createdAt] = now
                it[AnimalShelters.updatedAt] = now
            } get AnimalShelters.id
        }
    }

    // ==================== GET /api/shelters ====================

    @Test
    fun `GET shelters returns 400 when country is missing`() {
        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/shelters")
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Country is required"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET shelters returns 400 when country is blank`() {
        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/shelters?country=")
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET shelters returns matching shelters`() {
        createShelterInDb(name = "Shelter A", country = "United States")
        createShelterInDb(name = "Shelter B", country = "Canada")

        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/shelters?country=United%20States")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("Shelter A"))
            assertTrue(!body.contains("Shelter B"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET shelters filters by state city and zip`() {
        createShelterInDb(name = "Shelter A", country = "United States", state = "CA", city = "LA", zip = "90001")
        createShelterInDb(name = "Shelter B", country = "United States", state = "NY", city = "NYC", zip = "10001")

        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/shelters?country=United%20States&state=CA&city=LA&zip=90001")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("Shelter A"))
            assertTrue(!body.contains("Shelter B"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/shelters/countries ====================

    @Test
    fun `GET shelters countries returns distinct countries`() {
        createShelterInDb(name = "Shelter A", country = "United States")
        createShelterInDb(name = "Shelter B", country = "Canada")

        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/shelters/countries")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("United States"))
            assertTrue(body.contains("Canada"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/shelters/countries/{country}/states ====================

    @Test
    fun `GET shelters states returns states for country`() {
        createShelterInDb(name = "Shelter A", country = "United States", state = "CA")
        createShelterInDb(name = "Shelter B", country = "United States", state = "NY")

        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/shelters/countries/United%20States/states")
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("CA"))
            assertTrue(body.contains("NY"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/shelters/{id} ====================

    @Test
    fun `GET shelter by id returns shelter when it exists`() {
        val id = createShelterInDb(name = "Shelter A")

        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/shelters/$id")
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Shelter A"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET shelter by id returns 404 when not found`() {
        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/shelters/999")
            assertEquals(404, response.statusCode())
            assertTrue(response.body().contains("Shelter not found"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET shelter by id returns 400 for invalid id`() {
        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/shelters/abc")
            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Invalid ID"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/admin/shelters ====================

    @Test
    fun `GET admin shelters returns 401 when no session`() {
        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/admin/shelters")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin shelters returns 403 when not admin`() {
        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 2) // adopter
            val response = TestHttp.get("${handle.baseUrl}/api/admin/shelters", cookie)
            assertEquals(403, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin shelters returns empty list when country is missing`() {
        createShelterInDb(name = "Shelter A", country = "United States")

        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.get("${handle.baseUrl}/api/admin/shelters", cookie)
            assertEquals(200, response.statusCode())
            assertEquals("[]", response.body())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin shelters returns matching shelters when country provided`() {
        createShelterInDb(name = "Shelter A", country = "United States")
        createShelterInDb(name = "Shelter B", country = "Canada")

        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.get("${handle.baseUrl}/api/admin/shelters?country=United%20States", cookie)
            assertEquals(200, response.statusCode())
            val body = response.body()
            assertTrue(body.contains("Shelter A"))
            assertTrue(!body.contains("Shelter B"))
        } finally {
            handle.stop()
        }
    }

    // ==================== GET /api/admin/shelters/{id} ====================

    @Test
    fun `GET admin shelter by id returns 401 when no session`() {
        val id = createShelterInDb(name = "Shelter A")

        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/admin/shelters/$id")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin shelter by id returns shelter when it exists`() {
        val id = createShelterInDb(name = "Shelter A")

        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.get("${handle.baseUrl}/api/admin/shelters/$id", cookie)
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Shelter A"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin shelter by id returns 404 when not found`() {
        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.get("${handle.baseUrl}/api/admin/shelters/999", cookie)
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET admin shelter by id returns 400 for invalid id`() {
        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.get("${handle.baseUrl}/api/admin/shelters/abc", cookie)
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== POST /api/admin/shelters ====================

    @Test
    fun `POST admin shelters returns 401 when no session`() {
        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val request = CreateShelterRequest(name = "New Shelter", country = "United States", city = "LA", address = "123 Main St")
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/admin/shelters",
                JsonSupport.objectMapper.writeValueAsString(request)
            )
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin shelters creates shelter`() {
        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val request = CreateShelterRequest(
                name = "New Shelter",
                country = "United States",
                city = "LA",
                address = "123 Main St"
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/admin/shelters",
                JsonSupport.objectMapper.writeValueAsString(request),
                cookie
            )

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("New Shelter"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin shelters returns 400 for blank name`() {
        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val request = CreateShelterRequest(
                name = "",
                country = "United States",
                city = "LA",
                address = "123 Main St"
            )

            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/admin/shelters",
                JsonSupport.objectMapper.writeValueAsString(request),
                cookie
            )

            assertEquals(400, response.statusCode())
            assertTrue(response.body().contains("Name is required"))
        } finally {
            handle.stop()
        }
    }

    // ==================== PUT /api/admin/shelters/{id} ====================

    @Test
    fun `PUT admin shelter returns 401 when no session`() {
        val id = createShelterInDb(name = "Old Name")

        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/admin/shelters/$id",
                JsonSupport.objectMapper.writeValueAsString(UpdateShelterRequest(name = "New Name"))
            )
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT admin shelter updates shelter when it exists`() {
        val id = createShelterInDb(name = "Old Name")

        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/admin/shelters/$id",
                JsonSupport.objectMapper.writeValueAsString(
                    UpdateShelterRequest(
                        name = "New Name",
                        country = "Canada",
                        state = "ON",
                        city = "Toronto",
                        neighborhood = "Downtown",
                        address = "456 Other St",
                        zip = "M5V 2T6",
                        phone = "555-1234",
                        email = "shelter@example.com",
                        website = "https://example.com",
                        fiscalId = "FID123",
                        bankName = "Test Bank",
                        accountHolderName = "Account Holder",
                        accountNumber = "1234567890",
                        iban = "IBAN123",
                        swiftBic = "SWIFT123",
                        currency = "CAD",
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
    fun `PUT admin shelter returns 404 when not found`() {
        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/admin/shelters/999",
                JsonSupport.objectMapper.writeValueAsString(UpdateShelterRequest(name = "New Name")),
                cookie
            )

            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT admin shelter returns 400 for invalid id`() {
        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/admin/shelters/abc",
                JsonSupport.objectMapper.writeValueAsString(UpdateShelterRequest(name = "New Name")),
                cookie
            )

            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // ==================== DELETE /api/admin/shelters/{id} ====================

    @Test
    fun `DELETE admin shelter returns 401 when no session`() {
        val id = createShelterInDb(name = "To Delete")

        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val response = TestHttp.delete("${handle.baseUrl}/api/admin/shelters/$id")
            assertEquals(401, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE admin shelter deletes shelter when it exists`() {
        val id = createShelterInDb(name = "To Delete")

        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.delete("${handle.baseUrl}/api/admin/shelters/$id", cookie)

            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("\"success\": true"))

            val followUp = TestHttp.get("${handle.baseUrl}/api/admin/shelters/$id", cookie)
            assertEquals(404, followUp.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE admin shelter returns 404 when not found`() {
        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.delete("${handle.baseUrl}/api/admin/shelters/999", cookie)
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE admin shelter returns 400 for invalid id`() {
        val handle = TestServer.start(initDatabase = false, withTestLogin = true)
        try {
            val cookie = TestHttp.loginAs(handle.baseUrl, 3) // admin
            val response = TestHttp.delete("${handle.baseUrl}/api/admin/shelters/abc", cookie)
            assertEquals(400, response.statusCode())
        } finally {
            handle.stop()
        }
    }
}

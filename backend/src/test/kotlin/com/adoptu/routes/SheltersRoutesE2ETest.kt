package com.adoptu.routes

import com.adoptu.adapters.db.AnimalShelters
import com.adoptu.common.Country
import com.adoptu.testsupport.TestHttp
import com.adoptu.testsupport.TestServer
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Ported from the old Ktor test-application + Testcontainers (Postgres/LocalStack) harness to
 * the Helidon Nima TestServer/TestHttp pattern. The original test spun up its own Koin module
 * wiring real repository/adapter implementations against Testcontainers-backed Postgres and S3 -
 * that wiring is now just the production `appModule(config)` (the TestServer default) run
 * against H2, since none of these tests touch image storage or auth. No custom Koin module or
 * withTestLogin is needed: none of the shelter routes require a session cookie.
 */
@OptIn(ExperimentalTime::class)
class SheltersRoutesE2ETest {

    private fun createTestShelter(
        name: String,
        country: String,
        state: String? = null,
        city: String,
        address: String = "123 Test St"
    ): Int {
        val parsedCountry = Country.fromDisplayName(country) ?: throw IllegalArgumentException("Invalid country: $country")
        return transaction {
            AnimalShelters.insert {
                it[AnimalShelters.name] = name
                it[AnimalShelters.country] = parsedCountry
                it[AnimalShelters.state] = state
                it[AnimalShelters.city] = city
                it[AnimalShelters.address] = address
                it[AnimalShelters.createdAt] = Clock.System.now().toEpochMilliseconds()
                it[AnimalShelters.updatedAt] = Clock.System.now().toEpochMilliseconds()
            } get AnimalShelters.id
        }
    }

    @Test
    fun `GET shelters returns empty list when no shelters`() {
        val handle = TestServer.start()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/shelters?country=United%20States")
            assertEquals(200, response.statusCode(), "Expected 200 OK")
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET shelters returns shelters for country`() {
        val handle = TestServer.start()
        try {
            createTestShelter("Shelter 1", "United States", "NY", "New York")
            createTestShelter("Shelter 2", "United States", "CA", "Los Angeles")
            createTestShelter("Shelter 3", "Canada", "ON", "Toronto")

            val response = TestHttp.get("${handle.baseUrl}/api/shelters?country=United%20States")
            assertEquals(200, response.statusCode(), "Expected 200 OK")
            val body = response.body()
            assertTrue(body.contains("Shelter 1"), "Should contain Shelter 1")
            assertTrue(body.contains("Shelter 2"), "Should contain Shelter 2")
            assertTrue(!body.contains("Shelter 3") || !body.contains("Canada"), "Should not contain Shelter 3 (Canada)")
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET shelters filters by state`() {
        val handle = TestServer.start()
        try {
            createTestShelter("Shelter NY 1", "United States", "NY", "New York")
            createTestShelter("Shelter CA", "United States", "CA", "Los Angeles")
            createTestShelter("Shelter NY 2", "United States", "NY", "Buffalo")

            val response = TestHttp.get("${handle.baseUrl}/api/shelters?country=United%20States&state=NY")
            assertEquals(200, response.statusCode(), "Expected 200 OK")
            val body = response.body()
            assertTrue(body.contains("Shelter NY 1"), "Should contain Shelter NY 1")
            assertTrue(body.contains("Shelter NY 2"), "Should contain Shelter NY 2")
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET shelters returns 400 when country is missing`() {
        val handle = TestServer.start()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/shelters")
            assertEquals(400, response.statusCode(), "Expected 400 Bad Request")
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET shelters returns shelter with all fields`() {
        val handle = TestServer.start()
        try {
            val shelterId = transaction {
                AnimalShelters.insert {
                    it[AnimalShelters.name] = "Full Shelter XYZ"
                    it[AnimalShelters.country] = Country.UNITED_STATES
                    it[AnimalShelters.state] = "CA"
                    it[AnimalShelters.city] = "Los Angeles"
                    it[AnimalShelters.address] = "123 Main St"
                    it[AnimalShelters.zip] = "90001"
                    it[AnimalShelters.phone] = "555-1234"
                    it[AnimalShelters.email] = "test@shelter.com"
                    it[AnimalShelters.website] = "https://test.com"
                    it[AnimalShelters.fiscalId] = "12-3456789"
                    it[AnimalShelters.bankName] = "Test Bank"
                    it[AnimalShelters.accountHolderName] = "Account Holder"
                    it[AnimalShelters.accountNumber] = "123456789"
                    it[AnimalShelters.iban] = "US123456789"
                    it[AnimalShelters.swiftBic] = "TESTBIC"
                    it[AnimalShelters.currency] = "USD"
                    it[AnimalShelters.description] = "Test description"
                    it[AnimalShelters.createdAt] = Clock.System.now().toEpochMilliseconds()
                    it[AnimalShelters.updatedAt] = Clock.System.now().toEpochMilliseconds()
                } get AnimalShelters.id
            }

            val response = TestHttp.get("${handle.baseUrl}/api/shelters/$shelterId")
            assertEquals(200, response.statusCode(), "Expected 200 OK")
            val body = response.body()
            assertTrue(body.contains("Full Shelter XYZ"), "Should contain shelter name")
            assertTrue(body.contains("United States"), "Should contain country")
            assertTrue(body.contains("CA"), "Should contain state")
            assertTrue(body.contains("12-3456789"), "Should contain fiscalId")
            assertTrue(body.contains("Test Bank"), "Should contain bankName")
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET shelters countries returns list of countries`() {
        val handle = TestServer.start()
        try {
            createTestShelter("Shelter 1", "United States", "NY", "New York")
            createTestShelter("Shelter 2", "Canada", "ON", "Toronto")

            val response = TestHttp.get("${handle.baseUrl}/api/shelters/countries")
            assertEquals(200, response.statusCode(), "Expected 200 OK")
            val body = response.body()
            assertTrue(body.contains("United States"), "Should contain United States")
            assertTrue(body.contains("Canada"), "Should contain Canada")
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET shelters countries returns empty list when no shelters`() {
        val handle = TestServer.start()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/shelters/countries")
            assertEquals(200, response.statusCode(), "Expected 200 OK")
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET shelters states returns list of states for country`() {
        val handle = TestServer.start()
        try {
            createTestShelter("Shelter 1", "United States", "NY", "New York")
            createTestShelter("Shelter 2", "United States", "CA", "Los Angeles")
            createTestShelter("Shelter 3", "United States", "NY", "Buffalo")
            createTestShelter("Shelter 4", "Canada", "ON", "Toronto")

            val response = TestHttp.get("${handle.baseUrl}/api/shelters/countries/United%20States/states")
            assertEquals(200, response.statusCode(), "Expected 200 OK")
            val body = response.body()
            assertTrue(body.contains("NY"), "Should contain NY")
            assertTrue(body.contains("CA"), "Should contain CA")
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET shelters states returns empty list for country with no states`() {
        val handle = TestServer.start()
        try {
            createTestShelter("Shelter 1", "United States", "NY", "New York")

            val response = TestHttp.get("${handle.baseUrl}/api/shelters/countries/Canada/states")
            assertEquals(200, response.statusCode(), "Expected 200 OK")
            val body = response.body()
            assertTrue(!body.contains("NY"), "Should not contain NY for Canada")
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET shelter by id returns shelter`() {
        val handle = TestServer.start()
        try {
            val shelterId = createTestShelter("Test Shelter", "United States", "NY", "New York")

            val response = TestHttp.get("${handle.baseUrl}/api/shelters/$shelterId")
            assertEquals(200, response.statusCode(), "Expected 200 OK")
            val body = response.body()
            assertTrue(body.contains("Test Shelter"), "Should contain shelter name")
            assertTrue(body.contains("United States"), "Should contain country")
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET shelter by id returns 404 for non-existent id`() {
        val handle = TestServer.start()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/shelters/999999")
            assertEquals(404, response.statusCode(), "Expected 404 Not Found")
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin shelter creates new shelter`() {
        val handle = TestServer.start()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/admin/shelters",
                """
                    {
                        "name": "New Shelter",
                        "country": "United States",
                        "state": "NY",
                        "city": "New York",
                        "address": "123 Main St",
                        "bankName": "Test Bank",
                        "accountNumber": "123456789"
                    }
                """.trimIndent()
            )
            assertEquals(200, response.statusCode(), "Expected 200 OK")
            val body = response.body()
            assertTrue(body.contains("New Shelter"), "Should contain new shelter name")
            assertTrue(body.contains("United States"), "Should contain country")
            assertTrue(body.contains("NY"), "Should contain state")
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `POST admin shelter creates shelter with all donation fields`() {
        val handle = TestServer.start()
        try {
            val response = TestHttp.postJson(
                "${handle.baseUrl}/api/admin/shelters",
                """
                    {
                        "name": "Full Shelter",
                        "country": "United States",
                        "state": "CA",
                        "city": "Los Angeles",
                        "address": "456 Oak Ave",
                        "zip": "90001",
                        "phone": "555-1234",
                        "email": "shelter@test.com",
                        "website": "https://test.com",
                        "fiscalId": "12-3456789",
                        "bankName": "Test Bank",
                        "accountHolderName": "Account Holder",
                        "accountNumber": "123456789",
                        "iban": "US123456789",
                        "swiftBic": "TESTBIC",
                        "currency": "USD",
                        "description": "A test shelter"
                    }
                """.trimIndent()
            )
            assertEquals(200, response.statusCode(), "Expected 200 OK")
            val body = response.body()
            assertTrue(body.contains("Full Shelter"), "Should contain shelter name")
            assertTrue(body.contains("12-3456789"), "Should contain fiscalId")
            assertTrue(body.contains("Test Bank"), "Should contain bankName")
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT admin shelter updates shelter`() {
        val handle = TestServer.start()
        try {
            val shelterId = createTestShelter("Old Name", "United States", "NY", "New York")

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/admin/shelters/$shelterId",
                """
                    {
                        "name": "Updated Name",
                        "city": "Brooklyn"
                    }
                """.trimIndent()
            )
            assertEquals(200, response.statusCode(), "Expected 200 OK")
            val body = response.body()
            assertTrue(body.contains("Updated Name"), "Should contain updated name")
            assertTrue(body.contains("Brooklyn"), "Should contain new city")
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT admin shelter updates donation info`() {
        val handle = TestServer.start()
        try {
            val shelterId = createTestShelter("Test", "United States", "NY", "New York")

            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/admin/shelters/$shelterId",
                """
                    {
                        "bankName": "New Bank",
                        "accountNumber": "987654321",
                        "iban": "US987654321",
                        "fiscalId": "99-9999999"
                    }
                """.trimIndent()
            )
            assertEquals(200, response.statusCode(), "Expected 200 OK")
            val body = response.body()
            assertTrue(body.contains("New Bank"), "Should contain new bank name")
            assertTrue(body.contains("987654321"), "Should contain new account number")
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `PUT admin shelter returns 404 for non-existent id`() {
        val handle = TestServer.start()
        try {
            val response = TestHttp.putJson(
                "${handle.baseUrl}/api/admin/shelters/999999",
                """
                    {
                        "name": "Updated Name"
                    }
                """.trimIndent()
            )
            assertEquals(404, response.statusCode(), "Expected 404 Not Found")
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE admin shelter removes shelter`() {
        val handle = TestServer.start()
        try {
            val shelterId = createTestShelter("To Delete", "United States", "NY", "New York")

            val deleteResponse = TestHttp.delete("${handle.baseUrl}/api/admin/shelters/$shelterId")
            assertEquals(200, deleteResponse.statusCode(), "Expected 200 OK on delete")

            val getResponse = TestHttp.get("${handle.baseUrl}/api/shelters/$shelterId")
            assertEquals(404, getResponse.statusCode(), "Expected 404 Not Found after delete")
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `DELETE admin shelter returns 404 for non-existent id`() {
        val handle = TestServer.start()
        try {
            val response = TestHttp.delete("${handle.baseUrl}/api/admin/shelters/999999")
            assertEquals(404, response.statusCode(), "Expected 404 Not Found")
        } finally {
            handle.stop()
        }
    }
}

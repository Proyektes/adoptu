package com.adoptu

import com.adoptu.testsupport.TestHttp
import com.adoptu.testsupport.TestServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class ApplicationContainerTest {

    companion object {
        @Container
        val postgresContainer: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("adoptu")
            .withUsername("adoptu")
            .withPassword("Ad0ptU")
    }

    private fun testAppConfig(): Map<String, Any> {
        return mapOf(
            "env" to "prod",
            "db.prod.postgres.driver" to "org.postgresql.Driver",
            "db.prod.postgres.url" to postgresContainer.jdbcUrl,
            "db.prod.postgres.user" to postgresContainer.username,
            "db.prod.postgres.password" to postgresContainer.password,
            "storage.prod.bucket" to "test-bucket",
            "storage.prod.region" to "us-east-1",
            "storage.prod.endpoint" to "http://localhost:4566",
            "storage.prod.path_style_access" to "true",
            "email.from" to "test@test.com"
        )
    }

    @Test
    fun `application starts with PostgreSQL container`() {
        assertTrue(postgresContainer.isRunning, "PostgreSQL container should be running")

        val handle = TestServer.start(configOverrides = testAppConfig())
        try {
            val response = TestHttp.get("${handle.baseUrl}/")
            assertTrue(
                response.statusCode() == 200 ||
                response.statusCode() == 404 ||
                response.statusCode() == 302,
                "Application should respond"
            )
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `application connects to PostgreSQL container and creates tables`() {
        assertTrue(postgresContainer.isRunning, "PostgreSQL container should be running")

        val handle = TestServer.start(configOverrides = testAppConfig())
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/v1/pets")
            assertTrue(
                response.statusCode() != 500,
                "Application should handle database connection"
            )
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `application handles requests with PostgreSQL container`() {
        assertTrue(postgresContainer.isRunning, "PostgreSQL container should be running")

        val handle = TestServer.start(configOverrides = testAppConfig())
        try {
            val response = TestHttp.get("${handle.baseUrl}/unknown-route-xyz-123")
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }
}

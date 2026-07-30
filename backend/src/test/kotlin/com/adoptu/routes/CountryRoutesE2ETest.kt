package com.adoptu.routes

import com.adoptu.adapters.db.repositories.PetRepositoryImpl
import com.adoptu.adapters.db.repositories.PhotographerRepositoryImpl
import com.adoptu.adapters.db.repositories.UserRepository
import com.adoptu.config.AppConfig
import com.adoptu.mocks.MockNotificationAdapter
import com.adoptu.mocks.TestDatabase
import com.adoptu.services.EmailVerificationService
import com.adoptu.testsupport.TestHttp
import com.adoptu.testsupport.TestServer
import com.adoptu.testsupport.TestServerHandle
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * End-to-end tests for [countryRoutes]: GET /api/detect-country prefers the CloudFront-injected
 * viewer-country header (IP-based geolocation) and falls back to the region subtag of the
 * client-supplied `locale` query param, matching the comment on countryRoutes() in
 * CountryRoutes.kt.
 */
@OptIn(ExperimentalTime::class)
class CountryRoutesE2ETest {

    @BeforeEach
    fun setup() {
        TestDatabase.initH2()
        TestDatabase.clearAllData()
    }

    /** Mirrors AuthRoutesE2ETest's reduced Koin module - countryRoutes() itself needs no
     * injected dependencies, but configureRouting() mounts every route group eagerly and
     * authRoutes() resolves AppConfig at setup time, so it must always be bindable. */
    private fun startTestServer(): TestServerHandle {
        val config = AppConfig.fromMap(mapOf("env" to "test", "admin.email" to "admin@test.com"))
        val mockNotificationAdapter = MockNotificationAdapter()

        val testModules = module {
            single<AppConfig> { config }
            single<Clock> { Clock.System }
            single<com.adoptu.ports.UserRepositoryPort> { UserRepository(get()) }
            single<com.adoptu.ports.PetRepositoryPort> { PetRepositoryImpl(get()) }
            single<com.adoptu.ports.PhotographerRepositoryPort> { PhotographerRepositoryImpl(get(), get(), get()) }
            single { com.adoptu.services.UserService(get(), get()) }
            single { com.universaliun.ratelimit.common.RateLimiter(com.universaliun.ratelimit.common.InMemoryRateLimitStateAdapter()) }
            single { EmailVerificationService(get(), get(), get(), "http://localhost:80", get()) }
            single { com.adoptu.services.PasswordService(get(), mockNotificationAdapter, get(), "http://localhost:80", get()) }
            single { com.adoptu.services.MagicLinkService(get(), mockNotificationAdapter, get(), "http://localhost:80", get(), get()) }
            single {
                com.adoptu.services.auth.WebAuthnService(
                    get(), get(), get(), get(), get(),
                    config.propertyOrNull("admin.email")?.getString() ?: "admin@adopt-u.com",
                    config.propertyOrNull("webauthn.rpId")?.getString() ?: "localhost",
                    config.propertyOrNull("webauthn.rpName")?.getString() ?: "Adopt-U Pet Adoption",
                    listOf(config.propertyOrNull("webauthn.origin")?.getString() ?: "http://localhost:80")
                )
            }
            single { com.adoptu.services.validation.AuthValidationService() }
            single { mockNotificationAdapter }
            single<com.adoptu.ports.NotificationPort> { mockNotificationAdapter }
        }

        return TestServer.start(modules = listOf(testModules), initDatabase = false)
    }

    private fun getWithHeader(url: String, headerName: String, headerValue: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header(headerName, headerValue)
            .GET()
            .build()
        return TestHttp.client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `GET detect-country uses CloudFront viewer-country header when present`() {
        val handle = startTestServer()
        try {
            val response = getWithHeader("${handle.baseUrl}/api/detect-country", "CloudFront-Viewer-Country", "US")
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("United States"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET detect-country falls back to region subtag of locale query param when header absent`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/detect-country?locale=es-MX")
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Mexico"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET detect-country prefers CloudFront header over locale query param when both present`() {
        val handle = startTestServer()
        try {
            val request = HttpRequest.newBuilder(URI.create("${handle.baseUrl}/api/detect-country?locale=es-MX"))
                .header("CloudFront-Viewer-Country", "US")
                .GET()
                .build()
            val response = TestHttp.client.send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("United States"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET detect-country returns null country when no header and no locale param`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/detect-country")
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("null"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET detect-country returns null country when locale has no region subtag`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/detect-country?locale=en")
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("null"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET detect-country returns null country when locale is blank`() {
        val handle = startTestServer()
        try {
            val response = TestHttp.get("${handle.baseUrl}/api/detect-country?locale=")
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("null"))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET detect-country falls back to locale when CloudFront header value is unrecognized`() {
        val handle = startTestServer()
        try {
            val request = HttpRequest.newBuilder(URI.create("${handle.baseUrl}/api/detect-country?locale=fr-FR"))
                .header("CloudFront-Viewer-Country", "ZZ")
                .GET()
                .build()
            val response = TestHttp.client.send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("France"))
        } finally {
            handle.stop()
        }
    }
}

package com.adoptu.adapters.aws

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EcsTaskCredentialsProviderTest {

    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
        server = null
    }

    private fun startServer(status: Int, response: String): Pair<String, AtomicInteger> {
        val hitCount = AtomicInteger(0)
        val httpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        httpServer.createContext("/creds") { exchange ->
            hitCount.incrementAndGet()
            val bytes = response.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        httpServer.start()
        server = httpServer
        return "http://localhost:${httpServer.address.port}" to hitCount
    }

    @Test
    fun `resolveCredentials fetches and parses credentials from the ECS endpoint`() {
        val expiration = Instant.now().plusSeconds(3600).toString()
        val (host, hits) = startServer(
            200,
            """{"AccessKeyId": "AKIDEXAMPLE", "SecretAccessKey": "secret", "Token": "token123", "Expiration": "$expiration"}""",
        )
        val provider = EcsTaskCredentialsProvider(credentialsHost = host, relativeUriOverride = "/creds")

        val credentials = provider.resolveCredentials()

        assertEquals("AKIDEXAMPLE", credentials.accessKeyId())
        assertEquals("secret", credentials.secretAccessKey())
        assertTrue(credentials is AwsSessionCredentials)
        assertEquals("token123", (credentials as AwsSessionCredentials).sessionToken())
        assertEquals(1, hits.get())
    }

    @Test
    fun `resolveCredentials caches credentials and only hits the endpoint once`() {
        val expiration = Instant.now().plusSeconds(3600).toString()
        val (host, hits) = startServer(
            200,
            """{"AccessKeyId": "AKIDEXAMPLE", "SecretAccessKey": "secret", "Token": "token123", "Expiration": "$expiration"}""",
        )
        val provider = EcsTaskCredentialsProvider(credentialsHost = host, relativeUriOverride = "/creds")

        provider.resolveCredentials()
        provider.resolveCredentials()
        val third = provider.resolveCredentials()

        assertEquals(1, hits.get())
        assertEquals("AKIDEXAMPLE", third.accessKeyId())
    }

    @Test
    fun `resolveCredentials throws when the endpoint returns a non-200 status`() {
        val (host, _) = startServer(500, "internal error")
        val provider = EcsTaskCredentialsProvider(credentialsHost = host, relativeUriOverride = "/creds")

        val exception = assertFailsWith<IllegalStateException> { provider.resolveCredentials() }
        assertTrue(exception.message!!.contains("500"))
    }

    @Test
    fun `resolveCredentials throws when the relative URI is not set`() {
        val provider = EcsTaskCredentialsProvider(relativeUriOverride = null)

        assertFailsWith<IllegalArgumentException> { provider.resolveCredentials() }
    }

    @Test
    fun `ecsTaskCredentialsAvailable reflects the environment variable`() {
        // In the test JVM this env var is normally unset, matching production's non-ECS default.
        assertEquals(
            !System.getenv("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI").isNullOrBlank(),
            ecsTaskCredentialsAvailable(),
        )
    }

    @Test
    fun `EcsCredentialsPayload exposes its fields and supports equals, copy and toString`() {
        val payload = EcsCredentialsPayload(
            AccessKeyId = "AKID",
            SecretAccessKey = "secret",
            Token = "token",
            Expiration = "2030-01-01T00:00:00Z",
        )
        val same = payload.copy()
        val different = payload.copy(AccessKeyId = "OTHER")

        assertEquals("AKID", payload.AccessKeyId)
        assertEquals(payload, same)
        assertTrue(payload != different)
        assertTrue(payload.toString().contains("AKID"))
    }
}

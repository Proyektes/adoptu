package com.adoptu.adapters.captcha

import com.adoptu.ports.CaptchaPort
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TurnstileCaptchaAdapterTest {

    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
        server = null
    }

    private fun startServer(handler: (String) -> Pair<Int, String>): String {
        val httpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        httpServer.createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8)
            val (status, response) = handler(body)
            val bytes = response.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        httpServer.start()
        server = httpServer
        return "http://localhost:${httpServer.address.port}/turnstile/v0/siteverify"
    }

    @Test
    fun `verify returns true when Turnstile responds with success`() {
        val url = startServer { _ -> 200 to """{"success": true}""" }
        val adapter: CaptchaPort = TurnstileCaptchaAdapter(secretKey = "secret", verifyUrl = url)

        val result = runBlocking { adapter.verify("token", "1.2.3.4") }

        assertTrue(result)
    }

    @Test
    fun `verify returns false when Turnstile responds with failure`() {
        val url = startServer { _ -> 200 to """{"success": false, "error-codes": ["invalid-input-response"]}""" }
        val adapter: CaptchaPort = TurnstileCaptchaAdapter(secretKey = "secret", verifyUrl = url)

        val result = runBlocking { adapter.verify("bad-token", null) }

        assertFalse(result)
    }

    @Test
    fun `verify sends the secret, response and remoteip parameters`() {
        var receivedBody: String? = null
        val url = startServer { body ->
            receivedBody = body
            200 to """{"success": true}"""
        }
        val adapter: CaptchaPort = TurnstileCaptchaAdapter(secretKey = "my-secret", verifyUrl = url)

        runBlocking { adapter.verify("my-token", "9.9.9.9") }

        val decoded = URLDecoder.decode(receivedBody, StandardCharsets.UTF_8)
        assertTrue(decoded.contains("secret=my-secret"))
        assertTrue(decoded.contains("response=my-token"))
        assertTrue(decoded.contains("remoteip=9.9.9.9"))
    }

    @Test
    fun `verify returns false on a non-200 HTTP status`() {
        val url = startServer { _ -> 500 to "internal error" }
        val adapter: CaptchaPort = TurnstileCaptchaAdapter(secretKey = "secret", verifyUrl = url)

        // A non-200 body isn't valid JSON here so this also exercises the parse-failure path,
        // but assert on the documented "unexpected response" behaviour: verify() swallows any
        // exception and returns false.
        val result = runBlocking { adapter.verify("token", null) }

        assertFalse(result)
    }

    @Test
    fun `verify returns false when the endpoint is unreachable`() {
        // Nothing is listening on this port - the HttpClient.send() call throws, exercising the
        // catch (e Exception) branch.
        val adapter: CaptchaPort = TurnstileCaptchaAdapter(
            secretKey = "secret",
            verifyUrl = "http://localhost:1/turnstile/v0/siteverify",
        )

        val result = runBlocking { adapter.verify("token", null) }

        assertFalse(result)
    }
}

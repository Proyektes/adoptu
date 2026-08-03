package com.adoptu.plugins

import com.adoptu.testsupport.TestHttp
import com.adoptu.testsupport.TestServer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the real production route wiring in configureRouting() -- every other E2E test
 * mounts an individual route function directly, so this top-level wiring function (and its
 * /health endpoint) was otherwise never covered. TestServer.start() creates its own H2 schema
 * from scratch per call, so no separate TestDatabase.initH2() call is needed here.
 */
class RoutingTest {

    @Test
    fun `GET health returns ok status`() {
        val handle = TestServer.start()
        try {
            val response = TestHttp.get("${handle.baseUrl}/health")
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("\"status\""))
            assertTrue(response.body().contains("\"ok\""))
        } finally {
            handle.stop()
        }
    }

    @Test
    fun `GET unknown route returns 404`() {
        val handle = TestServer.start()
        try {
            val response = TestHttp.get("${handle.baseUrl}/this-route-does-not-exist")
            assertEquals(404, response.statusCode())
        } finally {
            handle.stop()
        }
    }

    // "GET / is served through the full route tree" used to live here - the backend is API-only
    // now (page rendering moved to frontend's static site, see SiteGenerator.kt), so "/" is just
    // another unknown route and correctly 404s like any other, covered by the test above.
}

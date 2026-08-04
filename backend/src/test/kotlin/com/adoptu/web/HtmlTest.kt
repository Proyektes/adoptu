package com.adoptu.web

import io.helidon.http.HeaderNames
import io.helidon.http.Status
import io.helidon.webserver.http.ServerResponse
import io.mockk.mockk
import io.mockk.verify
import kotlinx.html.body
import org.junit.jupiter.api.Test

/**
 * Unit test for [respondHtml]. The backend is JSON-API-only since the static-site migration (see
 * frontend/src/jvmMain/kotlin/com/adoptu/site/SiteGenerator.kt) - the only production call site
 * left is AuthRoutes.kt's magic-link-login same-origin cookie bounce, which omits `status` (relies
 * on the `Status.OK_200` default). Covered directly here rather than through the E2E route suite
 * since that flow needs a real magic-link token to exercise.
 */
class HtmlTest {

    @Test
    fun `respondHtml uses OK_200 by default and writes an html content type`() {
        val response = mockk<ServerResponse>(relaxed = true)

        response.respondHtml { body { } }

        verify { response.status(Status.OK_200) }
        verify { response.header(HeaderNames.CONTENT_TYPE, "text/html; charset=utf-8") }
        verify { response.send(any<ByteArray>()) }
    }

    @Test
    fun `respondHtml honors an explicit status`() {
        val response = mockk<ServerResponse>(relaxed = true)

        response.respondHtml(Status.NOT_FOUND_404) { body { } }

        verify { response.status(Status.NOT_FOUND_404) }
    }
}

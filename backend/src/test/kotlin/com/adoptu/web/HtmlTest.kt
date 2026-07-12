package com.adoptu.web

import io.helidon.http.HeaderNames
import io.helidon.http.Status
import io.helidon.webserver.http.ServerResponse
import io.mockk.mockk
import io.mockk.verify
import kotlinx.html.body
import org.junit.jupiter.api.Test

/**
 * Unit test for [respondHtml]. Every production call site (see UIRoutes.kt) passes the `status`
 * argument explicitly, so Kover never exercises the default-parameter dispatch for
 * `status: Status = Status.OK_200` through the E2E route suite - this test calls the extension
 * directly, omitting `status`, to cover that default-value branch.
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

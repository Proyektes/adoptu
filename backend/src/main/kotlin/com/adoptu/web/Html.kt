package com.adoptu.web

import io.helidon.http.HeaderNames
import io.helidon.http.Status
import io.helidon.webserver.http.ServerResponse
import kotlinx.html.HTML
import kotlinx.html.html
import kotlinx.html.stream.appendHTML

/** Replaces Ktor's `call.respondHtml { ... }` (ktor-server-html-builder). */
fun ServerResponse.respondHtml(status: Status = Status.OK_200, block: HTML.() -> Unit) {
    val html = buildString { appendHTML().html(block = block) }
    status(status)
    header(HeaderNames.CONTENT_TYPE, "text/html; charset=utf-8")
    send(html.toByteArray(Charsets.UTF_8))
}

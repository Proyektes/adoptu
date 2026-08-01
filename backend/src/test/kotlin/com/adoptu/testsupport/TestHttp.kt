package com.adoptu.testsupport

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse

/**
 * Thin java.net.http.HttpClient helpers replacing Ktor's test HttpClient, for use against a
 * TestServerHandle's baseUrl. Every call returns the raw HttpResponse<String> - assert on
 * `.statusCode()` and `.body()` directly (mirrors Ktor's `response.status`/`response.bodyAsText()`).
 */
object TestHttp {
    val client: HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    fun get(url: String, cookie: String? = null): HttpResponse<String> =
        send(requestBuilder(url, cookie).GET())

    fun delete(url: String, cookie: String? = null): HttpResponse<String> =
        send(requestBuilder(url, cookie).DELETE())

    fun post(url: String, cookie: String? = null): HttpResponse<String> =
        send(requestBuilder(url, cookie).POST(BodyPublishers.noBody()))

    fun postJson(url: String, json: String, cookie: String? = null): HttpResponse<String> =
        send(requestBuilder(url, cookie).header("Content-Type", "application/json").POST(BodyPublishers.ofString(json)))

    fun postForm(url: String, form: String, cookie: String? = null): HttpResponse<String> =
        send(requestBuilder(url, cookie).header("Content-Type", "application/x-www-form-urlencoded").POST(BodyPublishers.ofString(form)))

    fun putJson(url: String, json: String, cookie: String? = null): HttpResponse<String> =
        send(requestBuilder(url, cookie).header("Content-Type", "application/json").method("PUT", BodyPublishers.ofString(json)))

    fun putForm(url: String, form: String, cookie: String? = null): HttpResponse<String> =
        send(requestBuilder(url, cookie).header("Content-Type", "application/x-www-form-urlencoded").method("PUT", BodyPublishers.ofString(form)))

    fun put(url: String, cookie: String? = null): HttpResponse<String> =
        send(requestBuilder(url, cookie).method("PUT", BodyPublishers.noBody()))

    fun multipart(url: String, boundary: String, body: ByteArray, cookie: String? = null): HttpResponse<String> =
        send(
            requestBuilder(url, cookie)
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(BodyPublishers.ofByteArray(body))
        )

    /** Logs in as [userId] against the test-only `/test/login/{userId}` route, returning a
     *  `"name1=value1; name2=value2"` Cookie header covering every cookie the route set (it sets
     *  both the native session cookie and, best-effort, an AuthKit access-token cookie -- a
     *  response can carry multiple Set-Cookie headers, and `firstValue` silently drops all but the
     *  first, which previously meant only the session cookie made it back to the caller). */
    fun loginAs(baseUrl: String, userId: Int): String {
        val response = send(requestBuilder("$baseUrl/test/login/$userId", null).POST(BodyPublishers.noBody()))
        val setCookies = response.headers().allValues("Set-Cookie")
        if (setCookies.isEmpty()) throw IllegalStateException("No session cookie returned from test login")
        return setCookies.joinToString("; ") { it.substringBefore(";") }
    }

    private fun requestBuilder(url: String, cookie: String?): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder(URI.create(url))
        if (cookie != null) builder.header("Cookie", cookie)
        return builder
    }

    private fun send(builder: HttpRequest.Builder): HttpResponse<String> =
        client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
}

/** Builds a multipart/form-data body: fields is name->value, files is name->Triple(filename, contentType, bytes). */
fun buildMultipartBody(
    boundary: String,
    fields: Map<String, String> = emptyMap(),
    files: Map<String, Triple<String, String, ByteArray>> = emptyMap()
): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    fun writeLine(s: String) = out.write((s + "\r\n").toByteArray(Charsets.UTF_8))

    fields.forEach { (name, value) ->
        writeLine("--$boundary")
        writeLine("Content-Disposition: form-data; name=\"$name\"")
        writeLine("")
        writeLine(value)
    }
    files.forEach { (name, fileInfo) ->
        val (filename, contentType, bytes) = fileInfo
        writeLine("--$boundary")
        writeLine("Content-Disposition: form-data; name=\"$name\"; filename=\"$filename\"")
        writeLine("Content-Type: $contentType")
        writeLine("")
        out.write(bytes)
        out.write("\r\n".toByteArray(Charsets.UTF_8))
    }
    writeLine("--$boundary--")
    return out.toByteArray()
}

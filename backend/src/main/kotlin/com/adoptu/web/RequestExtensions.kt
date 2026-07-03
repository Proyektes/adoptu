package com.adoptu.web

import io.helidon.http.media.multipart.MultiPart
import io.helidon.webserver.http.ServerRequest
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Path template parameter, e.g. `req.pathParam("id")` for a route registered as `"/pets/{id}"`. */
fun ServerRequest.pathParam(name: String): String = path().pathParameters().get(name)

/** Optional query-string parameter. */
fun ServerRequest.queryParam(name: String): String? = query().first(name).orElse(null)

/** Reads and Jackson-deserializes the request body. */
inline fun <reified T> ServerRequest.receiveJson(): T = content().`as`(T::class.java)

fun ServerRequest.receiveText(): String {
    val entity = content()
    return if (entity.hasEntity()) entity.`as`(String::class.java) else ""
}

/** Parses an `application/x-www-form-urlencoded` body into a name->value map (last value wins per key). */
fun ServerRequest.receiveFormParameters(): Map<String, String> {
    val body = receiveText()
    if (body.isBlank()) return emptyMap()
    return body.split("&")
        .mapNotNull { pair ->
            if (pair.isBlank()) return@mapNotNull null
            val idx = pair.indexOf('=')
            val key = if (idx >= 0) pair.substring(0, idx) else pair
            val value = if (idx >= 0) pair.substring(idx + 1) else ""
            URLDecoder.decode(key, StandardCharsets.UTF_8) to URLDecoder.decode(value, StandardCharsets.UTF_8)
        }
        .toMap()
}

data class ReceivedFilePart(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray
)

/**
 * Iterates a multipart body, returning the first file part (if any) and a map of plain
 * form-field parts by name - covers the PetsRoutes.kt image-upload shape (one file + a few
 * form fields like `isPrimary`).
 */
fun ServerRequest.receiveMultipart(): Pair<ReceivedFilePart?, Map<String, String>> {
    val multiPart = content().`as`(MultiPart::class.java)
    var file: ReceivedFilePart? = null
    val fields = mutableMapOf<String, String>()
    while (multiPart.hasNext()) {
        val part = multiPart.next()
        if (part.fileName().isPresent) {
            file = ReceivedFilePart(
                fileName = part.fileName().get(),
                contentType = part.contentType().toString(),
                bytes = part.inputStream().readBytes()
            )
        } else {
            fields[part.name()] = String(part.inputStream().readBytes(), StandardCharsets.UTF_8)
        }
    }
    return file to fields
}

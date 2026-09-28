package com.adoptu.web

import com.universaliun.formats.json.generated.decodeAsErrorResponse
import com.universaliun.formats.json.generated.decodeAsSuccessResponse
import com.universaliun.formats.json.generated.encodeToJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * `ErrorResponse`/`SuccessResponse` are the shape of every `respondError`/`respondSuccess` call
 * in `Responses.kt` - unlike the DTOs under `dto/`, these are the one wire-visible pair
 * `web/JsonSupport.kt` had to register from outside the `dto` package.
 */
class JsonResponsesTest {

    @Test
    fun `ErrorResponse round-trips`() {
        val original = ErrorResponse(error = "Not found")
        assertEquals(original, original.encodeToJson().decodeAsErrorResponse())
    }

    @Test
    fun `SuccessResponse round-trips`() {
        val original = SuccessResponse(success = true)
        assertEquals(original, original.encodeToJson().decodeAsSuccessResponse())
    }
}

package com.adoptu.dto.input

import com.universaliun.formats.json.generated.decodeAsCreateSavedSearchRequest
import com.universaliun.formats.json.generated.decodeAsSavedSearchDto
import com.universaliun.formats.json.generated.encodeToJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SavedSearchDtoTest {

    @Test
    fun `SavedSearchDto round-trips`() {
        val original = SavedSearchDto(id = 1, userId = 2, type = "Dog", country = "MX", createdAt = 1_700_000_000_000)
        assertEquals(original, original.encodeToJson().decodeAsSavedSearchDto())
    }

    @Test
    fun `SavedSearchDto round-trips a null type (any type)`() {
        val original = SavedSearchDto(id = 1, userId = 2, type = null, country = "MX", createdAt = 1_700_000_000_000)
        assertEquals(original, original.encodeToJson().decodeAsSavedSearchDto())
    }

    @Test
    fun `CreateSavedSearchRequest round-trips`() {
        val original = CreateSavedSearchRequest(type = "Cat", country = "US")
        assertEquals(original, original.encodeToJson().decodeAsCreateSavedSearchRequest())
    }
}

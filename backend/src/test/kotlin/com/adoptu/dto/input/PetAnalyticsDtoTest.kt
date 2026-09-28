package com.adoptu.dto.input

import com.universaliun.formats.json.generated.decodeAsPetAnalyticsDto
import com.universaliun.formats.json.generated.encodeToJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PetAnalyticsDtoTest {

    @Test
    fun `PetAnalyticsDto round-trips`() {
        val original = PetAnalyticsDto(petId = 1, viewCount = 100, inquiryCount = 5, approvedCount = 2, conversionRate = 0.05)
        assertEquals(original, original.encodeToJson().decodeAsPetAnalyticsDto())
    }

    @Test
    fun `PetAnalyticsDto keeps conversionRate as an explicit null, not omitted`() {
        val original = PetAnalyticsDto(petId = 1, viewCount = 0, inquiryCount = 0, approvedCount = 0, conversionRate = null)
        val json = original.encodeToJson().decodeToString()
        assertTrue(json.contains("\"conversionRate\":null"))
        assertEquals(original, original.encodeToJson().decodeAsPetAnalyticsDto())
    }
}

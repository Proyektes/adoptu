package com.adoptu.dto.input

import com.universaliun.formats.json.generated.decodeAsContactLostFoundReporterRequest
import com.universaliun.formats.json.generated.decodeAsLostFoundReportDto
import com.universaliun.formats.json.generated.decodeAsSubmitLostFoundReportRequest
import com.universaliun.formats.json.generated.encodeToJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LostFoundDtoTest {

    @Test
    fun `LostFoundReportDto round-trips`() {
        val original = LostFoundReportDto(
            id = 1,
            kind = LostFoundKind.LOST,
            reporterUserId = 2,
            reporterEmail = "reporter@example.com",
            reporterPhone = "555-0000",
            petType = "Dog",
            description = "Brown lab",
            photoUrl = "https://x/1.jpg",
            latitude = 19.43,
            longitude = -99.13,
            locationLabel = "Downtown",
            country = "MX",
            lastSeenAt = 1_700_000_000_000,
            status = LostFoundStatus.OPEN,
            createdAt = 1_700_000_001_000,
            resolveToken = "tok-123",
        )
        assertEquals(original, original.encodeToJson().decodeAsLostFoundReportDto())
    }

    @Test
    fun `LostFoundReportDto round-trips with every nullable field absent`() {
        val original = LostFoundReportDto(
            id = 1,
            kind = LostFoundKind.FOUND,
            reporterEmail = "r@example.com",
            description = "Grey cat",
            latitude = 0.0,
            longitude = 0.0,
            locationLabel = "Park",
            country = "US",
            lastSeenAt = 1_700_000_000_000,
            status = LostFoundStatus.RESOLVED,
            createdAt = 1_700_000_001_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsLostFoundReportDto())
    }

    @Test
    fun `SubmitLostFoundReportRequest round-trips`() {
        val original = SubmitLostFoundReportRequest(
            kind = LostFoundKind.LOST,
            petType = "Cat",
            description = "Orange tabby",
            reporterEmail = "anon@example.com",
            reporterPhone = "555-1111",
            captchaToken = "captcha",
            country = "MX",
            state = "CDMX",
            city = "Coyoacan",
            latitude = 19.35,
            longitude = -99.16,
            lastSeenAt = 1_700_000_002_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsSubmitLostFoundReportRequest())
    }

    @Test
    fun `ContactLostFoundReporterRequest round-trips`() {
        val original = ContactLostFoundReporterRequest(fromEmail = "me@example.com", message = "I found your pet")
        assertEquals(original, original.encodeToJson().decodeAsContactLostFoundReporterRequest())
    }
}

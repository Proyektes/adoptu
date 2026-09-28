package com.adoptu.dto.input

import com.universaliun.formats.json.generated.decodeAsCreateUrgentRescuerProfileRequest
import com.universaliun.formats.json.generated.decodeAsSubmitUrgentReportRequest
import com.universaliun.formats.json.generated.decodeAsUpdateUrgentRescuerProfileRequest
import com.universaliun.formats.json.generated.decodeAsUrgentReportDto
import com.universaliun.formats.json.generated.decodeAsUrgentReportPageDto
import com.universaliun.formats.json.generated.decodeAsUrgentRescuerLeaderboardEntryDto
import com.universaliun.formats.json.generated.decodeAsUrgentRescuerProfileDto
import com.universaliun.formats.json.generated.encodeToJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class UrgentRescueDtoTest {

    @Test
    fun `UrgentRescuerProfileDto round-trips a COORDINATES profile`() {
        val original = UrgentRescuerProfileDto(
            userId = 1, phone = "555-0000", latitude = 19.43, longitude = -99.13, radiusKm = 5.0,
            inputMode = LocationInputMode.COORDINATES, active = true, createdAt = 1_700_000_000_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsUrgentRescuerProfileDto())
    }

    @Test
    fun `UrgentRescuerProfileDto round-trips a ZONE profile`() {
        val original = UrgentRescuerProfileDto(
            userId = 1, phone = "555-0000", latitude = 19.43, longitude = -99.13, radiusKm = 5.0,
            inputMode = LocationInputMode.ZONE, zoneCountry = "MX", zoneState = "CDMX", zoneCity = "Centro",
            active = false, createdAt = 1_700_000_000_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsUrgentRescuerProfileDto())
    }

    @Test
    fun `CreateUrgentRescuerProfileRequest round-trips`() {
        val original = CreateUrgentRescuerProfileRequest(
            phone = "555-0000", inputMode = LocationInputMode.COORDINATES, latitude = 19.43,
            longitude = -99.13, radiusKm = 5.0,
        )
        assertEquals(original, original.encodeToJson().decodeAsCreateUrgentRescuerProfileRequest())
    }

    @Test
    fun `UpdateUrgentRescuerProfileRequest round-trips an all-null instance`() {
        val original = UpdateUrgentRescuerProfileRequest()
        assertEquals(original, original.encodeToJson().decodeAsUpdateUrgentRescuerProfileRequest())
    }

    @Test
    fun `UrgentReportDto round-trips every UrgentReportStatus and UrgentDangerType value`() {
        for (status in UrgentReportStatus.entries) {
            for (danger in UrgentDangerType.entries) {
                val original = UrgentReportDto(
                    id = 1, reporterUserId = 2, reporterEmail = "r@example.com", reporterPhone = "555-0000",
                    description = "Injured dog", dangerType = danger, photoUrl = "https://x/1.jpg",
                    latitude = 19.43, longitude = -99.13, locationLabel = "Downtown", street = "Calle 1",
                    exteriorNumber = "10", referenceNotes = "Blue house", status = status,
                    acceptedByUserId = 3, acceptedByName = "Rescuer", acceptedAt = 1_700_000_001_000,
                    createdAt = 1_700_000_000_000,
                )
                assertEquals(original, original.encodeToJson().decodeAsUrgentReportDto())
            }
        }
    }

    @Test
    fun `SubmitUrgentReportRequest round-trips`() {
        val original = SubmitUrgentReportRequest(
            description = "Injured cat", dangerType = UrgentDangerType.INJURED, reporterEmail = "r@example.com",
            reporterPhone = "555-0000", captchaToken = "captcha", latitude = 19.43, longitude = -99.13,
            country = "MX", state = "CDMX", city = "Centro", street = "Calle 1", exteriorNumber = "10",
            referenceNotes = "Blue house",
        )
        assertEquals(original, original.encodeToJson().decodeAsSubmitUrgentReportRequest())
    }

    @Test
    fun `UrgentReportPageDto round-trips every UrgentReportPageStatus value`() {
        for (status in UrgentReportPageStatus.entries) {
            val original = UrgentReportPageDto(id = 1, reportId = 2, rescuerId = 3, token = "tok-123", status = status, createdAt = 1_700_000_000_000)
            assertEquals(original, original.encodeToJson().decodeAsUrgentReportPageDto())
        }
    }

    @Test
    fun `UrgentRescuerLeaderboardEntryDto round-trips`() {
        val original = UrgentRescuerLeaderboardEntryDto(userId = 1, displayName = "Rescuer", acceptedCount = 7)
        assertEquals(original, original.encodeToJson().decodeAsUrgentRescuerLeaderboardEntryDto())
    }
}

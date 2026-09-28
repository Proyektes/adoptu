package com.adoptu.dto.input

import com.universaliun.formats.json.generated.decodeAsCreateVolunteerApplicationRequest
import com.universaliun.formats.json.generated.decodeAsUpdateVolunteerStatusRequest
import com.universaliun.formats.json.generated.decodeAsVolunteerDto
import com.universaliun.formats.json.generated.encodeToJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class VolunteerDtoTest {

    @Test
    fun `VolunteerDto round-trips`() {
        val original = VolunteerDto(
            id = 1,
            rescuerId = 2,
            rescuerName = "Rescuer",
            volunteerId = 3,
            volunteerName = "Volunteer",
            status = VolunteerStatus.ACTIVE,
            createdAt = 1_700_000_000_000,
        )
        assertEquals(original, original.encodeToJson().decodeAsVolunteerDto())
    }

    @Test
    fun `CreateVolunteerApplicationRequest round-trips`() {
        val original = CreateVolunteerApplicationRequest(rescuerId = 2)
        assertEquals(original, original.encodeToJson().decodeAsCreateVolunteerApplicationRequest())
    }

    @Test
    fun `UpdateVolunteerStatusRequest round-trips every status value`() {
        for (status in VolunteerStatus.entries) {
            val original = UpdateVolunteerStatusRequest(status = status)
            assertEquals(original, original.encodeToJson().decodeAsUpdateVolunteerStatusRequest())
        }
    }
}

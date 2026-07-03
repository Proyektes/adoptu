package com.adoptu.services.auth

import com.adoptu.web.JsonSupport
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SessionUserTest {

    @Test
    fun `constructs with given fields`() {
        val user = SessionUser(userId = 1, email = "user@example.com", displayName = "Test User")

        assertEquals(1, user.userId)
        assertEquals("user@example.com", user.email)
        assertEquals("Test User", user.displayName)
    }

    @Test
    fun `equals and hashCode follow data class semantics`() {
        val user1 = SessionUser(userId = 1, email = "user@example.com", displayName = "Test User")
        val user2 = SessionUser(userId = 1, email = "user@example.com", displayName = "Test User")
        val user3 = SessionUser(userId = 2, email = "other@example.com", displayName = "Other User")

        assertEquals(user1, user2)
        assertEquals(user1.hashCode(), user2.hashCode())
        assertNotEquals(user1, user3)
    }

    @Test
    fun `toString contains field values`() {
        val user = SessionUser(userId = 7, email = "test@adopt-u.com", displayName = "Display Name")

        val text = user.toString()

        assertTrue(text.contains("7"))
        assertTrue(text.contains("test@adopt-u.com"))
        assertTrue(text.contains("Display Name"))
    }

    @Test
    fun `serializes and deserializes round trip via Jackson`() {
        val user = SessionUser(userId = 42, email = "round@trip.com", displayName = "Round Tripper")

        val json = JsonSupport.objectMapper.writeValueAsString(user)
        val decoded = JsonSupport.objectMapper.readValue(json, SessionUser::class.java)

        assertEquals(user, decoded)
        assertTrue(json.contains("\"userId\": 42"))
        assertTrue(json.contains("round@trip.com"))
        assertTrue(json.contains("Round Tripper"))
    }
}

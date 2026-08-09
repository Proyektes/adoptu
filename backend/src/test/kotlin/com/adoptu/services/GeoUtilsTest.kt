package com.adoptu.services

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoUtilsTest {

    @Test
    fun `distance between a point and itself is zero`() {
        assertEquals(0.0, haversineDistanceKm(19.4326, -99.1332, 19.4326, -99.1332))
    }

    @Test
    fun `distance between Mexico City and Guadalajara is roughly 460km`() {
        val km = haversineDistanceKm(19.4326, -99.1332, 20.6597, -103.3496)
        assertTrue(abs(km - 460.0) < 15.0, "expected ~460km, got $km")
    }

    @Test
    fun `distance is symmetric`() {
        val a = haversineDistanceKm(19.4326, -99.1332, 20.6597, -103.3496)
        val b = haversineDistanceKm(20.6597, -103.3496, 19.4326, -99.1332)
        assertEquals(a, b, 0.0001)
    }
}

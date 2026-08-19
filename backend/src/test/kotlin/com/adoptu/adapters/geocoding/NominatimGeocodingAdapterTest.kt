package com.adoptu.adapters.geocoding

import com.adoptu.ports.GeocodingPort
import com.adoptu.services.haversineDistanceKm
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NominatimGeocodingAdapterTest {

    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
        server = null
    }

    private fun startServer(status: Int, response: String): String {
        val httpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        httpServer.createContext("/search") { exchange ->
            val bytes = response.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        httpServer.start()
        server = httpServer
        return "http://localhost:${httpServer.address.port}/search"
    }

    private fun startReverseServer(status: Int, response: String): String {
        val httpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        httpServer.createContext("/reverse") { exchange ->
            val bytes = response.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        httpServer.start()
        server = httpServer
        return "http://localhost:${httpServer.address.port}/reverse"
    }

    @Test
    fun `geocode returns lat lon and a radius derived from the boundingbox`() {
        val url = startServer(
            200,
            """[{"lat": "19.4326", "lon": "-99.1332", "boundingbox": ["19.30", "19.55", "-99.25", "-99.00"]}]""",
        )
        val adapter: GeocodingPort = NominatimGeocodingAdapter(baseUrl = url)

        val result = runBlocking { adapter.geocode("Mexico", "CDMX", "Mexico City") }

        requireNotNull(result)
        assertEquals(19.4326, result.latitude, 0.0001)
        assertEquals(-99.1332, result.longitude, 0.0001)

        val expectedRadius = maxOf(
            haversineDistanceKm(19.4326, -99.1332, 19.30, -99.25),
            haversineDistanceKm(19.4326, -99.1332, 19.30, -99.00),
            haversineDistanceKm(19.4326, -99.1332, 19.55, -99.25),
            haversineDistanceKm(19.4326, -99.1332, 19.55, -99.00),
        )
        assertTrue(abs(result.radiusKm - expectedRadius) < 0.0001)
    }

    @Test
    fun `geocode falls back to a 15km radius when there is no boundingbox`() {
        val url = startServer(200, """[{"lat": "40.7128", "lon": "-74.0060"}]""")
        val adapter: GeocodingPort = NominatimGeocodingAdapter(baseUrl = url)

        val result = runBlocking { adapter.geocode("USA", null, "New York") }

        requireNotNull(result)
        assertEquals(15.0, result.radiusKm)
    }

    @Test
    fun `geocode returns null when Nominatim returns an empty result array`() {
        val url = startServer(200, "[]")
        val adapter: GeocodingPort = NominatimGeocodingAdapter(baseUrl = url)

        val result = runBlocking { adapter.geocode("Nowhere", null, "Nowhere City") }

        assertNull(result)
    }

    @Test
    fun `geocode returns null on a non-200 HTTP status`() {
        val url = startServer(503, "service unavailable")
        val adapter: GeocodingPort = NominatimGeocodingAdapter(baseUrl = url)

        val result = runBlocking { adapter.geocode("Somewhere", null, "Some City") }

        assertNull(result)
    }

    @Test
    fun `geocode returns null when the response body is malformed`() {
        val url = startServer(200, """[{"lat": "not-a-number", "lon": "-74.0060"}]""")
        val adapter: GeocodingPort = NominatimGeocodingAdapter(baseUrl = url)

        val result = runBlocking { adapter.geocode("Somewhere", null, "Some City") }

        assertNull(result)
    }

    @Test
    fun `geocode returns null when the endpoint is unreachable`() {
        val adapter: GeocodingPort = NominatimGeocodingAdapter(baseUrl = "http://localhost:1/search")

        val result = runBlocking { adapter.geocode("Somewhere", null, "Some City") }

        assertNull(result)
    }

    @Test
    fun `reverseGeocode returns the address fields Nominatim provides`() {
        val url = startReverseServer(
            200,
            """{"address": {"road": "Av. Insurgentes Sur", "house_number": "123", "city": "Mexico City", "state": "CDMX", "country": "Mexico"}}""",
        )
        val adapter: GeocodingPort = NominatimGeocodingAdapter(reverseBaseUrl = url)

        val result = runBlocking { adapter.reverseGeocode(19.4326, -99.1332) }

        requireNotNull(result)
        assertEquals("Av. Insurgentes Sur", result.street)
        assertEquals("123", result.houseNumber)
        assertEquals("Mexico City", result.city)
        assertEquals("CDMX", result.state)
        assertEquals("Mexico", result.country)
    }

    @Test
    fun `reverseGeocode falls back through town then village when city is absent`() {
        val url = startReverseServer(200, """{"address": {"village": "Small Village", "country": "Nowhereland"}}""")
        val adapter: GeocodingPort = NominatimGeocodingAdapter(reverseBaseUrl = url)

        val result = runBlocking { adapter.reverseGeocode(0.0, 0.0) }

        requireNotNull(result)
        assertEquals("Small Village", result.city)
        assertNull(result.street)
    }

    @Test
    fun `reverseGeocode returns null when the response has no address object`() {
        val url = startReverseServer(200, """{"error": "Unable to geocode"}""")
        val adapter: GeocodingPort = NominatimGeocodingAdapter(reverseBaseUrl = url)

        val result = runBlocking { adapter.reverseGeocode(0.0, 0.0) }

        assertNull(result)
    }

    @Test
    fun `reverseGeocode returns null on a non-200 HTTP status`() {
        val url = startReverseServer(503, "service unavailable")
        val adapter: GeocodingPort = NominatimGeocodingAdapter(reverseBaseUrl = url)

        val result = runBlocking { adapter.reverseGeocode(0.0, 0.0) }

        assertNull(result)
    }

    @Test
    fun `reverseGeocode returns null when the endpoint is unreachable`() {
        val adapter: GeocodingPort = NominatimGeocodingAdapter(reverseBaseUrl = "http://localhost:1/reverse")

        val result = runBlocking { adapter.reverseGeocode(0.0, 0.0) }

        assertNull(result)
    }
}

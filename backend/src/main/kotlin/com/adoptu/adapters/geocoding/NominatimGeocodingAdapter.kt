package com.adoptu.adapters.geocoding

import com.adoptu.ports.GeocodeResult
import com.adoptu.ports.GeocodingPort
import com.adoptu.ports.ReverseGeocodeResult
import com.adoptu.services.haversineDistanceKm
import com.adoptu.web.JsonSupport
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

private val logger = LoggerFactory.getLogger("AdoptU-Geocoding")

/**
 * Free, no-API-key geocoding via OpenStreetMap's public Nominatim instance. Its usage policy
 * (https://operations.osmfoundation.org/policies/nominatim/) requires a descriptive User-Agent
 * identifying the application and caps usage at ~1 request/second - fine for this project's
 * volume (urgent-rescuer profile setup + report submissions where the reporter didn't grant
 * browser Geolocation), but this must never be called in a tight loop.
 *
 * Radius derivation: Nominatim returns a boundingbox [south, north, west, east]; the stored
 * radius is the haversine distance from the returned center point to the farthest bounding-box
 * corner, so the circle always fully contains the box (may over-cover non-square zones, which is
 * the safe direction to err for an urgency-matching feature).
 */
class NominatimGeocodingAdapter(
    private val baseUrl: String = "https://nominatim.openstreetmap.org/search",
    private val reverseBaseUrl: String = "https://nominatim.openstreetmap.org/reverse",
) : GeocodingPort {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    override suspend fun geocode(country: String, state: String?, city: String): GeocodeResult? {
        val query = listOfNotNull(city, state, country).joinToString(", ")
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val uri = URI.create("$baseUrl?q=$encoded&format=json&limit=1")

        val request = HttpRequest.newBuilder(uri)
            .header("User-Agent", "Adopt-U (adopt-u.org, urgent-rescuer geocoding)")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.warn("Nominatim geocode failed for '$query': HTTP ${response.statusCode()}")
                return null
            }

            val results = JsonSupport.objectMapper.readTree(response.body())
            val first = results.firstOrNull() ?: return null

            val lat = first["lat"].asText().toDouble()
            val lon = first["lon"].asText().toDouble()
            val bbox = first["boundingbox"]
            val radiusKm = if (bbox != null && bbox.isArray && bbox.size() == 4) {
                val south = bbox[0].asText().toDouble()
                val north = bbox[1].asText().toDouble()
                val west = bbox[2].asText().toDouble()
                val east = bbox[3].asText().toDouble()
                maxOf(
                    haversineDistanceKm(lat, lon, south, west),
                    haversineDistanceKm(lat, lon, south, east),
                    haversineDistanceKm(lat, lon, north, west),
                    haversineDistanceKm(lat, lon, north, east)
                )
            } else {
                // No bounding box in the response - fall back to a conservative default city-scale radius.
                15.0
            }

            GeocodeResult(latitude = lat, longitude = lon, radiusKm = radiusKm)
        } catch (e: Exception) {
            logger.error("Nominatim geocode error for '$query'", e)
            null
        }
    }

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseGeocodeResult? {
        // accept-language=en pins Nominatim's address fields to English regardless of the
        // location's local language - the report form's country <select> is keyed by English
        // country names (see I18n.kt's countryKeyByEnglishName), so a localized name like
        // "México" wouldn't match any <option> and the field would silently stay unselected.
        val uri = URI.create("$reverseBaseUrl?lat=$latitude&lon=$longitude&format=json&zoom=18&addressdetails=1&accept-language=en")

        val request = HttpRequest.newBuilder(uri)
            .header("User-Agent", "Adopt-U (adopt-u.org, urgent-rescuer geocoding)")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                logger.warn("Nominatim reverseGeocode failed for ($latitude, $longitude): HTTP ${response.statusCode()}")
                return null
            }

            val address = JsonSupport.objectMapper.readTree(response.body())["address"] ?: return null
            ReverseGeocodeResult(
                street = address["road"]?.asText(),
                houseNumber = address["house_number"]?.asText(),
                // Nominatim uses whichever of these applies to the locality's size - no single
                // field is populated for every place, so this is the standard fallback chain.
                city = address["city"]?.asText() ?: address["town"]?.asText() ?: address["village"]?.asText(),
                state = address["state"]?.asText(),
                country = address["country"]?.asText()
            )
        } catch (e: Exception) {
            logger.error("Nominatim reverseGeocode error for ($latitude, $longitude)", e)
            null
        }
    }
}

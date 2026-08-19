package com.adoptu.mocks

import com.adoptu.ports.GeocodeResult
import com.adoptu.ports.GeocodingPort
import com.adoptu.ports.ReverseGeocodeResult

/** Returns a canned [GeocodeResult] for any (country, state, city) present in [results]; null for
 *  anything else, matching [GeocodingPort.geocode]'s documented "not found" contract. Same for
 *  [reverseResults], keyed by (latitude, longitude). */
class FakeGeocodingPort(
    private val results: Map<Triple<String, String?, String>, GeocodeResult> = emptyMap(),
    private val reverseResults: Map<Pair<Double, Double>, ReverseGeocodeResult> = emptyMap()
) : GeocodingPort {
    override suspend fun geocode(country: String, state: String?, city: String): GeocodeResult? =
        results[Triple(country, state, city)]

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseGeocodeResult? =
        reverseResults[Pair(latitude, longitude)]
}

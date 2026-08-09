package com.adoptu.mocks

import com.adoptu.ports.GeocodeResult
import com.adoptu.ports.GeocodingPort

/** Returns a canned [GeocodeResult] for any (country, state, city) present in [results]; null for
 *  anything else, matching [GeocodingPort.geocode]'s documented "not found" contract. */
class FakeGeocodingPort(
    private val results: Map<Triple<String, String?, String>, GeocodeResult> = emptyMap()
) : GeocodingPort {
    override suspend fun geocode(country: String, state: String?, city: String): GeocodeResult? =
        results[Triple(country, state, city)]
}

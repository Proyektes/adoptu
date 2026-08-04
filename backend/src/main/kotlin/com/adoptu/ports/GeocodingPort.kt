package com.adoptu.ports

data class GeocodeResult(val latitude: Double, val longitude: Double, val radiusKm: Double)

interface GeocodingPort {
    /** Resolves a country/state/city into a center point + a radius that fully covers the zone's bounding box. Null if not found. */
    suspend fun geocode(country: String, state: String?, city: String): GeocodeResult?
}

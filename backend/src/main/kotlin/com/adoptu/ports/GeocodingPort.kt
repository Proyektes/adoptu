package com.adoptu.ports

data class GeocodeResult(val latitude: Double, val longitude: Double, val radiusKm: Double)

/** Best-effort street address for a coordinate pair - any field may be null if the reverse
 *  geocoder didn't return it for that location (e.g. rural areas with no addressed roads). */
data class ReverseGeocodeResult(
    val street: String?,
    val houseNumber: String?,
    val city: String?,
    val state: String?,
    val country: String?
)

interface GeocodingPort {
    /** Resolves a country/state/city into a center point + a radius that fully covers the zone's bounding box. Null if not found. */
    suspend fun geocode(country: String, state: String?, city: String): GeocodeResult?

    /** Resolves a coordinate pair into a best-effort street address. Null if the geocoder has nothing for that location. */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseGeocodeResult?
}

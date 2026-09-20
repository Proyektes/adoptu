package com.adoptu.frontend.components

import com.adoptu.frontend.I18n
import com.adoptu.frontend.apiFetch
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import kotlin.js.json

/**
 * Interactive Leaflet coverage/location map: a draggable pin (created via [captureCurrentLocation]
 * or by typing into the zone fields) kept in sync with country/state/city fields in both
 * directions, same "way of working" as the Urgent Rescuer Settings coverage map - reused here for
 * any page that needs a rescuer/reporter to pin an exact point rather than trust raw GPS/typed-zone
 * accuracy alone. An optional circle (radiusKm) turns it into a coverage-area picker; omit it for a
 * single-point picker (an incident/report location).
 *
 * Expects a `<div id="mapContainerId">` in the page markup and, if the zone fields are present,
 * `<select id="countryFieldId">` / `<input id="stateFieldId">` / `<input id="cityFieldId">`.
 */
class LocationMapWidget(
    private val mapContainerId: String,
    private val countryFieldId: String,
    private val cityFieldId: String,
    private val stateFieldId: String? = null,
    private val radiusFieldId: String? = null,
    // Extra per-page fields (e.g. report-urgent's street/exteriorNumber) that only the caller
    // knows how to fill - called with the raw reverse-geocode response after country/state/city
    // are already applied.
    private val onReverseGeocoded: ((address: dynamic) -> Unit)? = null
) {
    var latitude: Double? = null
        private set
    var longitude: Double? = null
        private set

    private var map: dynamic = null
    private var marker: dynamic = null
    private var circle: dynamic = null
    private var suppressZoneFieldSync = false

    fun init(initialLat: Double? = null, initialLon: Double? = null) {
        val leaflet = window.asDynamic().L ?: return
        map = leaflet.map(mapContainerId).setView(leaflet.latLng(20.0, 0.0), 2)
        leaflet.tileLayer(
            "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
            json("attribution" to "&copy; OpenStreetMap contributors", "maxZoom" to 19)
        ).addTo(map)

        if (radiusFieldId != null) {
            document.getElementById(radiusFieldId)?.addEventListener("change", {
                val lat = latitude
                val lon = longitude
                if (circle != null && lat != null && lon != null) {
                    circle.setRadius(radiusMeters())
                    fitToCoverage(lat, lon, radiusMeters())
                }
            })
        }
        document.getElementById(countryFieldId)?.addEventListener("change", { geocodeZoneFields() })
        stateFieldId?.let { document.getElementById(it)?.addEventListener("change", { geocodeZoneFields() }) }
        document.getElementById(cityFieldId)?.addEventListener("change", { geocodeZoneFields() })

        if (initialLat != null && initialLon != null) {
            setPin(initialLat, initialLon)
            reverseGeocodeAndFillZoneFields(initialLat, initialLon)
        }
    }

    private fun radiusMeters(): Double =
        (((radiusFieldId?.let { document.getElementById(it) as? HTMLInputElement })?.value?.toDoubleOrNull()) ?: 10.0) * 1000.0

    // Computed directly from lat/lon + radius rather than circle.getBounds() (verified live not to
    // reliably change the map's zoom) - 111320 = meters per degree of latitude; longitude degrees
    // shrink by cos(latitude).
    private fun fitToCoverage(lat: Double, lon: Double, radiusMeters: Double) {
        val leaflet = window.asDynamic().L ?: return
        val dLat = radiusMeters / 111320.0
        val dLon = radiusMeters / (111320.0 * kotlin.math.cos(lat * kotlin.math.PI / 180.0))
        val bounds = leaflet.latLngBounds(
            leaflet.latLng(lat - dLat, lon - dLon),
            leaflet.latLng(lat + dLat, lon + dLon)
        )
        map.fitBounds(bounds, json("maxZoom" to 15))
    }

    fun setPin(lat: Double, lon: Double, recenter: Boolean = true) {
        latitude = lat
        longitude = lon
        val leaflet = window.asDynamic().L ?: return
        val point = leaflet.latLng(lat, lon)
        if (marker == null) {
            marker = leaflet.marker(point, json("draggable" to true)).addTo(map)
            marker.on("dragend", {
                val pos = marker.getLatLng()
                val newLat = pos.lat.unsafeCast<Double>()
                val newLon = pos.lng.unsafeCast<Double>()
                setPin(newLat, newLon, recenter = false)
                reverseGeocodeAndFillZoneFields(newLat, newLon)
            })
            if (radiusFieldId != null) {
                circle = leaflet.circle(point, json("radius" to radiusMeters())).addTo(map)
            }
        } else {
            marker.setLatLng(point)
            circle?.setLatLng(point)
        }
        if (radiusFieldId != null) {
            circle.setRadius(radiusMeters())
            if (recenter) fitToCoverage(lat, lon, radiusMeters()) else Unit
        } else if (recenter) {
            map.setView(point, 13)
        }
    }

    fun captureCurrentLocation(onStatus: (String) -> Unit) {
        onStatus(I18n.t("locating"))
        val geolocation = window.navigator.asDynamic().geolocation
        if (geolocation == null) {
            onStatus(I18n.t("geolocationUnsupported"))
            return
        }
        geolocation.getCurrentPosition(
            { position: dynamic ->
                val lat = position.coords.latitude as? Double
                val lon = position.coords.longitude as? Double
                onStatus(I18n.t("locationCaptured"))
                if (lat != null && lon != null) {
                    setPin(lat, lon)
                    reverseGeocodeAndFillZoneFields(lat, lon)
                }
            },
            { _: dynamic -> onStatus(I18n.t("locationDenied")) }
        )
    }

    // Best-effort - leaves the zone fields as they were on failure (no address found, network
    // error) rather than clearing them; the map/pin is already the source of truth.
    private fun reverseGeocodeAndFillZoneFields(lat: Double, lon: Double) {
        apiFetch("/api/urgent-reports/reverse-geocode?lat=$lat&lon=$lon")
            .then<Unit> { address: dynamic ->
                suppressZoneFieldSync = true
                (document.getElementById(countryFieldId) as? HTMLSelectElement)?.let {
                    val country = address.country?.toString()
                    if (!country.isNullOrBlank()) it.value = country
                }
                stateFieldId?.let { id ->
                    (address.state?.toString())?.let { (document.getElementById(id) as? HTMLInputElement)?.value = it }
                }
                (address.city?.toString())?.let { (document.getElementById(cityFieldId) as? HTMLInputElement)?.value = it }
                suppressZoneFieldSync = false
                onReverseGeocoded?.invoke(address)
            }
            .catch<Unit> { suppressZoneFieldSync = false }
    }

    // Mirror of the above: typing/selecting a zone moves the pin instead. Leaves the pin where it
    // was on failure (no match, incomplete fields) rather than clearing it.
    fun syncToZoneFields() = geocodeZoneFields()

    private fun geocodeZoneFields() {
        if (suppressZoneFieldSync) return
        val country = (document.getElementById(countryFieldId) as? HTMLSelectElement)?.value
        val state = stateFieldId?.let { (document.getElementById(it) as? HTMLInputElement)?.value }
        val city = (document.getElementById(cityFieldId) as? HTMLInputElement)?.value
        if (country.isNullOrBlank()) return
        val countryOnly = city.isNullOrBlank()
        if (countryOnly && latitude != null) return
        val params = js("new URLSearchParams()")
        params.append("country", country)
        if (!state.isNullOrBlank()) params.append("state", state)
        if (!countryOnly) params.append("city", city)
        apiFetch("/api/urgent-reports/geocode?" + params.toString())
            .then<Unit> { result: dynamic ->
                val lat = result.latitude as? Double
                val lon = result.longitude as? Double
                if (lat == null || lon == null) return@then
                if (countryOnly) map.setView(window.asDynamic().L.latLng(lat, lon), 5) else setPin(lat, lon)
            }
            .catch<Unit> { /* no match for that zone - leave the pin where it was */ }
    }
}

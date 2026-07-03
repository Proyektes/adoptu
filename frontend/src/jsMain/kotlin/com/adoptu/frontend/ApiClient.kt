package com.adoptu.frontend

import kotlinx.browser.window
import kotlin.js.Promise
import kotlin.js.json

fun apiFetch(path: String, init: dynamic = null): Promise<dynamic> {
    val opts = init ?: js("({})")
    val method = opts.method?.unsafeCast<String>()
    if (method == null) {
    } else if (method != "GET" && method != "HEAD") {
        if (opts.headers == null) {
            opts.headers = js("({})")
        }
        if (opts.headers["Content-Type"] == null && opts.headers["content-type"] == null) {
            opts.headers["Content-Type"] = "application/json"
        }
    }
    return window.asDynamic().fetch(path, opts).then { res ->
        try {
            val r = res.unsafeCast<dynamic>()
            if (!r.ok) {
                r.text().then { text ->
                    throw js("new Error('Request failed: ' + text)")
                }
            } else {
                r.json().then<dynamic> { json -> json }
            }
        } catch (e: dynamic) {
            throw js("new Error('Request failed: ' + e)")
        }
    }
}

@JsExport
@JsName("ApiClient")
object ApiClientModule {
    fun me(): Promise<dynamic> = apiFetch("/api/auth/me")

    fun logout(): Promise<dynamic> = apiFetch("/api/auth/logout", js("({method: 'POST'})"))

    fun getPets(type: String? = null): Promise<dynamic> =
        apiFetch("/api/pets" + (type?.let { "?type=${window.asDynamic().encodeURIComponent(it)}" } ?: ""))

    fun getPet(id: String): Promise<dynamic> = apiFetch("/api/pets/$id")

    fun createPet(pet: dynamic): Promise<dynamic> = apiFetch("/api/pets", js("({method: 'POST', body: JSON.stringify(pet)})"))

    fun updatePet(id: String, pet: dynamic): Promise<dynamic> = apiFetch("/api/pets/$id", js("({method: 'PUT', body: JSON.stringify(pet)})"))

    fun deletePet(id: String): Promise<dynamic> = apiFetch("/api/pets/$id", js("({method: 'DELETE'})"))

    fun updateProfile(displayName: String, country: String? = null): Promise<dynamic> {
        val body = js("({displayName: displayName, country: country})")
        return apiFetch("/api/users/profile", js("({method: 'PUT', body: JSON.stringify(body)})"))
    }

    fun updateLanguage(language: String): Promise<dynamic> {
        val body = js("({language: language})")
        return apiFetch("/api/users/language", js("({method: 'PUT', body: JSON.stringify(body)})"))
    }

    fun getTemporalHome(): Promise<dynamic> = apiFetch("/api/users/temporal-home")

    fun updateTemporalHome(data: dynamic): Promise<dynamic> = apiFetch("/api/users/temporal-home", js("({method: 'PUT', body: JSON.stringify(data)})"))

    fun createTemporalHome(data: dynamic): Promise<dynamic> = apiFetch("/api/users/temporal-home", js("({method: 'POST', body: JSON.stringify(data)})"))

    fun activateTemporalHome(active: Boolean): Promise<dynamic> {
        val body = js("({activate: active})")
        return apiFetch("/api/users/temporal-home-profile", js("({method: 'POST', body: JSON.stringify(body)})"))
    }

    fun activateRescuer(active: Boolean): Promise<dynamic> {
        val body = js("({activate: active})")
        return apiFetch("/api/users/rescuer-profile", js("({method: 'POST', body: JSON.stringify(body)})"))
    }

    fun activatePhotographer(active: Boolean): Promise<dynamic> {
        val body = js("({activate: active})")
        return apiFetch("/api/photographers/profile", js("({method: 'POST', body: JSON.stringify(body)})"))
    }

    fun activateShelter(active: Boolean): Promise<dynamic> {
        val body = js("({activate: active})")
        return apiFetch("/api/users/shelter-profile", js("({method: 'POST', body: JSON.stringify(body)})"))
    }

    fun activateSterilization(active: Boolean): Promise<dynamic> {
        val body = js("({activate: active})")
        return apiFetch("/api/users/sterilization-profile", js("({method: 'POST', body: JSON.stringify(body)})"))
    }

    fun updatePhotographerSettings(fee: Double, currency: String, country: String, state: String?): Promise<dynamic> {
        val body = js("({photographerFee: fee, photographerCurrency: currency, country: country, state: state})")
        return apiFetch("/api/photographers/settings", js("({method: 'PUT', body: JSON.stringify(body)})"))
    }

    fun getShelter(): Promise<dynamic> = apiFetch("/api/users/shelter")

    fun updateShelter(data: dynamic): Promise<dynamic> = apiFetch("/api/users/shelter", js("({method: 'PUT', body: JSON.stringify(data)})"))

    fun createShelter(data: dynamic): Promise<dynamic> = apiFetch("/api/users/shelter", js("({method: 'POST', body: JSON.stringify(data)})"))

    fun getSterilizationLocation(): Promise<dynamic> = apiFetch("/api/users/sterilization-location")

    fun updateSterilizationLocation(data: dynamic): Promise<dynamic> = apiFetch("/api/users/sterilization-location", js("({method: 'PUT', body: JSON.stringify(data)})"))

    fun createSterilizationLocation(data: dynamic): Promise<dynamic> = apiFetch("/api/users/sterilization-location", js("({method: 'POST', body: JSON.stringify(data)})"))

    fun hasPassword(): Promise<dynamic> = apiFetch("/api/users/has-password")

    fun setPassword(encryptedPassword: String): Promise<dynamic> {
        val body = js("({encryptedPassword: encryptedPassword})")
        return apiFetch("/api/users/password", js("({method: 'POST', body: JSON.stringify(body)})"))
    }

    fun changePassword(encryptedCurrentPassword: String, encryptedNewPassword: String): Promise<dynamic> {
        val body = js("({encryptedCurrentPassword: encryptedCurrentPassword, encryptedNewPassword: encryptedNewPassword})")
        return apiFetch("/api/users/password", js("({method: 'PUT', body: JSON.stringify(body)})"))
    }

    fun requestEmailChange(newEmail: String): Promise<dynamic> {
        val body = js("({newEmail: newEmail})")
        return apiFetch("/api/users/request-email-change", js("({method: 'POST', body: JSON.stringify(body)})"))
    }

    fun getPhotographers(): Promise<dynamic> = apiFetch("/api/photographers")

    fun getShelters(): Promise<dynamic> = apiFetch("/api/shelters")

    fun searchTemporalHomes(query: dynamic): Promise<dynamic> = apiFetch("/api/temporal-homes/search", js("({method: 'POST', body: JSON.stringify(query)})"))
}

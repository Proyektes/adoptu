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
                    val message = try {
                        JSON.parse<dynamic>(text.unsafeCast<String>()).error?.unsafeCast<String>()
                            ?: "Request failed: $text"
                    } catch (e: dynamic) {
                        "Request failed: $text"
                    }
                    throw js("new Error(message)")
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

    fun detectCountry(locale: String? = null): Promise<dynamic> {
        val query = if (!locale.isNullOrEmpty()) "?locale=" + window.asDynamic().encodeURIComponent(locale) else ""
        return apiFetch("/api/detect-country$query")
    }

    fun getPets(type: String? = null, country: String? = null): Promise<dynamic> {
        val params = mutableListOf<String>()
        if (!type.isNullOrEmpty()) params.add("type=" + window.asDynamic().encodeURIComponent(type))
        if (!country.isNullOrEmpty()) params.add("country=" + window.asDynamic().encodeURIComponent(country))
        val query = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
        return apiFetch("/api/pets$query")
    }

    fun getPet(id: String): Promise<dynamic> = apiFetch("/api/pets/$id")

    fun getFavoritePetIds(): Promise<dynamic> = apiFetch("/api/pets/favorite-ids")
    fun getFavoritePets(): Promise<dynamic> = apiFetch("/api/pets/favorites")
    fun addFavorite(petId: String): Promise<dynamic> = apiFetch("/api/pets/$petId/favorite", js("({method: 'POST'})"))
    fun removeFavorite(petId: String): Promise<dynamic> = apiFetch("/api/pets/$petId/favorite", js("({method: 'DELETE'})"))

    fun getSavedSearches(): Promise<dynamic> = apiFetch("/api/saved-searches")
    fun createSavedSearch(type: String?, country: String): Promise<dynamic> =
        apiFetch("/api/saved-searches", js("({method: 'POST', body: JSON.stringify({type: type, country: country})})"))
    fun deleteSavedSearch(id: Int): Promise<dynamic> = apiFetch("/api/saved-searches/$id", js("({method: 'DELETE'})"))

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

    fun getMyPhotographerSettings(): Promise<dynamic> = apiFetch("/api/photographers/me")

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

    fun getTemporalHomeById(id: String): Promise<dynamic> = apiFetch("/api/temporal-homes/$id")

    fun sendTemporalHomeRequest(temporalHomeId: Int, message: String): Promise<dynamic> {
        val body = js("({temporalHomeId: temporalHomeId, message: message})")
        return apiFetch("/api/temporal-homes/request", js("({method: 'POST', body: JSON.stringify(body)})"))
    }

    fun getMyPets(): Promise<dynamic> = apiFetch("/api/pets/mine")

    fun adoptPet(id: String, message: String): Promise<dynamic> {
        val body = js("({message: message})")
        return apiFetch("/api/pets/$id/adopt", js("({method: 'POST', body: JSON.stringify(body)})"))
    }

    fun getTemporalHomeRequests(): Promise<dynamic> = apiFetch("/api/users/temporal-home/requests")

    fun blockRescuer(rescuerId: Int): Promise<dynamic> {
        val body = js("({rescuerId: rescuerId})")
        return apiFetch("/api/temporal-homes/block", js("({method: 'POST', body: JSON.stringify(body)})"))
    }

    fun createPhotographyRequest(photographerId: Int, petId: Int?, message: String): Promise<dynamic> {
        val body = js("({photographerId: photographerId, petId: petId, message: message})")
        return apiFetch("/api/photographers/requests", js("({method: 'POST', body: JSON.stringify(body)})"))
    }

    fun getAdoptionRequests(petId: Int): Promise<dynamic> = apiFetch("/api/pets/$petId/adoption-requests")

    fun updateAdoptionRequest(requestId: Int, status: String): Promise<dynamic> {
        val opts = js("({method: 'PUT', headers: {'Content-Type': 'application/x-www-form-urlencoded'}})")
        opts.body = "status=" + window.asDynamic().encodeURIComponent(status)
        return apiFetch("/api/pets/adoption-requests/$requestId", opts)
    }

    fun addImage(petId: String, file: dynamic, isPrimary: Boolean = false, fileName: String? = null): Promise<dynamic> {
        val formData = js("new FormData()")
        val name = fileName ?: (file.name as? String) ?: "photo.jpg"
        formData.append("file", file, name)
        formData.append("isPrimary", isPrimary.toString())
        return window.asDynamic().fetch("/api/pets/$petId/images", js("({method: 'POST', body: formData, credentials: 'include'})")).then { res: dynamic ->
            if (res.ok != true) {
                res.text().then { text: dynamic -> throw js("new Error('Request failed: ' + text)") }
            } else {
                res.json()
            }
        }
    }

    fun removeImage(petId: String, imageId: Int): Promise<dynamic> = apiFetch("/api/pets/$petId/images/$imageId", js("({method: 'DELETE'})"))

    fun addVideo(petId: String, file: dynamic): Promise<dynamic> {
        val formData = js("new FormData()")
        val name = (file.name as? String) ?: "video.mp4"
        formData.append("file", file, name)
        return window.asDynamic().fetch("/api/pets/$petId/video", js("({method: 'POST', body: formData, credentials: 'include'})")).then { res: dynamic ->
            if (res.ok != true) {
                res.text().then { text: dynamic -> throw js("new Error('Request failed: ' + text)") }
            } else {
                res.json()
            }
        }
    }

    fun removeVideo(petId: String): Promise<dynamic> = apiFetch("/api/pets/$petId/video", js("({method: 'DELETE'})"))

    fun setPrimaryImage(petId: String, imageId: Int): Promise<dynamic> = apiFetch("/api/pets/$petId/images/$imageId/primary", js("({method: 'PUT'})"))
}

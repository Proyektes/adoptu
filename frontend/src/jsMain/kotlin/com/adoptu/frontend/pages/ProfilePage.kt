package com.adoptu.frontend.pages

import com.adoptu.frontend.ApiClientModule
import com.adoptu.frontend.I18n
import com.adoptu.frontend.RsaCryptoModule
import com.adoptu.frontend.WebAuthnModule
import com.adoptu.frontend.forEachElement
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import kotlin.js.Promise

@JsExport
@JsName("ProfilePage")
object ProfilePageModule {
    private var currentRoles = emptyList<String>()
    private var hasTemporalHomeProfile = false
    private var hasShelterProfile = false
    private var hasSterilizationProfile = false

    fun init() {
        loadProfile()
    }

    private fun loadProfile() {
        ApiClientModule.me().then<Unit> { user ->
            if (user != null) {
                updateProfileUI(user)
            }
            undefined
        }
    }

    private fun updateProfileUI(user: dynamic) {
        if (user.authenticated == false) {
            window.location.href = "/login"
            return
        }

        (document.getElementById("email") as? HTMLInputElement)?.value = user.email ?: ""
        (document.getElementById("displayName") as? HTMLInputElement)?.value = user.displayName ?: ""
        (document.getElementById("language") as? HTMLSelectElement)?.value = user.language ?: "en"
        (document.getElementById("country") as? HTMLSelectElement)?.value = user.country ?: ""

        currentRoles = (user.activeRoles as? Array<*>)?.map { it.toString() } ?: emptyList()

        loadFavorites()
        loadSavedSearches()

        listOf("role-rescuer", "role-photographer", "role-temporal-home", "role-shelter", "role-sterilization").forEach { id ->
            (document.getElementById(id) as? HTMLInputElement)?.checked = when (id) {
                "role-rescuer" -> currentRoles.contains("RESCUER")
                "role-photographer" -> currentRoles.contains("PHOTOGRAPHER")
                "role-temporal-home" -> currentRoles.contains("TEMPORAL_HOME")
                "role-shelter" -> currentRoles.contains("SHELTER")
                "role-sterilization" -> currentRoles.contains("STERILIZATION_SERVICE")
                else -> false
            }
        }

        hasTemporalHomeProfile = false
        hasShelterProfile = false
        hasSterilizationProfile = false

        checkProfileExists()

        setupRoleToggles()
        loadRoleSections(user)
        setupSaveButton()
        setupPasswordButton()
        setupEmailChangeButton()
        setupPasskeyButton()
        loadPasswordStatus()
        loadPasskeyStatus()
        I18n.updatePage()
    }

    private fun checkProfileExists() {
        if (currentRoles.contains("TEMPORAL_HOME")) {
            window.asDynamic().fetch("/api/users/temporal-home").then { res ->
                if (res.ok) hasTemporalHomeProfile = true
            }
        }
        if (currentRoles.contains("SHELTER")) {
            window.asDynamic().fetch("/api/users/shelter").then { res ->
                if (res.ok) hasShelterProfile = true
            }
        }
        if (currentRoles.contains("STERILIZATION_SERVICE")) {
            window.asDynamic().fetch("/api/users/sterilization-location").then { res ->
                if (res.ok) hasSterilizationProfile = true
            }
        }
    }

    private fun setupRoleToggles() {
        (document.getElementById("role-photographer") as? HTMLInputElement)?.addEventListener("change", {
            val checked = (it.asDynamic().target.checked as Boolean)
            val section = document.querySelector(".photographer-section") as? HTMLElement
            section?.style?.display = if (checked) "block" else "none"
        })
        (document.getElementById("role-temporal-home") as? HTMLInputElement)?.addEventListener("change", {
            val checked = (it.asDynamic().target.checked as Boolean)
            val section = document.querySelector(".temporal-home-section") as? HTMLElement
            section?.style?.display = if (checked) "block" else "none"
        })
        (document.getElementById("role-shelter") as? HTMLInputElement)?.addEventListener("change", {
            val checked = (it.asDynamic().target.checked as Boolean)
            val section = document.querySelector(".shelter-section") as? HTMLElement
            section?.style?.display = if (checked) "block" else "none"
        })
        (document.getElementById("role-sterilization") as? HTMLInputElement)?.addEventListener("change", {
            val checked = (it.asDynamic().target.checked as Boolean)
            val section = document.querySelector(".sterilization-section") as? HTMLElement
            section?.style?.display = if (checked) "block" else "none"
        })
    }

    private fun loadRoleSections(user: dynamic) {
        if (currentRoles.contains("PHOTOGRAPHER")) {
            (document.querySelector(".photographer-section") as? HTMLElement)?.style?.display = "block"
            loadPhotographer()
        }
        if (currentRoles.contains("TEMPORAL_HOME")) {
            (document.querySelector(".temporal-home-section") as? HTMLElement)?.style?.display = "block"
            loadTemporalHome()
        }
        if (currentRoles.contains("SHELTER")) {
            (document.querySelector(".shelter-section") as? HTMLElement)?.style?.display = "block"
            loadShelter()
        }
        if (currentRoles.contains("STERILIZATION_SERVICE")) {
            (document.querySelector(".sterilization-section") as? HTMLElement)?.style?.display = "block"
            loadSterilization()
        }
    }

    private fun loadPhotographer() {
        ApiClientModule.getMyPhotographerSettings().then<Unit> { photographer ->
            (document.getElementById("photographerFee") as? HTMLInputElement)?.value = photographer.photographerFee?.toString() ?: "0"
            (document.getElementById("photographerCurrency") as? HTMLSelectElement)?.value = photographer.photographerCurrency ?: "USD"
            (document.getElementById("photographerCountry") as? HTMLSelectElement)?.value = photographer.country ?: ""
            (document.getElementById("photographerState") as? HTMLInputElement)?.value = photographer.state ?: ""
            undefined
        }
    }

    private fun loadTemporalHome() {
        ApiClientModule.getTemporalHome().then<Unit> { th ->
            if (th != null) {
                (document.getElementById("th-alias") as? HTMLInputElement)?.value = th.alias ?: ""
                (document.getElementById("th-country") as? HTMLSelectElement)?.value = th.country ?: ""
                (document.getElementById("th-state") as? HTMLInputElement)?.value = th.state ?: ""
                (document.getElementById("th-city") as? HTMLInputElement)?.value = th.city ?: ""
                (document.getElementById("th-zip") as? HTMLInputElement)?.value = th.zip ?: ""
                (document.getElementById("th-neighborhood") as? HTMLInputElement)?.value = th.neighborhood ?: ""
            }
            undefined
        }
    }

    private fun loadShelter() {
        ApiClientModule.getShelter().then<Unit> { shelter ->
            if (shelter != null) {
                (document.getElementById("shelter-name") as? HTMLInputElement)?.value = shelter.name ?: ""
                (document.getElementById("shelter-country") as? HTMLSelectElement)?.value = shelter.country ?: ""
                (document.getElementById("shelter-state") as? HTMLInputElement)?.value = shelter.state ?: ""
                (document.getElementById("shelter-city") as? HTMLInputElement)?.value = shelter.city ?: ""
                (document.getElementById("shelter-address") as? HTMLInputElement)?.value = shelter.address ?: ""
                (document.getElementById("shelter-zip") as? HTMLInputElement)?.value = shelter.zip ?: ""
                (document.getElementById("shelter-phone") as? HTMLInputElement)?.value = shelter.phone ?: ""
                (document.getElementById("shelter-email") as? HTMLInputElement)?.value = shelter.email ?: ""
                (document.getElementById("shelter-website") as? HTMLInputElement)?.value = shelter.website ?: ""
                (document.getElementById("shelter-description") as? HTMLTextAreaElement)?.value = shelter.description ?: ""
                updateEmailPendingStatus("shelter-email-status", shelter.email as? String, shelter.emailVerified == true)
            }
            undefined
        }
    }

    private fun updateEmailPendingStatus(elementId: String, email: String?, verified: Boolean) {
        val statusEl = document.getElementById(elementId) as? HTMLElement ?: return
        statusEl.textContent = if (!email.isNullOrEmpty() && !verified) {
            "Pending verification - this listing won't be published until you confirm this email via the link we sent it"
        } else {
            ""
        }
    }

    private fun loadSterilization() {
        ApiClientModule.getSterilizationLocation().then<Unit> { loc ->
            if (loc != null) {
                (document.getElementById("sterilization-name") as? HTMLInputElement)?.value = loc.name ?: ""
                (document.getElementById("sterilization-country") as? HTMLSelectElement)?.value = loc.country ?: ""
                (document.getElementById("sterilization-state") as? HTMLInputElement)?.value = loc.state ?: ""
                (document.getElementById("sterilization-city") as? HTMLInputElement)?.value = loc.city ?: ""
                (document.getElementById("sterilization-address") as? HTMLInputElement)?.value = loc.address ?: ""
                (document.getElementById("sterilization-zip") as? HTMLInputElement)?.value = loc.zip ?: ""
                (document.getElementById("sterilization-phone") as? HTMLInputElement)?.value = loc.phone ?: ""
                (document.getElementById("sterilization-email") as? HTMLInputElement)?.value = loc.email ?: ""
                (document.getElementById("sterilization-website") as? HTMLInputElement)?.value = loc.website ?: ""
                (document.getElementById("sterilization-description") as? HTMLTextAreaElement)?.value = loc.description ?: ""
                updateEmailPendingStatus("sterilization-email-status", loc.email as? String, loc.emailVerified == true)
            }
            undefined
        }
    }

    private fun setupSaveButton() {
        val btn = document.getElementById("save-profile-btn") as? HTMLElement
        if (btn == null) return
        btn.addEventListener("click", {
            saveProfile()
        })
    }

    private fun saveProfile() {
        val msg = document.getElementById("message") as? HTMLElement ?: return
        val displayName = (document.getElementById("displayName") as? HTMLInputElement)?.value ?: ""
        val language = (document.getElementById("language") as? HTMLSelectElement)?.value ?: "en"
        val country = (document.getElementById("country") as? HTMLSelectElement)?.value?.ifEmpty { null }

        clearFieldErrors()

        if (displayName.trim().isEmpty()) {
            showFieldError("displayName", I18n.t("displayNameRequired"))
            showMessage(msg, "error", I18n.t("displayNameRequired"))
            return
        }

        val validationError = validateActiveRoles()
        if (validationError != null) {
            showMessage(msg, "error", validationError)
            return
        }

        showMessage(msg, "", I18n.t("saving"))

        ApiClientModule.updateProfile(displayName, country).then<Unit> {
            ApiClientModule.updateLanguage(language)
            undefined
        }.then<Unit> {
            saveRoles(msg)
            undefined
        }.catch { error: dynamic ->
            showMessage(msg, "error", error.message ?: "Failed to save profile")
        }
    }

    private fun validateActiveRoles(): String? {
        val rolePhotographer = (document.getElementById("role-photographer") as? HTMLInputElement)?.checked ?: false
        val roleTemporalHome = (document.getElementById("role-temporal-home") as? HTMLInputElement)?.checked ?: false
        val roleShelter = (document.getElementById("role-shelter") as? HTMLInputElement)?.checked ?: false
        val roleSterilization = (document.getElementById("role-sterilization") as? HTMLInputElement)?.checked ?: false

        var firstError: String? = null

        if (rolePhotographer) {
            val phCountry = (document.getElementById("photographerCountry") as? HTMLSelectElement)?.value ?: ""
            val phState = (document.getElementById("photographerState") as? HTMLInputElement)?.value ?: ""
            if (phCountry.isEmpty()) {
                showFieldError("photographerCountry", I18n.t("countryLabel") + " is required")
                if (firstError == null) firstError = I18n.t("photographerCountryStateRequired")
            }
            if (phState.isEmpty()) {
                showFieldError("photographerState", I18n.t("state") + " is required")
                if (firstError == null) firstError = I18n.t("photographerCountryStateRequired")
            }
        }
        if (roleTemporalHome) {
            val thAlias = (document.getElementById("th-alias") as? HTMLInputElement)?.value?.trim() ?: ""
            val thCountry = (document.getElementById("th-country") as? HTMLSelectElement)?.value ?: ""
            val thCity = (document.getElementById("th-city") as? HTMLInputElement)?.value?.trim() ?: ""
            if (thAlias.isEmpty()) {
                showFieldError("th-alias", I18n.t("alias") + " is required")
                if (firstError == null) firstError = I18n.t("temporalHomeAliasCountryCityRequired")
            }
            if (thCountry.isEmpty()) {
                showFieldError("th-country", I18n.t("countryLabel") + " is required")
                if (firstError == null) firstError = I18n.t("temporalHomeAliasCountryCityRequired")
            }
            if (thCity.isEmpty()) {
                showFieldError("th-city", I18n.t("city") + " is required")
                if (firstError == null) firstError = I18n.t("temporalHomeAliasCountryCityRequired")
            }
        }
        if (roleShelter) {
            val shelterName = (document.getElementById("shelter-name") as? HTMLInputElement)?.value?.trim() ?: ""
            val shelterCountry = (document.getElementById("shelter-country") as? HTMLSelectElement)?.value ?: ""
            val shelterCity = (document.getElementById("shelter-city") as? HTMLInputElement)?.value?.trim() ?: ""
            val shelterAddress = (document.getElementById("shelter-address") as? HTMLInputElement)?.value?.trim() ?: ""
            if (shelterName.isEmpty()) {
                showFieldError("shelter-name", I18n.t("name") + " is required")
                if (firstError == null) firstError = I18n.t("shelterNameCountryCityAddressRequired")
            }
            if (shelterCountry.isEmpty()) {
                showFieldError("shelter-country", I18n.t("countryLabel") + " is required")
                if (firstError == null) firstError = I18n.t("shelterNameCountryCityAddressRequired")
            }
            if (shelterCity.isEmpty()) {
                showFieldError("shelter-city", I18n.t("city") + " is required")
                if (firstError == null) firstError = I18n.t("shelterNameCountryCityAddressRequired")
            }
            if (shelterAddress.isEmpty()) {
                showFieldError("shelter-address", I18n.t("address") + " is required")
                if (firstError == null) firstError = I18n.t("shelterNameCountryCityAddressRequired")
            }
        }
        if (roleSterilization) {
            val sterName = (document.getElementById("sterilization-name") as? HTMLInputElement)?.value?.trim() ?: ""
            val sterCountry = (document.getElementById("sterilization-country") as? HTMLSelectElement)?.value ?: ""
            val sterCity = (document.getElementById("sterilization-city") as? HTMLInputElement)?.value?.trim() ?: ""
            val sterAddress = (document.getElementById("sterilization-address") as? HTMLInputElement)?.value?.trim() ?: ""
            if (sterName.isEmpty()) {
                showFieldError("sterilization-name", I18n.t("name") + " is required")
                if (firstError == null) firstError = I18n.t("sterilizationNameCountryCityAddressRequired")
            }
            if (sterCountry.isEmpty()) {
                showFieldError("sterilization-country", I18n.t("countryLabel") + " is required")
                if (firstError == null) firstError = I18n.t("sterilizationNameCountryCityAddressRequired")
            }
            if (sterCity.isEmpty()) {
                showFieldError("sterilization-city", I18n.t("city") + " is required")
                if (firstError == null) firstError = I18n.t("sterilizationNameCountryCityAddressRequired")
            }
            if (sterAddress.isEmpty()) {
                showFieldError("sterilization-address", I18n.t("address") + " is required")
                if (firstError == null) firstError = I18n.t("sterilizationNameCountryCityAddressRequired")
            }
        }
        return firstError
    }

    private fun showMessage(msg: HTMLElement, type: String, text: String) {
        msg.className = "message $type"
        msg.textContent = text
        msg.style.display = "block"
        if (type == "error") {
            scrollToFirstError(msg)
            window.setTimeout({
                msg.style.display = "none"
                msg.textContent = ""
            }, 3000)
        }
    }

    private fun scrollToFirstError(fallback: HTMLElement) {
        val errors = document.querySelectorAll(".field-error")
        for (i in 0 until errors.length) {
            val el = errors.item(i) as? HTMLElement
            if (!el?.textContent.isNullOrEmpty()) {
                el.asDynamic().scrollIntoView(js("({behavior: 'smooth', block: 'center'})"))
                return
            }
        }
        fallback.asDynamic()?.scrollIntoView(js("({behavior: 'smooth', block: 'center'})"))
    }

    private fun clearFieldErrors() {
        val errors = document.querySelectorAll(".field-error")
        for (i in 0 until errors.length) {
            (errors.item(i) as? HTMLElement)?.textContent = ""
        }
        val invalids = document.querySelectorAll(".field-invalid")
        for (i in 0 until invalids.length) {
            (invalids.item(i) as? HTMLElement)?.classList?.remove("field-invalid")
        }
    }

    private fun showFieldError(fieldId: String, message: String) {
        val errorEl = document.getElementById("$fieldId-error")
        errorEl?.textContent = message
        val inputEl = document.getElementById(fieldId)
        inputEl?.classList?.add("field-invalid")
    }

    private fun savePhotographerData(): Promise<dynamic> {
        val phFee = (document.getElementById("photographerFee") as? HTMLInputElement)?.value?.toDoubleOrNull() ?: 0.0
        val phCurrency = (document.getElementById("photographerCurrency") as? HTMLSelectElement)?.value ?: "USD"
        val phCountry = (document.getElementById("photographerCountry") as? HTMLSelectElement)?.value ?: ""
        val phState = (document.getElementById("photographerState") as? HTMLInputElement)?.value ?: ""
        return ApiClientModule.updatePhotographerSettings(phFee, phCurrency, phCountry, phState)
    }

    private fun saveRoles(msg: HTMLElement) {
        val roleRescuer = (document.getElementById("role-rescuer") as? HTMLInputElement)?.checked ?: false
        val rolePhotographer = (document.getElementById("role-photographer") as? HTMLInputElement)?.checked ?: false
        val roleTemporalHome = (document.getElementById("role-temporal-home") as? HTMLInputElement)?.checked ?: false
        val roleShelter = (document.getElementById("role-shelter") as? HTMLInputElement)?.checked ?: false
        val roleSterilization = (document.getElementById("role-sterilization") as? HTMLInputElement)?.checked ?: false

        val promises = mutableListOf<Promise<dynamic>>()

        if (roleRescuer != currentRoles.contains("RESCUER")) {
            promises.add(ApiClientModule.activateRescuer(roleRescuer))
        }
        if (rolePhotographer != currentRoles.contains("PHOTOGRAPHER")) {
            if (rolePhotographer) {
                promises.add(ApiClientModule.activatePhotographer(true).then<Unit> {
                    savePhotographerData()
                    undefined
                })
            } else {
                promises.add(ApiClientModule.activatePhotographer(false))
            }
        } else if (rolePhotographer) {
            promises.add(savePhotographerData())
        }
        if (roleTemporalHome != currentRoles.contains("TEMPORAL_HOME")) {
            if (roleTemporalHome) {
                val thData = js("({})")
                thData.alias = (document.getElementById("th-alias") as? HTMLInputElement)?.value?.trim() ?: ""
                thData.country = (document.getElementById("th-country") as? HTMLSelectElement)?.value ?: ""
                thData.state = (document.getElementById("th-state") as? HTMLInputElement)?.value?.trim()
                thData.city = (document.getElementById("th-city") as? HTMLInputElement)?.value?.trim() ?: ""
                thData.zip = (document.getElementById("th-zip") as? HTMLInputElement)?.value?.trim()
                thData.neighborhood = (document.getElementById("th-neighborhood") as? HTMLInputElement)?.value?.trim()

                if (hasTemporalHomeProfile) {
                    promises.add(ApiClientModule.updateTemporalHome(thData))
                } else {
                    promises.add(ApiClientModule.createTemporalHome(thData).then<Unit> { hasTemporalHomeProfile = true; undefined })
                }
                promises.add(ApiClientModule.activateTemporalHome(true))
            } else {
                promises.add(ApiClientModule.activateTemporalHome(false))
            }
        } else if (roleTemporalHome) {
            val thData = js("({})")
            thData.alias = (document.getElementById("th-alias") as? HTMLInputElement)?.value?.trim() ?: ""
            thData.country = (document.getElementById("th-country") as? HTMLSelectElement)?.value ?: ""
            thData.state = (document.getElementById("th-state") as? HTMLInputElement)?.value?.trim()
            thData.city = (document.getElementById("th-city") as? HTMLInputElement)?.value?.trim() ?: ""
            thData.zip = (document.getElementById("th-zip") as? HTMLInputElement)?.value?.trim()
            thData.neighborhood = (document.getElementById("th-neighborhood") as? HTMLInputElement)?.value?.trim()

            if (hasTemporalHomeProfile) {
                promises.add(ApiClientModule.updateTemporalHome(thData))
            } else {
                promises.add(ApiClientModule.createTemporalHome(thData).then<Unit> { hasTemporalHomeProfile = true; undefined })
            }
        }
        if (roleShelter != currentRoles.contains("SHELTER")) {
            if (roleShelter) {
                val shelterData = js("({})")
                shelterData.name = (document.getElementById("shelter-name") as? HTMLInputElement)?.value?.trim() ?: ""
                shelterData.country = (document.getElementById("shelter-country") as? HTMLSelectElement)?.value ?: ""
                shelterData.state = (document.getElementById("shelter-state") as? HTMLInputElement)?.value?.trim()
                shelterData.city = (document.getElementById("shelter-city") as? HTMLInputElement)?.value?.trim() ?: ""
                shelterData.address = (document.getElementById("shelter-address") as? HTMLInputElement)?.value?.trim() ?: ""
                shelterData.zip = (document.getElementById("shelter-zip") as? HTMLInputElement)?.value?.trim()
                shelterData.phone = (document.getElementById("shelter-phone") as? HTMLInputElement)?.value?.trim()
                shelterData.email = (document.getElementById("shelter-email") as? HTMLInputElement)?.value?.trim()
                shelterData.website = (document.getElementById("shelter-website") as? HTMLInputElement)?.value?.trim()
                shelterData.description = (document.getElementById("shelter-description") as? HTMLTextAreaElement)?.value?.trim()

                if (hasShelterProfile) {
                    promises.add(ApiClientModule.updateShelter(shelterData))
                } else {
                    promises.add(ApiClientModule.createShelter(shelterData).then<Unit> { hasShelterProfile = true; undefined })
                }
                promises.add(ApiClientModule.activateShelter(true))
            } else {
                promises.add(ApiClientModule.activateShelter(false))
            }
        } else if (roleShelter) {
            val shelterData = js("({})")
            shelterData.name = (document.getElementById("shelter-name") as? HTMLInputElement)?.value?.trim() ?: ""
            shelterData.country = (document.getElementById("shelter-country") as? HTMLSelectElement)?.value ?: ""
            shelterData.state = (document.getElementById("shelter-state") as? HTMLInputElement)?.value?.trim()
            shelterData.city = (document.getElementById("shelter-city") as? HTMLInputElement)?.value?.trim() ?: ""
            shelterData.address = (document.getElementById("shelter-address") as? HTMLInputElement)?.value?.trim() ?: ""
            shelterData.zip = (document.getElementById("shelter-zip") as? HTMLInputElement)?.value?.trim()
            shelterData.phone = (document.getElementById("shelter-phone") as? HTMLInputElement)?.value?.trim()
            shelterData.email = (document.getElementById("shelter-email") as? HTMLInputElement)?.value?.trim()
            shelterData.website = (document.getElementById("shelter-website") as? HTMLInputElement)?.value?.trim()
            shelterData.description = (document.getElementById("shelter-description") as? HTMLTextAreaElement)?.value?.trim()

            if (hasShelterProfile) {
                promises.add(ApiClientModule.updateShelter(shelterData))
            } else {
                promises.add(ApiClientModule.createShelter(shelterData).then<Unit> { hasShelterProfile = true; undefined })
            }
        }
        if (roleSterilization != currentRoles.contains("STERILIZATION_SERVICE")) {
            if (roleSterilization) {
                val sterData = js("({})")
                sterData.name = (document.getElementById("sterilization-name") as? HTMLInputElement)?.value?.trim() ?: ""
                sterData.country = (document.getElementById("sterilization-country") as? HTMLSelectElement)?.value ?: ""
                sterData.state = (document.getElementById("sterilization-state") as? HTMLInputElement)?.value?.trim()
                sterData.city = (document.getElementById("sterilization-city") as? HTMLInputElement)?.value?.trim() ?: ""
                sterData.address = (document.getElementById("sterilization-address") as? HTMLInputElement)?.value?.trim() ?: ""
                sterData.zip = (document.getElementById("sterilization-zip") as? HTMLInputElement)?.value?.trim()
                sterData.phone = (document.getElementById("sterilization-phone") as? HTMLInputElement)?.value?.trim()
                sterData.email = (document.getElementById("sterilization-email") as? HTMLInputElement)?.value?.trim()
                sterData.website = (document.getElementById("sterilization-website") as? HTMLInputElement)?.value?.trim()
                sterData.description = (document.getElementById("sterilization-description") as? HTMLTextAreaElement)?.value?.trim()

                if (hasSterilizationProfile) {
                    promises.add(ApiClientModule.updateSterilizationLocation(sterData))
                } else {
                    promises.add(ApiClientModule.createSterilizationLocation(sterData).then<Unit> { hasSterilizationProfile = true; undefined })
                }
                promises.add(ApiClientModule.activateSterilization(true))
            } else {
                promises.add(ApiClientModule.activateSterilization(false))
            }
        } else if (roleSterilization) {
            val sterData = js("({})")
            sterData.name = (document.getElementById("sterilization-name") as? HTMLInputElement)?.value?.trim() ?: ""
            sterData.country = (document.getElementById("sterilization-country") as? HTMLSelectElement)?.value ?: ""
            sterData.state = (document.getElementById("sterilization-state") as? HTMLInputElement)?.value?.trim()
            sterData.city = (document.getElementById("sterilization-city") as? HTMLInputElement)?.value?.trim() ?: ""
            sterData.address = (document.getElementById("sterilization-address") as? HTMLInputElement)?.value?.trim() ?: ""
            sterData.zip = (document.getElementById("sterilization-zip") as? HTMLInputElement)?.value?.trim()
            sterData.phone = (document.getElementById("sterilization-phone") as? HTMLInputElement)?.value?.trim()
            sterData.email = (document.getElementById("sterilization-email") as? HTMLInputElement)?.value?.trim()
            sterData.website = (document.getElementById("sterilization-website") as? HTMLInputElement)?.value?.trim()
            sterData.description = (document.getElementById("sterilization-description") as? HTMLTextAreaElement)?.value?.trim()

            if (hasSterilizationProfile) {
                promises.add(ApiClientModule.updateSterilizationLocation(sterData))
            } else {
                promises.add(ApiClientModule.createSterilizationLocation(sterData).then<Unit> { hasSterilizationProfile = true; undefined })
            }
        }

        if (promises.isEmpty()) {
            showMessage(msg, "success", I18n.t("profileUpdated"))
            window.setTimeout({ window.location.reload() }, 1500)
            return
        }

        Promise.all(promises.toTypedArray().unsafeCast<Array<Promise<dynamic>>>()).then<Unit> {
            showMessage(msg, "success", I18n.t("profileUpdated"))
            window.setTimeout({ window.location.reload() }, 1500)
            undefined
        }.catch { error: dynamic ->
            showMessage(msg, "error", error.message ?: "Failed to save profile")
        }
    }

    private fun setupPasswordButton() {
        (document.getElementById("save-password-btn") as? HTMLElement)?.addEventListener("click", {
            savePassword()
        })
    }

    private fun savePassword() {
        val currentPassword = (document.getElementById("current-password") as? HTMLInputElement)?.value ?: ""
        val newPassword = (document.getElementById("new-password") as? HTMLInputElement)?.value ?: ""
        val confirmPassword = (document.getElementById("confirm-password") as? HTMLInputElement)?.value ?: ""
        val msg = document.getElementById("password-message") as? HTMLElement ?: return
        val hasPassword = (document.getElementById("current-password") as? HTMLInputElement)?.disabled == false

        if (newPassword != confirmPassword) {
            showMessage(msg, "error", I18n.t("passwordsDoNotMatch"))
            return
        }

        if (hasPassword && currentPassword.isEmpty()) {
            showMessage(msg, "error", I18n.t("currentPasswordRequired"))
            return
        }

        showMessage(msg, "", I18n.t("saving"))

        RsaCryptoModule.getPublicKey().then<Unit> { publicKey ->
            if (hasPassword) {
                RsaCryptoModule.encrypt(currentPassword, publicKey).then<Unit> { encCurrent ->
                    RsaCryptoModule.encrypt(newPassword, publicKey).then<Unit> { encNew ->
                        ApiClientModule.changePassword(encCurrent, encNew).then<Unit> { result ->
                            if (result.success == true) {
                                showMessage(msg, "success", I18n.t("passwordChangeSuccess"))
                                (document.getElementById("current-password") as? HTMLInputElement)?.value = ""
                                (document.getElementById("new-password") as? HTMLInputElement)?.value = ""
                                (document.getElementById("confirm-password") as? HTMLInputElement)?.value = ""
                            } else {
                                showMessage(msg, "error", result.error ?: I18n.t("passwordChangeFailed"))
                            }
                            undefined
                        }
                    }
                }
            } else {
                RsaCryptoModule.encrypt(newPassword, publicKey).then<Unit> { encNew ->
                    ApiClientModule.setPassword(encNew).then<Unit> { result ->
                        if (result.success == true) {
                            showMessage(msg, "success", I18n.t("passwordSetSuccess"))
                            (document.getElementById("new-password") as? HTMLInputElement)?.value = ""
                            (document.getElementById("confirm-password") as? HTMLInputElement)?.value = ""
                            loadPasswordStatus()
                        } else {
                            showMessage(msg, "error", result.error ?: I18n.t("passwordSetFailed"))
                        }
                        undefined
                    }
                }
            }
            undefined
        }.catch { error: dynamic ->
            showMessage(msg, "error", error.message ?: "Failed to save password")
        }
    }

    private fun loadPasswordStatus() {
        ApiClientModule.hasPassword().then<Unit> { data ->
            val passwordStatus = document.getElementById("password-status") as? HTMLElement
            val currentPasswordRow = document.getElementById("current-password-row") as? HTMLElement
            if (data.hasPassword == true) {
                passwordStatus?.textContent = I18n.t("passwordSet")
                currentPasswordRow?.classList?.remove("password-hidden")
                (document.getElementById("current-password") as? HTMLInputElement)?.disabled = false
            } else {
                passwordStatus?.textContent = I18n.t("noPassword")
                currentPasswordRow?.classList?.add("password-hidden")
                (document.getElementById("current-password") as? HTMLInputElement)?.disabled = true
            }
            undefined
        }
    }

    private fun setupEmailChangeButton() {
        (document.getElementById("change-email-btn") as? HTMLElement)?.addEventListener("click", {
            requestEmailChange()
        })
    }

    private fun requestEmailChange() {
        val newEmail = (document.getElementById("new-email") as? HTMLInputElement)?.value ?: ""
        val msg = document.getElementById("email-change-message") as? HTMLElement ?: return
        if (newEmail.isEmpty()) {
            showMessage(msg, "error", I18n.t("emailRequired"))
            return
        }
        showMessage(msg, "", I18n.t("saving"))
        ApiClientModule.requestEmailChange(newEmail).then<Unit> { result ->
            if (result.success == true) {
                showMessage(msg, "success", I18n.t("emailChangeSent"))
                (document.getElementById("new-email") as? HTMLInputElement)?.value = ""
            } else {
                showMessage(msg, "error", result.error ?: I18n.t("emailChangeFailed"))
            }
            undefined
        }.catch { error: dynamic ->
            showMessage(msg, "error", error.message ?: I18n.t("emailChangeFailed"))
        }
    }

    private fun setupPasskeyButton() {
        (document.getElementById("register-passkey-btn") as? HTMLElement)?.addEventListener("click", {
            registerPasskey()
        })
    }

    private fun registerPasskey() {
        val msg = document.getElementById("passkey-message") as? HTMLElement ?: return
        val passkeyName = (document.getElementById("passkey-name") as? HTMLInputElement)?.value?.trim() ?: ""
        if (passkeyName.isEmpty()) {
            showMessage(msg, "error", I18n.t("passkeyNameRequired"))
            return
        }
        showMessage(msg, "", I18n.t("registeringPasskey"))

        ApiClientModule.me().then<Unit> { user ->
            if (user != null && user.email != null) {
                WebAuthnModule.register(user.email.toString(), passkeyName).then<Unit> {
                    showMessage(msg, "success", I18n.t("passkeyRegisteredSuccess"))
                    (document.getElementById("passkey-name") as? HTMLInputElement)?.value = ""
                    loadPasskeyStatus()
                    undefined
                }.catch { error: dynamic ->
                    showMessage(msg, "error", error.message ?: I18n.t("passkeyRegistrationFailed"))
                }
            }
            undefined
        }
    }

    private fun loadPasskeyStatus() {
        window.asDynamic().fetch("/api/auth/has-passkey").then { res ->
            res.unsafeCast<dynamic>().json().then { data ->
                val passkeyStatus = document.getElementById("passkey-status") as? HTMLElement
                if (data.success == true) {
                    passkeyStatus?.textContent = I18n.t("passkeyRegistered")
                } else {
                    passkeyStatus?.textContent = I18n.t("noPasskey")
                }
                undefined
            }
        }
    }

    private fun loadFavorites() {
        ApiClientModule.getFavoritePets().then<Unit> { pets: dynamic ->
            val list = (pets as? Array<dynamic>) ?: arrayOf()
            val container = document.getElementById("favorites-list")
            val empty = document.getElementById("favorites-empty")
            if (list.isEmpty()) {
                empty?.textContent = I18n.t("noFavoritesYet")
                container?.innerHTML = ""
                return@then
            }
            empty?.textContent = ""
            container?.innerHTML = list.joinToString("") { pet ->
                "<div class=\"card-bg profile-section\"><h3>${pet.name}</h3>" +
                    "<a class=\"btn\" href=\"/pet/${pet.id}\">${I18n.t("viewDetails")}</a> " +
                    "<button type=\"button\" class=\"btn btn-secondary remove-favorite-btn\" data-pet-id=\"${pet.id}\">${I18n.t("removeFromFavorites")}</button></div>"
            }
            document.querySelectorAll(".remove-favorite-btn").forEachElement { node ->
                val petId = node.asDynamic().dataset.petId?.toString() ?: return@forEachElement
                node.addEventListener("click", { ApiClientModule.removeFavorite(petId).then<Unit> { loadFavorites() } })
            }
        }
    }

    private fun loadSavedSearches() {
        ApiClientModule.getSavedSearches().then<Unit> { searches: dynamic ->
            val list = (searches as? Array<dynamic>) ?: arrayOf()
            val container = document.getElementById("saved-searches-list")
            val empty = document.getElementById("saved-searches-empty")
            if (list.isEmpty()) {
                empty?.textContent = I18n.t("noSavedSearchesYet")
                container?.innerHTML = ""
                return@then
            }
            empty?.textContent = ""
            container?.innerHTML = list.joinToString("") { search ->
                val type = search.type?.toString()?.takeIf { it.isNotEmpty() }?.let { I18n.t(it.lowercase()) } ?: I18n.t("all")
                "<div class=\"saved-search-row\">" +
                    "<span>$type - ${search.country}</span> " +
                    "<button type=\"button\" class=\"btn btn-secondary remove-saved-search-btn\" data-id=\"${search.id}\">${I18n.t("delete")}</button></div>"
            }
            document.querySelectorAll(".remove-saved-search-btn").forEachElement { node ->
                val id = node.asDynamic().dataset.id?.toString()?.toIntOrNull() ?: return@forEachElement
                node.addEventListener("click", { ApiClientModule.deleteSavedSearch(id).then<Unit> { loadSavedSearches() } })
            }
        }
    }
}

external val undefined: dynamic

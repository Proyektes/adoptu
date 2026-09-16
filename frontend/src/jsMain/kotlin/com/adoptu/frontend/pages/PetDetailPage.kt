package com.adoptu.frontend.pages

import com.adoptu.frontend.ApiClientModule
import com.adoptu.frontend.CommonModule
import com.adoptu.frontend.I18n
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event
import kotlin.js.json

private val emoji = mapOf("DOG" to "🐕", "CAT" to "🐱", "BIRD" to "🐦", "FISH" to "🐟")
private val currencySymbols = mapOf("USD" to "$", "EUR" to "€", "GBP" to "£", "CAD" to "C$", "AUD" to "A$")

@JsExport
@JsName("PetDetailPage")
object PetDetailPageModule {
    private var currentPet: dynamic = null
    private var petId: String = ""

    fun init() {
        val segments = window.location.pathname.split("/")
        val id = segments.lastOrNull { it.isNotEmpty() }
        if (id == null) {
            window.location.href = "/"
            return
        }
        petId = id

        ApiClientModule.getPet(id).then<Unit> { pet ->
            currentPet = pet
            ApiClientModule.me().then<Unit> { user -> render(user) }.catch { render(js("({authenticated: false})")) }
        }.catch { window.location.href = "/pets" }
    }

    private fun render(user: dynamic) {
        val pet = currentPet
        val container = document.getElementById("pet-detail") ?: return
        val activeRoles = pet.rescuerId

        val isOwner = try {
            val roles = user.activeRoles as? Array<String>
            (roles?.contains("RESCUER") == true || roles?.contains("ADMIN") == true) && pet.rescuerId == user.id
        } catch (e: dynamic) { false }

        val canAdopt = try {
            val roles = user.activeRoles as? Array<String>
            pet.status == "AVAILABLE" && roles?.contains("ADOPTER") == true
        } catch (e: dynamic) { false }

        val images = pet.images as? Array<dynamic>
        val primaryImage = images?.firstOrNull { it.isPrimary == true } ?: images?.firstOrNull()

        val sb = StringBuilder()
        sb.append("<div class=\"pet-detail-header\">")
        if (primaryImage != null) {
            sb.append("<img src=\"${primaryImage.imageUrl}\" alt=\"${pet.name}\" class=\"pet-main-image\">")
        } else {
            sb.append("<div class=\"pet-detail-placeholder\">${emoji[pet.type.toString()] ?: "🐾"}</div>")
        }
        val yesLabel = I18n.t("yes")
        val noLabel = I18n.t("no")
        sb.append("<span class=\"pet-type\">${I18n.t(pet.type.toString().lowercase())}</span><h1>${pet.name}</h1>")
        if (pet.breed != null && pet.breed.toString().isNotEmpty()) sb.append("<p class=\"pet-breed\"><strong>${I18n.t("breed")}:</strong> ${pet.breed}</p>")
        sb.append("<p><strong>${I18n.t("weight")}:</strong> ${pet.weight} kg | <strong>${I18n.t("age")}:</strong> ${pet.ageYears} ${I18n.t("years")} ${pet.ageMonths} ${I18n.t("months")} | <strong>${I18n.t("sex")}:</strong> ${I18n.t(pet.sex.toString().lowercase())}</p>")
        sb.append("<p><strong>${I18n.t("status")}:</strong> ${petStatusLabel(pet.status)}</p></div>")

        val videoUrl = pet.videoUrl?.toString()
        if (!videoUrl.isNullOrEmpty()) {
            sb.append("<video src=\"$videoUrl\" controls class=\"pet-main-video\"></video>")
        }

        sb.append("<div class=\"pet-detail-body\">")
        if (isOwner) {
            sb.append("<div class=\"storage-management\"><h3>${I18n.t("photos")}</h3><div class=\"pet-images-grid\" id=\"pet-images\">${renderImages()}</div></div>")
        }
        val description = pet.description?.toString()?.takeIf { it.isNotEmpty() } ?: I18n.t("noDescription")
        sb.append("<p>$description</p>")

        sb.append("<div class=\"pet-details-grid\">")
        if (pet.color != null && pet.color.toString().isNotEmpty()) sb.append("<div class=\"detail-item\"><strong>${I18n.t("color")}:</strong> ${pet.color}</div>")
        if (pet.size != null && pet.size.toString().isNotEmpty()) sb.append("<div class=\"detail-item\"><strong>${I18n.t("size")}:</strong> ${I18n.t(pet.size.toString().lowercase())}</div>")
        if (pet.temperament != null && pet.temperament.toString().isNotEmpty()) sb.append("<div class=\"detail-item\"><strong>${I18n.t("temperament")}:</strong> ${pet.temperament}</div>")
        if (pet.energyLevel != null && pet.energyLevel.toString().isNotEmpty()) sb.append("<div class=\"detail-item\"><strong>${I18n.t("energyLevel")}:</strong> ${I18n.t(pet.energyLevel.toString().lowercase())}</div>")
        sb.append("</div>")

        sb.append("<div class=\"pet-details-grid\">")
        sb.append("<div class=\"detail-item\"><strong>${I18n.t("sterilized")}:</strong> ${if (pet.isSterilized == true) yesLabel else noLabel}</div>")
        sb.append("<div class=\"detail-item\"><strong>${I18n.t("microchipped")}:</strong> ${if (pet.isMicrochipped == true) yesLabel else noLabel}</div>")
        if (pet.microchipId != null && pet.microchipId.toString().isNotEmpty()) sb.append("<div class=\"detail-item\"><strong>${I18n.t("microchipId")}:</strong> ${pet.microchipId}</div>")
        sb.append("</div>")

        sb.append("<div class=\"pet-details-grid\">")
        sb.append("<div class=\"detail-item\"><strong>${I18n.t("goodWithKids")}:</strong> ${if (pet.isGoodWithKids == true) yesLabel else noLabel}</div>")
        sb.append("<div class=\"detail-item\"><strong>${I18n.t("goodWithDogs")}:</strong> ${if (pet.isGoodWithDogs == true) yesLabel else noLabel}</div>")
        sb.append("<div class=\"detail-item\"><strong>${I18n.t("goodWithCats")}:</strong> ${if (pet.isGoodWithCats == true) yesLabel else noLabel}</div>")
        sb.append("<div class=\"detail-item\"><strong>${I18n.t("houseTrained")}:</strong> ${if (pet.isHouseTrained == true) yesLabel else noLabel}</div>")
        sb.append("</div>")

        if (pet.vaccinations != null && pet.vaccinations.toString().isNotEmpty()) sb.append("<div class=\"detail-section\"><strong>${I18n.t("vaccinations")}:</strong><p>${pet.vaccinations}</p></div>")
        sb.append("<div class=\"detail-section\" id=\"medical-schedule-section\"></div>")
        if (pet.rescueLocation != null && pet.rescueLocation.toString().isNotEmpty()) sb.append("<div class=\"detail-section\"><strong>${I18n.t("rescueLocation")}:</strong> ${pet.rescueLocation}</div>")
        if (pet.specialNeeds != null && pet.specialNeeds.toString().isNotEmpty()) sb.append("<div class=\"detail-section\"><strong>${I18n.t("specialNeeds")}:</strong><p>${pet.specialNeeds}</p></div>")
        val adoptionFee = pet.adoptionFee?.unsafeCast<Double?>() ?: 0.0
        if (adoptionFee > 0) sb.append("<div class=\"detail-section\"><strong>${I18n.t("adoptionFee")}:</strong> ${currencySymbols[pet.currency.toString()] ?: "$"}$adoptionFee ${pet.currency}</div>")
        if (pet.isUrgent == true) sb.append("<div class=\"urgent-badge\">${I18n.t("urgentBadge")}</div>")
        if (pet.isPromoted == true) {
            val reasonKey = when (pet.promotedReason?.toString()) {
                "MOVING" -> "promotedReasonMoving"
                "COMPLAINTS" -> "promotedReasonComplaints"
                "PET_CONFLICT" -> "promotedReasonPetConflict"
                else -> "promotedReasonOther"
            }
            val detail = pet.promotedReasonDetail?.toString()?.takeIf { it.isNotEmpty() }
            sb.append("<div class=\"promoted-badge-detail\">🏠 ${I18n.t("needsNewHomeBadge")}: ${I18n.t(reasonKey)}${if (detail != null) " - ${CommonModule.escapeHtml(detail)}" else ""}</div>")
        }

        // Share controls on the left, Add to Favorites pinned to the right of the same row.
        sb.append("<div class=\"pet-actions-row\">")

        // navigator.share opens the OS share sheet (Facebook/Instagram/Messages/etc. already
        // included) on mobile/supporting browsers; desktop browsers generally don't implement
        // it, so they get explicit per-platform icons instead of a single dead-end button.
        if (window.navigator.asDynamic().share != null) {
            sb.append("<button type=\"button\" class=\"btn btn-secondary\" id=\"share-pet-btn\">${I18n.t("share")}</button>")
        } else {
            sb.append("<div class=\"share-icons\">")
            sb.append("<button type=\"button\" class=\"share-icon-btn share-facebook\" id=\"share-facebook-btn\" title=\"${I18n.t("shareViaFacebook")}\" aria-label=\"${I18n.t("shareViaFacebook")}\">f</button>")
            sb.append("<button type=\"button\" class=\"share-icon-btn share-x\" id=\"share-x-btn\" title=\"${I18n.t("shareViaX")}\" aria-label=\"${I18n.t("shareViaX")}\">𝕏</button>")
            sb.append("<button type=\"button\" class=\"share-icon-btn share-whatsapp\" id=\"share-whatsapp-btn\" title=\"${I18n.t("shareViaWhatsapp")}\" aria-label=\"${I18n.t("shareViaWhatsapp")}\"><span class=\"material-symbols-outlined\">chat</span></button>")
            sb.append("<button type=\"button\" class=\"share-icon-btn share-email\" id=\"share-email-btn\" title=\"${I18n.t("shareViaEmail")}\" aria-label=\"${I18n.t("shareViaEmail")}\"><span class=\"material-symbols-outlined\">mail</span></button>")
            sb.append("<button type=\"button\" class=\"share-icon-btn share-copy\" id=\"share-copy-btn\" title=\"${I18n.t("copyLink")}\" aria-label=\"${I18n.t("copyLink")}\"><span class=\"material-symbols-outlined\">content_copy</span></button>")
            sb.append("</div>")
        }
        val authenticated = user.authenticated == true || user.id != null
        if (authenticated) {
            sb.append("<button type=\"button\" class=\"btn btn-secondary\" id=\"favorite-pet-btn\">${I18n.t("addToFavorites")}</button>")
        }
        sb.append("</div>")

        if (canAdopt) {
            sb.append(
                "<form id=\"adopt-form\">" +
                    "<label for=\"msg\">${I18n.t("messageOptional")}</label><textarea id=\"msg\" name=\"message\"></textarea>" +
                    "<label for=\"adopt-housing\">${I18n.t("housingType")}</label>" +
                    "<select id=\"adopt-housing\"><option value=\"\">${I18n.t("preferNotToSay")}</option>" +
                    "<option value=\"HOUSE\">${I18n.t("houseHousing")}</option><option value=\"APARTMENT\">${I18n.t("apartmentHousing")}</option></select>" +
                    "<div class=\"checkbox-group\">" +
                    "<input type=\"checkbox\" id=\"adopt-has-yard\"><label for=\"adopt-has-yard\">${I18n.t("hasYard")}</label>" +
                    "<input type=\"checkbox\" id=\"adopt-has-other-pets\"><label for=\"adopt-has-other-pets\">${I18n.t("hasOtherPets")}</label>" +
                    "</div>" +
                    "<label for=\"adopt-experience\">${I18n.t("adoptionExperience")}</label>" +
                    "<select id=\"adopt-experience\"><option value=\"\">${I18n.t("preferNotToSay")}</option>" +
                    "<option value=\"FIRST_TIME\">${I18n.t("firstTimeAdopter")}</option><option value=\"EXPERIENCED\">${I18n.t("experiencedAdopter")}</option></select>" +
                    "<button type=\"submit\" class=\"btn\">${I18n.t("requestAdoption")}</button></form>"
            )
        }
        if (isOwner) {
            sb.append("<a href=\"/edit-pet?id=${pet.id}\" class=\"btn\">${I18n.t("editPet")}</a>")
        }
        if (!isOwner && authenticated) {
            sb.append(sponsorFormHtml())
            sb.append("<div id=\"suggest-edit-section\"></div>")
        }
        sb.append("</div>")

        container.innerHTML = sb.toString()

        loadMedicalSchedule()
        if (!isOwner && authenticated) {
            loadSuggestEditSection(pet)
            initSponsorForm()
        }

        if (window.navigator.asDynamic().share != null) {
            document.getElementById("share-pet-btn")?.addEventListener("click", { shareCurrentPet() })
        } else {
            document.getElementById("share-facebook-btn")?.addEventListener("click", { shareToFacebook() })
            document.getElementById("share-x-btn")?.addEventListener("click", { shareToX() })
            document.getElementById("share-whatsapp-btn")?.addEventListener("click", { shareToWhatsapp() })
            document.getElementById("share-email-btn")?.addEventListener("click", { shareByEmail() })
            document.getElementById("share-copy-btn")?.addEventListener("click", { copyShareLink() })
        }

        if (authenticated) {
            val favBtn = document.getElementById("favorite-pet-btn")
            favBtn?.addEventListener("click", { toggleFavorite(favBtn) })
            ApiClientModule.getFavoritePetIds().then<Unit> { ids: dynamic ->
                val list = (ids as? Array<dynamic>)?.map { it.toString() } ?: emptyList()
                if (list.contains(pet.id.toString())) {
                    favBtn?.textContent = I18n.t("removeFromFavorites")
                    favBtn?.asDynamic()?.dataset?.favorited = "true"
                }
            }
        }

        val form = document.getElementById("adopt-form")
        form?.addEventListener("submit", { e: Event ->
            e.preventDefault()
            if (user.id == null) {
                window.location.href = "/login"
                return@addEventListener
            }
            val msg = (document.getElementById("msg") as? HTMLTextAreaElement)?.value ?: ""
            val housingType = (document.getElementById("adopt-housing") as? HTMLSelectElement)?.value?.ifEmpty { null }
            val hasYard = (document.getElementById("adopt-has-yard") as? HTMLInputElement)?.checked
            val hasOtherPets = (document.getElementById("adopt-has-other-pets") as? HTMLInputElement)?.checked
            val experienceLevel = (document.getElementById("adopt-experience") as? HTMLSelectElement)?.value?.ifEmpty { null }
            ApiClientModule.adoptPet(petId, msg, housingType, hasYard, hasOtherPets, experienceLevel).then<Unit> {
                (document.getElementById("message") as? HTMLElement)?.let {
                    it.className = "message success"
                    it.textContent = I18n.t("adoptionRequestSubmitted")
                    CommonModule.showDonationPrompt(it)
                }
                form.unsafeCast<HTMLElement>().style.display = "none"
            }.catch { err: dynamic ->
                (document.getElementById("message") as? HTMLElement)?.let {
                    it.className = "message error"
                    it.textContent = err?.message?.toString() ?: I18n.t("failedSubmitRequest")
                }
            }
        })
    }

    // Open to any authenticated non-owner (not gated to a role) - anyone might want to help with
    // a specific pet's costs. See RescuersPageModule for the "sponsor the rescuer in general"
    // equivalent on the rescuer detail page.
    private fun sponsorFormHtml(): String {
        return "<h3>${I18n.t("sponsorThisPetTitle")}</h3>" +
            "<p>${I18n.t("sponsorExplanation")}</p>" +
            "<form id=\"sponsor-form\">" +
            "<label for=\"sponsor-type\">${I18n.t("sponsorTypeLabel")}</label>" +
            "<select id=\"sponsor-type\">" +
            "<option value=\"MONEY\">${I18n.t("sponsorTypeMoney")}</option>" +
            "<option value=\"IN_KIND\">${I18n.t("sponsorTypeInKind")}</option>" +
            "</select>" +
            "<div id=\"sponsor-money-fields\">" +
            "<label for=\"sponsor-amount\">${I18n.t("amountLabel")}</label>" +
            "<input type=\"number\" id=\"sponsor-amount\" min=\"0\" step=\"0.01\">" +
            "<label for=\"sponsor-currency\">${I18n.t("currencyLabel")}</label>" +
            "<select id=\"sponsor-currency\"><option value=\"USD\">USD</option><option value=\"MXN\">MXN</option><option value=\"EUR\">EUR</option></select>" +
            "</div>" +
            "<div id=\"sponsor-in-kind-fields\" class=\"hidden\">" +
            "<label for=\"sponsor-in-kind-description\">${I18n.t("inKindDescriptionLabel")}</label>" +
            "<textarea id=\"sponsor-in-kind-description\"></textarea>" +
            "</div>" +
            "<label for=\"sponsor-message\">${I18n.t("messageLabel")}</label>" +
            "<textarea id=\"sponsor-message\" required></textarea>" +
            "<button type=\"submit\" class=\"btn btn-secondary\">${I18n.t("sendOfferBtn")}</button></form>" +
            "<div id=\"sponsor-form-message\"></div>"
    }

    private fun initSponsorForm() {
        val typeSelect = document.getElementById("sponsor-type") as? HTMLSelectElement
        typeSelect?.addEventListener("change", { toggleSponsorFields() })
        document.getElementById("sponsor-form")?.addEventListener("submit", { e: Event ->
            e.preventDefault()
            submitSponsorOffer(currentPet.rescuerId, petId)
        })
    }

    private fun toggleSponsorFields() {
        val isMoney = (document.getElementById("sponsor-type") as? HTMLSelectElement)?.value == "MONEY"
        (document.getElementById("sponsor-money-fields") as? HTMLElement)?.classList?.let { if (isMoney) it.remove("hidden") else it.add("hidden") }
        (document.getElementById("sponsor-in-kind-fields") as? HTMLElement)?.classList?.let { if (isMoney) it.add("hidden") else it.remove("hidden") }
    }

    private fun submitSponsorOffer(rescuerId: dynamic, targetPetId: String) {
        val offerType = (document.getElementById("sponsor-type") as? HTMLSelectElement)?.value ?: "MONEY"
        val message = (document.getElementById("sponsor-message") as? HTMLTextAreaElement)?.value ?: ""
        val body = if (offerType == "MONEY") {
            val amount = (document.getElementById("sponsor-amount") as? HTMLInputElement)?.value?.toDoubleOrNull()
            val currency = (document.getElementById("sponsor-currency") as? HTMLSelectElement)?.value
            json("rescuerId" to rescuerId, "petId" to targetPetId.toInt(), "offerType" to offerType, "amount" to amount, "currency" to currency, "message" to message)
        } else {
            val description = (document.getElementById("sponsor-in-kind-description") as? HTMLTextAreaElement)?.value
            json("rescuerId" to rescuerId, "petId" to targetPetId.toInt(), "offerType" to offerType, "inKindDescription" to description, "message" to message)
        }
        ApiClientModule.createSponsorshipOffer(body).then<Unit> {
            (document.getElementById("sponsor-form-message") as? HTMLElement)?.let {
                it.className = "message success"
                it.textContent = I18n.t("sponsorOfferSent")
            }
            document.getElementById("sponsor-form")?.unsafeCast<HTMLElement>()?.style?.display = "none"
        }.catch { err: dynamic ->
            (document.getElementById("sponsor-form-message") as? HTMLElement)?.let {
                it.className = "message error"
                it.textContent = err?.message?.toString() ?: "Error"
            }
        }
    }

    // Only rendered for an active volunteer of this pet's rescuer - checked by fetching the
    // volunteer's own applications and filtering client-side (there's no dedicated
    // "am I an active volunteer for rescuer X" endpoint; the authorization that actually matters
    // is re-checked server-side in PetEditSuggestionService.createSuggestion).
    private fun loadSuggestEditSection(pet: dynamic) {
        val section = document.getElementById("suggest-edit-section") ?: return
        ApiClientModule.getMyVolunteerApplications().then<Unit> { appsRaw: dynamic ->
            val apps = (appsRaw as? Array<dynamic>) ?: arrayOf()
            val isActiveVolunteer = apps.any { it.rescuerId.toString() == pet.rescuerId.toString() && it.status == "ACTIVE" }
            if (!isActiveVolunteer) return@then

            section.innerHTML = "<h3>${I18n.t("suggestAnEditTitle")}</h3>" +
                "<p>${I18n.t("suggestAnEditExplanation")}</p>" +
                "<form id=\"suggest-edit-form\">" +
                "<label for=\"se-description\">${I18n.t("description")}</label>" +
                "<textarea id=\"se-description\">${CommonModule.escapeHtml(pet.description?.toString() ?: "")}</textarea>" +
                "<label for=\"se-temperament\">${I18n.t("temperament")}</label>" +
                "<input type=\"text\" id=\"se-temperament\" value=\"${CommonModule.escapeHtml(pet.temperament?.toString() ?: "")}\">" +
                "<label for=\"se-energyLevel\">${I18n.t("energyLevel")}</label>" +
                "<select id=\"se-energyLevel\">" +
                "<option value=\"\">${I18n.t("preferNotToSay")}</option>" +
                "<option value=\"LOW\"${if (pet.energyLevel == "LOW") " selected" else ""}>${I18n.t("low")}</option>" +
                "<option value=\"MEDIUM\"${if (pet.energyLevel == "MEDIUM") " selected" else ""}>${I18n.t("medium")}</option>" +
                "<option value=\"HIGH\"${if (pet.energyLevel == "HIGH") " selected" else ""}>${I18n.t("high")}</option>" +
                "</select>" +
                "<label for=\"se-specialNeeds\">${I18n.t("specialNeeds")}</label>" +
                "<textarea id=\"se-specialNeeds\">${CommonModule.escapeHtml(pet.specialNeeds?.toString() ?: "")}</textarea>" +
                "<label for=\"se-vaccinations\">${I18n.t("vaccinations")}</label>" +
                "<textarea id=\"se-vaccinations\">${CommonModule.escapeHtml(pet.vaccinations?.toString() ?: "")}</textarea>" +
                "<button type=\"submit\" class=\"btn btn-secondary\">${I18n.t("suggestEditBtn")}</button></form>"

            document.getElementById("suggest-edit-form")?.addEventListener("submit", { e: Event ->
                e.preventDefault()
                submitEditSuggestion(pet)
            })
        }.catch { }
    }

    private fun submitEditSuggestion(pet: dynamic) {
        fun changedOrNull(fieldId: String, original: String?): String? {
            val value = (document.getElementById(fieldId) as? HTMLElement)?.asDynamic()?.value?.toString() ?: ""
            val originalValue = original ?: ""
            return if (value != originalValue) value else null
        }
        val body = json(
            "description" to changedOrNull("se-description", pet.description?.toString()),
            "temperament" to changedOrNull("se-temperament", pet.temperament?.toString()),
            "energyLevel" to changedOrNull("se-energyLevel", pet.energyLevel?.toString()),
            "specialNeeds" to changedOrNull("se-specialNeeds", pet.specialNeeds?.toString()),
            "vaccinations" to changedOrNull("se-vaccinations", pet.vaccinations?.toString())
        )
        ApiClientModule.createPetEditSuggestion(petId, body).then<Unit> {
            (document.getElementById("suggest-edit-section") as? HTMLElement)?.innerHTML =
                "<p class=\"message success\">${I18n.t("editSuggestionSent")}</p>"
        }.catch { err: dynamic ->
            (document.getElementById("message") as? HTMLElement)?.let {
                it.className = "message error"
                it.textContent = err?.message?.toString() ?: "Error"
            }
        }
    }

    // Public GET /api/pets/{id}/medical-events - a trust signal for adopters, same visibility as
    // the legacy free-text "vaccinations" field above it. Renders as its own card (not just a
    // bare list wedged between detail-section boxes) so it doesn't read as an afterthought.
    private fun loadMedicalSchedule() {
        ApiClientModule.getMedicalEvents(petId).then<Unit> { eventsRaw: dynamic ->
            val events = (eventsRaw as? Array<dynamic>) ?: arrayOf()
            val section = document.getElementById("medical-schedule-section") ?: return@then
            if (events.isEmpty()) {
                section.className = "hidden"
                section.innerHTML = ""
                return@then
            }
            section.className = "detail-section medical-schedule-section"
            val rows = events.joinToString("") { event ->
                val isVaccination = event.category == "VACCINATION"
                val categoryIcon = if (isVaccination) "vaccines" else "medication"
                val categoryLabel = I18n.t(if (isVaccination) "vaccination" else "deworming")
                val administeredDate = js("new Date(event.administeredDate)").toLocaleDateString()
                val dueHtml = if (event.nextDueDate != null) {
                    val dueDateStr = js("new Date(event.nextDueDate)").toLocaleDateString()
                    val daysUntil = js("Math.floor((event.nextDueDate - Date.now()) / 86400000)").unsafeCast<Int>()
                    val (statusClass, statusLabel) = when {
                        daysUntil < 0 -> "overdue" to I18n.t("overdue")
                        daysUntil <= 7 -> "due-soon" to I18n.t("dueSoon")
                        else -> "ok" to I18n.t("upToDate")
                    }
                    "<span class=\"medical-due-badge $statusClass\">${I18n.t("nextDueLabel")}: $dueDateStr ($statusLabel)</span>"
                } else ""
                "<li class=\"medical-schedule-row\">" +
                    "<span class=\"material-symbols-outlined medical-row-icon\">$categoryIcon</span>" +
                    "<span class=\"medical-row-info\"><strong>${CommonModule.escapeHtml(event.name?.toString())}</strong>" +
                    "<span class=\"medical-row-meta\">$categoryLabel &middot; ${I18n.t("givenLabel")} $administeredDate</span></span>" +
                    dueHtml +
                    "</li>"
            }
            section.innerHTML = "<h3><span class=\"material-symbols-outlined\">vaccines</span>${I18n.t("medicalSchedule")}</h3>" +
                "<ul class=\"medical-schedule-list\">$rows</ul>"
        }
    }

    private fun shareCurrentPet() {
        val pet = currentPet ?: return
        val url = window.location.href
        val text = "${pet.name} - ${I18n.t("adoptU")}"
        window.navigator.asDynamic().share(json("title" to text, "url" to url))
    }

    private fun shareUrl(): String = window.location.href

    private fun shareText(): String {
        val pet = currentPet ?: return I18n.t("adoptU")
        return "${pet.name} - ${I18n.t("adoptU")}"
    }

    private fun shareToFacebook() {
        val encoded = window.asDynamic().encodeURIComponent(shareUrl())
        window.open("https://www.facebook.com/sharer/sharer.php?u=$encoded", "_blank")
    }

    private fun shareToX() {
        val encodedUrl = window.asDynamic().encodeURIComponent(shareUrl())
        val encodedText = window.asDynamic().encodeURIComponent(shareText())
        window.open("https://twitter.com/intent/tweet?text=$encodedText&url=$encodedUrl", "_blank")
    }

    private fun shareToWhatsapp() {
        val encoded = window.asDynamic().encodeURIComponent("${shareText()} ${shareUrl()}")
        window.open("https://wa.me/?text=$encoded", "_blank")
    }

    private fun shareByEmail() {
        val encodedSubject = window.asDynamic().encodeURIComponent(shareText())
        val encodedBody = window.asDynamic().encodeURIComponent(shareUrl())
        window.location.href = "mailto:?subject=$encodedSubject&body=$encodedBody"
    }

    private fun copyShareLink() {
        window.navigator.asDynamic().clipboard.writeText(shareUrl()).then<Unit> {
            (document.getElementById("message") as? HTMLElement)?.let {
                it.className = "message success"
                it.textContent = I18n.t("linkCopied")
            }
        }
    }

    private fun toggleFavorite(btn: org.w3c.dom.Element?) {
        val pet = currentPet ?: return
        val petId = pet.id.toString()
        val currentlyFavorited = btn?.asDynamic()?.dataset?.favorited == "true"
        val call = if (currentlyFavorited) ApiClientModule.removeFavorite(petId) else ApiClientModule.addFavorite(petId)
        call.then<Unit> {
            if (currentlyFavorited) {
                btn?.textContent = I18n.t("addToFavorites")
                btn?.asDynamic()?.dataset?.favorited = "false"
            } else {
                btn?.textContent = I18n.t("removeFromFavorites")
                btn?.asDynamic()?.dataset?.favorited = "true"
            }
        }
    }

    private fun petStatusLabel(status: dynamic): String = when (status.toString()) {
        "AVAILABLE" -> I18n.t("petStatusAvailable")
        "ADOPTED" -> I18n.t("petStatusAdopted")
        "DISABLED" -> I18n.t("petStatusDisabled")
        "PENDING" -> I18n.t("petStatusPending")
        else -> status.toString()
    }

    private fun renderImages(): String {
        val images = currentPet?.images as? Array<dynamic>
        if (images == null || images.isEmpty()) return "<p>${I18n.t("noPhotosYet")}</p>"
        return images.joinToString("") { img ->
            val primaryClass = if (img.isPrimary == true) " primary" else ""
            val badge = if (img.isPrimary == true) "<span class=\"primary-badge\">${I18n.t("primary")}</span>" else ""
            "<div class=\"pet-image-item$primaryClass\"><img src=\"${img.imageUrl}\" alt=\"Pet photo\">$badge</div>"
        }
    }
}

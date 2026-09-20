package com.adoptu.frontend.pages

import com.adoptu.frontend.ApiClientModule
import com.adoptu.frontend.CommonModule
import com.adoptu.frontend.I18n
import com.adoptu.frontend.forEachElement
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement

private val emoji = mapOf("DOG" to "🐕", "CAT" to "🐱", "BIRD" to "🐦", "FISH" to "🐟")

@JsExport
@JsName("MyPetsPage")
object MyPetsPageModule {
    private var user: dynamic = null

    fun init() {
        window.asDynamic().del = { id: dynamic -> deletePet(id.toString().toInt()) }
        window.asDynamic().approveRequest = { id: dynamic -> approveRequest(id.toString().toInt()) }
        window.asDynamic().rejectRequest = { id: dynamic -> rejectRequest(id.toString().toInt()) }
        window.asDynamic().markUnderReview = { id: dynamic -> markUnderReview(id.toString().toInt()) }
        window.asDynamic().approveVolunteer = { id: dynamic -> updateVolunteerStatus(id.toString().toInt(), "ACTIVE") }
        window.asDynamic().rejectVolunteer = { id: dynamic -> updateVolunteerStatus(id.toString().toInt(), "REJECTED") }
        window.asDynamic().approveEditSuggestion = { id: dynamic -> updateEditSuggestionStatus(id.toString().toInt(), "APPROVED") }
        window.asDynamic().rejectEditSuggestion = { id: dynamic -> updateEditSuggestionStatus(id.toString().toInt(), "REJECTED") }
        window.asDynamic().markSponsorshipRead = { id: dynamic -> markSponsorshipOfferRead(id.toString().toInt()) }

        load()
    }

    private fun load() {
        ApiClientModule.me().then<Unit> { u ->
            user = u
            val roles = u.activeRoles as? Array<String>
            if (u.authenticated == false || (roles?.contains("RESCUER") != true && roles?.contains("ADMIN") != true)) {
                window.location.href = "/"
                return@then
            }
            ApiClientModule.getMyPets().then<Unit> { pets -> onPetsLoaded(pets) }
        }.catch { window.location.href = "/" }
    }

    private fun onPetsLoaded(petsRaw: dynamic) {
        var pets = (petsRaw as? Array<dynamic>) ?: arrayOf()
        val params = js("new URLSearchParams(location.search)")
        val filterType = params.get("filter") as? String
        if (!filterType.isNullOrEmpty()) pets = pets.filter { it.type == filterType }.toTypedArray()
        val roles = user.activeRoles as? Array<String>
        if (roles?.contains("ADMIN") != true) pets = pets.filter { it.rescuerId.toString() == user.id.toString() }.toTypedArray()

        val container = document.getElementById("pets").unsafeCast<HTMLElement?>()
        container?.innerHTML = if (pets.isNotEmpty()) {
            pets.joinToString("") { p -> renderPetCard(p) }
        } else "<p data-i18n=\"noPets\">${I18n.t("noPets")}</p>"

        loadAdoptionRequests(pets)
        loadPetAnalytics(pets)
        loadVolunteerApplications()
        loadPetEditSuggestions()
        loadSponsorshipOffers()
        loadMedicalEventsOverview()
    }

    // Cross-pet view of every vaccination/deworming record, sorted overdue-first by the backend -
    // the "what's due across my whole roster" control the per-pet Medical Events list (inside the
    // edit form) can't answer on its own.
    private fun loadMedicalEventsOverview() {
        val section = document.getElementById("medical-events-overview-section").unsafeCast<HTMLElement?>()
        val container = document.getElementById("medical-events-overview").unsafeCast<HTMLElement?>()
        ApiClientModule.getRescuerMedicalEvents().then<Unit> { eventsRaw: dynamic ->
            val events = (eventsRaw as? Array<dynamic>) ?: arrayOf()
            if (events.isEmpty()) {
                section?.classList?.add("hidden")
                return@then
            }
            section?.classList?.remove("hidden")
            container?.innerHTML = events.joinToString("") { renderRescuerMedicalEventRow(it) }
        }.catch { section?.classList?.add("hidden") }
    }

    private fun renderRescuerMedicalEventRow(event: dynamic): String {
        val categoryLabel = I18n.t(if (event.category == "VACCINATION") "vaccination" else "deworming")
        val (statusClass, statusLabel) = when (event.urgency?.toString()) {
            "OVERDUE" -> "overdue" to I18n.t("overdue")
            "DUE_SOON" -> "due-soon" to I18n.t("dueSoon")
            else -> "ok" to I18n.t("upToDate")
        }
        val dueHtml = if (event.nextDueDate != null) {
            val dueDateStr = I18n.formatDateOnly(event.nextDueDate)
            "<span class=\"medical-due-badge $statusClass\">${I18n.t("nextDueLabel")}: $dueDateStr ($statusLabel)</span>"
        } else ""
        val petName = CommonModule.escapeHtml(event.petName?.toString() ?: "")
        return "<div class=\"medical-event-row\">" +
            "<a href=\"/edit-pet?id=${event.petId}\" class=\"medical-event-pet-link\">$petName</a> " +
            "<strong>$categoryLabel: ${CommonModule.escapeHtml(event.name?.toString())}</strong> $dueHtml" +
            "</div>"
    }

    private fun loadSponsorshipOffers() {
        val container = document.getElementById("sponsorship-offers").unsafeCast<HTMLElement?>()
        ApiClientModule.getSponsorshipOffersForRescuer().then<Unit> { offersRaw: dynamic ->
            val offers = (offersRaw as? Array<dynamic>) ?: arrayOf()
            if (offers.isEmpty()) {
                container?.innerHTML = "<p data-i18n=\"noSponsorshipOffers\">${I18n.t("noSponsorshipOffers")}</p>"
                return@then
            }
            container?.innerHTML = offers.joinToString("") { renderSponsorshipOfferCard(it) }
        }.catch { }
    }

    private fun renderSponsorshipOfferCard(o: dynamic): String {
        val date = js("new Date(o.createdAt)").toLocaleDateString(I18n.currentLang)
        val sponsorName = CommonModule.escapeHtml(o.sponsorName?.toString() ?: "")
        val petName = o.petName?.toString()?.takeIf { it.isNotEmpty() }
        val target = if (petName != null) CommonModule.escapeHtml(petName) else I18n.t("generalFundLabel")
        val offerDetail = if (o.offerType == "MONEY") {
            "${o.amount} ${o.currency ?: ""}"
        } else {
            CommonModule.escapeHtml(o.inKindDescription?.toString() ?: "")
        }
        val message = CommonModule.escapeHtml(o.message?.toString() ?: "")
        val status = o.status?.toString() ?: "SENT"
        val statusLabel = if (status == "READ") I18n.t("sponsorshipStatusRead") else I18n.t("sponsorshipStatusSent")
        val markReadBtn = if (status != "READ") {
            "<button class=\"btn btn-secondary\" data-action=\"markSponsorshipRead\" data-arg=\"${o.id}\">${I18n.t("markAsReadBtn")}</button>"
        } else ""
        return "<div class=\"adoption-request-card\"><div class=\"ar-pet\">$sponsorName - $target</div>" +
            "<p><strong>${I18n.t("offeringLabel")}:</strong> $offerDetail</p>" +
            "<p>$message</p>" +
            "<span class=\"ar-status status-${status.lowercase()}\">$statusLabel</span>" +
            "<span class=\"ar-date\">$date</span>$markReadBtn</div>"
    }

    private fun markSponsorshipOfferRead(id: Int) {
        ApiClientModule.markSponsorshipOfferRead(id).then<Unit> { loadSponsorshipOffers() }
            .catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Error") }
    }

    private fun loadPetEditSuggestions() {
        val container = document.getElementById("pet-edit-suggestions").unsafeCast<HTMLElement?>()
        ApiClientModule.getPetEditSuggestionsForRescuer().then<Unit> { suggestionsRaw: dynamic ->
            val suggestions = (suggestionsRaw as? Array<dynamic>) ?: arrayOf()
            if (suggestions.isEmpty()) {
                container?.innerHTML = "<p data-i18n=\"noPetEditSuggestions\">${I18n.t("noPetEditSuggestions")}</p>"
                return@then
            }
            container?.innerHTML = suggestions.joinToString("") { renderEditSuggestionCard(it) }
        }.catch { }
    }

    private fun renderEditSuggestionCard(s: dynamic): String {
        val date = js("new Date(s.createdAt)").toLocaleDateString(I18n.currentLang)
        val petName = CommonModule.escapeHtml(s.petName?.toString() ?: "")
        val volunteerName = CommonModule.escapeHtml(s.volunteerName?.toString() ?: "")
        val fields = mutableListOf<String>()
        s.description?.toString()?.let { fields.add("<strong>${I18n.t("description")}:</strong> ${CommonModule.escapeHtml(it)}") }
        s.temperament?.toString()?.let { fields.add("<strong>${I18n.t("temperament")}:</strong> ${CommonModule.escapeHtml(it)}") }
        s.energyLevel?.toString()?.let { fields.add("<strong>${I18n.t("energyLevel")}:</strong> ${I18n.t(it.lowercase())}") }
        s.specialNeeds?.toString()?.let { fields.add("<strong>${I18n.t("specialNeeds")}:</strong> ${CommonModule.escapeHtml(it)}") }
        s.vaccinations?.toString()?.let { fields.add("<strong>${I18n.t("vaccinations")}:</strong> ${CommonModule.escapeHtml(it)}") }
        return "<div class=\"adoption-request-card\"><div class=\"ar-pet\">$petName - ${I18n.t("suggestedByLabel")} $volunteerName</div>" +
            fields.joinToString("") { "<p>$it</p>" } +
            "<div class=\"ar-date\">$date</div>" +
            "<div class=\"ar-actions\"><button class=\"btn btn-secondary\" data-action=\"approveEditSuggestion\" data-arg=\"${s.id}\">${I18n.t("approve")}</button>" +
            "<button class=\"btn btn-secondary\" data-action=\"rejectEditSuggestion\" data-arg=\"${s.id}\">${I18n.t("reject")}</button></div></div>"
    }

    private fun updateEditSuggestionStatus(id: Int, status: String) {
        ApiClientModule.updatePetEditSuggestionStatus(id, status).then<Unit> { load() }
            .catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Error") }
    }

    private fun loadVolunteerApplications() {
        val container = document.getElementById("volunteer-applications").unsafeCast<HTMLElement?>()
        ApiClientModule.getVolunteerApplicationsForRescuer().then<Unit> { applicationsRaw: dynamic ->
            val applications = (applicationsRaw as? Array<dynamic>) ?: arrayOf()
            if (applications.isEmpty()) {
                container?.innerHTML = "<p data-i18n=\"noVolunteerApplications\">${I18n.t("noVolunteerApplications")}</p>"
                return@then
            }
            container?.innerHTML = applications.joinToString("") { renderVolunteerApplicationCard(it) }
        }.catch { }
    }

    private fun renderVolunteerApplicationCard(a: dynamic): String {
        val date = js("new Date(a.createdAt)").toLocaleDateString(I18n.currentLang)
        val status = a.status?.toString() ?: "PENDING"
        val statusLabel = when (status) {
            "ACTIVE" -> I18n.t("volunteerStatusActive")
            "REJECTED" -> I18n.t("volunteerStatusRejected")
            else -> I18n.t("volunteerStatusPending")
        }
        val volunteerName = CommonModule.escapeHtml(a.volunteerName?.toString() ?: "")
        val actions = if (status == "PENDING") {
            "<div class=\"ar-actions\"><button class=\"btn btn-secondary\" data-action=\"approveVolunteer\" data-arg=\"${a.id}\">${I18n.t("approve")}</button>" +
                "<button class=\"btn btn-secondary\" data-action=\"rejectVolunteer\" data-arg=\"${a.id}\">${I18n.t("reject")}</button></div>"
        } else ""
        return "<div class=\"adoption-request-card\"><div class=\"ar-pet\">$volunteerName</div>" +
            "<div class=\"ar-status status-${status.lowercase()}\">$statusLabel</div>" +
            "<div class=\"ar-date\">$date</div>$actions</div>"
    }

    private fun updateVolunteerStatus(id: Int, status: String) {
        ApiClientModule.updateVolunteerStatus(id, status).then<Unit> { loadVolunteerApplications() }
            .catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Error") }
    }

    private fun loadPetAnalytics(pets: Array<dynamic>) {
        pets.forEach { pet ->
            ApiClientModule.getPetAnalytics(pet.id.toString()).then<Unit> { analytics: dynamic ->
                val el = document.getElementById("pet-analytics-${pet.id}") ?: return@then
                val views = analytics.viewCount?.toString() ?: "0"
                val inquiries = analytics.inquiryCount?.toString() ?: "0"
                val conversionRate = analytics.conversionRate as? Double
                val conversionHtml = if (conversionRate != null) {
                    val pct = kotlin.math.round(conversionRate * 100 * 10) / 10
                    " • ${I18n.t("conversionLabel")}: $pct%"
                } else ""
                el.textContent = "${I18n.t("viewsLabel")}: $views • ${I18n.t("inquiriesLabel")}: $inquiries$conversionHtml"
            }.catch { }
        }
    }

    private fun renderPetCard(p: dynamic): String {
        val images = p.images as? Array<dynamic>
        val primaryImage = images?.firstOrNull { it.isPrimary == true } ?: images?.firstOrNull()
        val imageHtml = if (primaryImage != null) {
            "<img src=\"${primaryImage.imageUrl}\" alt=\"${CommonModule.escapeHtml(p.name?.toString())}\">"
        } else {
            "<div class=\"pet-card-placeholder\">${emoji[p.type.toString()] ?: "🐾"}</div>"
        }
        val sexClass = if (p.sex == "MALE") "male" else "female"
        val sizeHtml = if (p.size != null) "<span class=\"pet-size\">${I18n.t(p.size.toString().lowercase())}</span>" else ""
        val urgent = if (p.isUrgent == true) " ⚠️" else ""
        val promoted = if (p.isPromoted == true) " 🏠" else ""
        val breedHtml = if (p.breed != null) "<span class=\"pet-breed\">${CommonModule.escapeHtml(p.breed.toString())}</span>" else ""
        val rescueDateHtml = if (p.rescueDate != null) {
            val date = I18n.formatDateOnly(p.rescueDate)
            "<span class=\"label\">${I18n.t("rescued")}</span><span class=\"value\">$date</span>"
        } else ""
        return "<div class=\"pet-card\">$imageHtml<div class=\"pet-card-body\">" +
            "<span class=\"pet-type\">${I18n.t(p.type.toString().lowercase())}</span>" +
            "<span class=\"pet-sex $sexClass\">${I18n.t(p.sex.toString().lowercase())}</span>$sizeHtml" +
            "<div class=\"pet-name\"><h3>${CommonModule.escapeHtml(p.name?.toString())}$urgent$promoted</h3>$breedHtml</div>" +
            "<p class=\"pet-info\"><span class=\"pet-age\"><span class=\"label\">${I18n.t("age")}</span>" +
            "<span class=\"value\">${CommonModule.formatAge(p.ageYears, p.ageMonths)} • ${p.weight} kg</span></span>" +
            "<span class=\"pet-rescue-date\">$rescueDateHtml</span></p>" +
            "<p class=\"pet-status\">${petStatusLabel(p.status)}</p>" +
            "<div class=\"pet-analytics\" id=\"pet-analytics-${p.id}\"></div>" +
            "<div class=\"pet-card-actions\"><a href=\"/pet/${p.id}\" class=\"btn\">${I18n.t("viewDetails")}</a>" +
            "<a href=\"/edit-pet?id=${p.id}\" class=\"btn btn-secondary\">${I18n.t("edit")}</a>" +
            "<button class=\"btn btn-secondary\" data-action=\"del\" data-arg=\"${p.id}\">${I18n.t("delete")}</button></div></div></div>"
    }

    private fun petStatusLabel(status: dynamic): String = when (status.toString()) {
        "AVAILABLE" -> I18n.t("petStatusAvailable")
        "ADOPTED" -> I18n.t("petStatusAdopted")
        "DISABLED" -> I18n.t("petStatusDisabled")
        "PENDING" -> I18n.t("petStatusPending")
        else -> status.toString()
    }

    private fun loadAdoptionRequests(pets: Array<dynamic>) {
        val container = document.getElementById("adoption-requests").unsafeCast<HTMLElement?>()
        if (pets.isEmpty()) {
            container?.innerHTML = "<p data-i18n=\"noAdoptionRequests\">${I18n.t("noAdoptionRequests")}</p>"
            return
        }
        ApiClientModule.getRescuerAdoptionRequests().then<Unit> { requests ->
            val list = (requests as? Array<dynamic>)?.toList() ?: emptyList()
            renderAdoptionRequests(list, container)
        }.catch<Unit> { renderAdoptionRequests(emptyList(), container) }
    }

    private fun renderAdoptionRequests(allRequests: List<dynamic>, container: HTMLElement?) {
        if (allRequests.isEmpty()) {
            container?.innerHTML = "<p data-i18n=\"noAdoptionRequests\">${I18n.t("noAdoptionRequests")}</p>"
            return
        }
        container?.innerHTML = allRequests.joinToString("") { r -> renderAdoptionRequestCard(r) }
        document.querySelectorAll(".save-review-note-btn").forEachElement { node ->
            val requestId = node.asDynamic().dataset.arg?.toString()?.toIntOrNull() ?: return@forEachElement
            val status = node.asDynamic().dataset.status?.toString() ?: "PENDING"
            node.addEventListener("click", { saveReviewNote(requestId, status) })
        }
    }

    private fun renderAdoptionRequestCard(r: dynamic): String {
        val date = js("new Date(r.createdAt)").toLocaleDateString(I18n.currentLang)
        val message = if (r.message != null) CommonModule.escapeHtml(r.message.toString()) else I18n.t("noMessage")
        val status = r.status?.toString() ?: "PENDING"
        val actions = when (status) {
            "PENDING" -> "<div class=\"ar-actions\"><button class=\"btn btn-secondary\" data-action=\"markUnderReview\" data-arg=\"${r.id}\">${I18n.t("markUnderReview")}</button>" +
                "<button class=\"btn btn-secondary\" data-action=\"approveRequest\" data-arg=\"${r.id}\">${I18n.t("approve")}</button>" +
                "<button class=\"btn btn-secondary\" data-action=\"rejectRequest\" data-arg=\"${r.id}\">${I18n.t("reject")}</button></div>"
            "UNDER_REVIEW" -> "<div class=\"ar-actions\"><button class=\"btn btn-secondary\" data-action=\"approveRequest\" data-arg=\"${r.id}\">${I18n.t("approve")}</button>" +
                "<button class=\"btn btn-secondary\" data-action=\"rejectRequest\" data-arg=\"${r.id}\">${I18n.t("reject")}</button></div>"
            else -> ""
        }
        val screening = renderScreeningFields(r)
        val reviewNote = r.reviewNote?.toString() ?: ""
        val statusLabel = when (status) {
            "UNDER_REVIEW" -> I18n.t("adoptionStatusUnderReview")
            "APPROVED" -> I18n.t("adoptionStatusApproved")
            "REJECTED" -> I18n.t("adoptionStatusRejected")
            else -> I18n.t("adoptionStatusPending")
        }
        val adopterName = CommonModule.escapeHtml(r.adopterName?.toString() ?: "")
        val adopterEmail = CommonModule.escapeHtml(r.adopterEmail?.toString() ?: "")
        val adopterHtml = if (adopterName.isNotEmpty() || adopterEmail.isNotEmpty()) {
            "<div class=\"ar-adopter\">${I18n.t("adopter")}: $adopterName" +
                (if (adopterEmail.isNotEmpty()) " &middot; <a href=\"mailto:$adopterEmail\">$adopterEmail</a>" else "") + "</div>"
        } else ""
        return "<div class=\"adoption-request-card\"><div class=\"ar-pet\">${emoji[r.petType.toString()] ?: "🐾"} ${CommonModule.escapeHtml(r.petName?.toString())}</div>" +
            "<div class=\"ar-status status-${status.lowercase()}\">$statusLabel</div>$adopterHtml" +
            "<div class=\"ar-message\">$message</div>$screening<div class=\"ar-date\">$date</div>" +
            "<div class=\"ar-review-note\"><label for=\"review-note-${r.id}\">${I18n.t("reviewNoteLabel")}</label>" +
            "<textarea id=\"review-note-${r.id}\">${CommonModule.escapeHtml(reviewNote)}</textarea>" +
            "<button type=\"button\" class=\"btn btn-secondary save-review-note-btn\" data-arg=\"${r.id}\" data-status=\"$status\">${I18n.t("saveNote")}</button></div>" +
            "$actions</div>"
    }

    private fun renderScreeningFields(r: dynamic): String {
        val parts = mutableListOf<String>()
        r.housingType?.toString()?.let { parts.add(I18n.t(if (it == "HOUSE") "houseHousing" else "apartmentHousing")) }
        if (r.hasYard == true) parts.add(I18n.t("hasYard"))
        if (r.hasOtherPets == true) parts.add(I18n.t("hasOtherPets"))
        r.experienceLevel?.toString()?.let { parts.add(I18n.t(if (it == "FIRST_TIME") "firstTimeAdopter" else "experiencedAdopter")) }
        if (parts.isEmpty()) return ""
        return "<div class=\"ar-screening\">${parts.joinToString(" • ")}</div>"
    }

    private fun saveReviewNote(requestId: Int, status: String) {
        val note = (document.getElementById("review-note-$requestId") as? HTMLTextAreaElement)?.value ?: ""
        ApiClientModule.updateAdoptionRequest(requestId, status, note).then<Unit> { load() }
            .catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Error") }
    }

    private fun markUnderReview(requestId: Int) {
        ApiClientModule.updateAdoptionRequest(requestId, "UNDER_REVIEW").then<Unit> { load() }
            .catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Error") }
    }

    private fun approveRequest(requestId: Int) {
        ApiClientModule.updateAdoptionRequest(requestId, "APPROVED").then<Unit> { load() }
            .catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Error") }
    }

    private fun rejectRequest(requestId: Int) {
        ApiClientModule.updateAdoptionRequest(requestId, "REJECTED").then<Unit> { load() }
            .catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Error") }
    }

    private fun deletePet(id: Int) {
        if (!window.confirm(I18n.t("confirmDeletePet"))) return
        ApiClientModule.deletePet(id.toString()).then<Unit> { load() }
    }
}

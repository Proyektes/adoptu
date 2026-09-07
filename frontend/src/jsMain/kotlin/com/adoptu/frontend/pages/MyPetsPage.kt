package com.adoptu.frontend.pages

import com.adoptu.frontend.ApiClientModule
import com.adoptu.frontend.CommonModule
import com.adoptu.frontend.I18n
import com.adoptu.frontend.ImageCompression
import com.adoptu.frontend.forEachElement
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLFormElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event

private val emoji = mapOf("DOG" to "🐕", "CAT" to "🐱", "BIRD" to "🐦", "FISH" to "🐟")

@JsExport
@JsName("MyPetsPage")
object MyPetsPageModule {
    private var user: dynamic = null
    private var existingImages: Array<dynamic> = arrayOf()
    private var selectedFiles: MutableList<dynamic> = mutableListOf()
    private var currentPetIdForVideo: String? = null
    private var activePlacementId: Int? = null

    fun init() {
        window.asDynamic().edit = { id: dynamic -> editPet(id.toString().toInt()) }
        window.asDynamic().del = { id: dynamic -> deletePet(id.toString().toInt()) }
        window.asDynamic().approveRequest = { id: dynamic -> approveRequest(id.toString().toInt()) }
        window.asDynamic().rejectRequest = { id: dynamic -> rejectRequest(id.toString().toInt()) }
        window.asDynamic().markUnderReview = { id: dynamic -> markUnderReview(id.toString().toInt()) }
        window.asDynamic().setPrimaryImage = { index: dynamic -> setPrimaryImage(index.toString().toInt()) }
        window.asDynamic().removeExistingImage = { index: dynamic -> removeExistingImage(index.toString().toInt()) }
        window.asDynamic().removePreview = { index: dynamic -> removePreview(index.toString().toInt()) }
        window.asDynamic().deleteMedicalEvent = { id: dynamic -> deleteMedicalEvent(id.toString().toInt()) }
        window.asDynamic().approveVolunteer = { id: dynamic -> updateVolunteerStatus(id.toString().toInt(), "ACTIVE") }
        window.asDynamic().rejectVolunteer = { id: dynamic -> updateVolunteerStatus(id.toString().toInt(), "REJECTED") }
        window.asDynamic().approveEditSuggestion = { id: dynamic -> updateEditSuggestionStatus(id.toString().toInt(), "APPROVED") }
        window.asDynamic().rejectEditSuggestion = { id: dynamic -> updateEditSuggestionStatus(id.toString().toInt(), "REJECTED") }
        window.asDynamic().markSponsorshipRead = { id: dynamic -> markSponsorshipOfferRead(id.toString().toInt()) }

        document.getElementById("add-btn")?.addEventListener("click", { openAddForm() })
        document.getElementById("cancel-btn")?.addEventListener("click", { closeForm() })
        document.getElementById("add-medical-event-btn")?.addEventListener("click", { addMedicalEvent() })
        document.getElementById("end-placement-btn")?.addEventListener("click", { endCurrentPlacement() })

        listOf("weight", "ageYears", "ageMonths").forEach { id -> clampNonNegative(id, maxMonths = id == "ageMonths") }
        clampNonNegative("adoptionFee")

        setupDropzone()
        document.getElementById("pet-form")?.addEventListener("submit", { e: Event -> onSubmit(e) })
        document.getElementById("isPromoted")?.addEventListener("change", { togglePromotedReasonRow() })

        load()
    }

    private fun togglePromotedReasonRow() {
        val checked = (document.getElementById("isPromoted") as? HTMLInputElement)?.checked == true
        (document.getElementById("promoted-reason-row") as? HTMLElement)?.classList?.let {
            if (checked) it.remove("hidden") else it.add("hidden")
        }
    }

    private fun loadFosterPlacementStatus(petId: String) {
        val statusEl = document.getElementById("foster-placement-status")
        val endBtn = document.getElementById("end-placement-btn") as? HTMLElement
        ApiClientModule.getFosterPlacementHistory(petId).then<Unit> { historyRaw: dynamic ->
            val history = (historyRaw as? Array<dynamic>) ?: arrayOf()
            val active = history.firstOrNull { it.endDate == null }
            if (active != null) {
                activePlacementId = active.id?.toString()?.toIntOrNull()
                val since = js("new Date(active.startDate)").toLocaleDateString()
                val alias = active.temporalHomeAlias?.toString()?.takeIf { it.isNotEmpty() } ?: I18n.t("aTemporalHome")
                statusEl?.removeAttribute("data-i18n")
                statusEl?.textContent = "${I18n.t("currentlyWithLabel")} $alias ${I18n.t("sinceLabel")} $since"
                endBtn?.classList?.remove("hidden")
            } else {
                activePlacementId = null
                statusEl?.setAttribute("data-i18n", "notCurrentlyFostered")
                statusEl?.textContent = I18n.t("notCurrentlyFostered")
                endBtn?.classList?.add("hidden")
            }
        }.catch {
            statusEl?.setAttribute("data-i18n", "notCurrentlyFostered")
            statusEl?.textContent = I18n.t("notCurrentlyFostered")
            endBtn?.classList?.add("hidden")
        }
    }

    private fun endCurrentPlacement() {
        val placementId = activePlacementId ?: return
        if (!window.confirm(I18n.t("confirmEndPlacement"))) return
        val petId = currentPetIdForVideo ?: return
        ApiClientModule.endFosterPlacement(placementId).then<Unit> { loadFosterPlacementStatus(petId) }
            .catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Error") }
    }

    private fun loadMedicalEvents(petId: String) {
        ApiClientModule.getMedicalEvents(petId).then<Unit> { eventsRaw: dynamic ->
            val events = (eventsRaw as? Array<dynamic>) ?: arrayOf()
            val container = document.getElementById("medical-events-list")
            if (events.isEmpty()) {
                container?.innerHTML = "<p data-i18n=\"noMedicalRecords\">${I18n.t("noMedicalRecords")}</p>"
                return@then
            }
            container?.innerHTML = events.joinToString("") { renderMedicalEventRow(it) }
        }
    }

    private fun renderMedicalEventRow(event: dynamic): String {
        val categoryLabel = I18n.t(if (event.category == "VACCINATION") "vaccination" else "deworming")
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
        val notes = event.notes?.toString()
        val notesHtml = if (!notes.isNullOrEmpty()) "<p class=\"medical-notes\">${CommonModule.escapeHtml(notes)}</p>" else ""
        return "<div class=\"medical-event-row\">" +
            "<strong>$categoryLabel: ${CommonModule.escapeHtml(event.name?.toString())}</strong> " +
            "<span class=\"medical-administered\">${I18n.t("givenLabel")}: $administeredDate</span> $dueHtml" +
            notesHtml +
            "<button type=\"button\" class=\"btn btn-secondary\" data-action=\"deleteMedicalEvent\" data-arg=\"${event.id}\">${I18n.t("delete")}</button>" +
            "</div>"
    }

    private fun addMedicalEvent() {
        val petId = currentPetIdForVideo ?: return
        val msg = document.getElementById("medical-event-message")
        val category = (document.getElementById("medical-category") as HTMLSelectElement).value
        val name = (document.getElementById("medical-name") as HTMLInputElement).value.trim()
        val administeredDateVal = (document.getElementById("medical-administered-date") as HTMLInputElement).value
        val nextDueDateVal = (document.getElementById("medical-next-due-date") as HTMLInputElement).value
        val notes = (document.getElementById("medical-notes") as HTMLInputElement).value.ifEmpty { null }

        if (name.isEmpty()) {
            msg?.className = "message error"
            msg?.textContent = I18n.t("recordNameRequired")
            return
        }
        if (administeredDateVal.isEmpty()) {
            msg?.className = "message error"
            msg?.textContent = I18n.t("dateGivenRequired")
            return
        }
        val administeredDate = js("new Date(administeredDateVal)").getTime().unsafeCast<Double>()
        val nextDueDate = if (nextDueDateVal.isNotEmpty()) js("new Date(nextDueDateVal)").getTime().unsafeCast<Double>() else null

        ApiClientModule.createMedicalEvent(petId, category, name, administeredDate, nextDueDate, notes).then<Unit> {
            msg?.className = ""
            msg?.textContent = ""
            (document.getElementById("medical-name") as HTMLInputElement).value = ""
            (document.getElementById("medical-administered-date") as HTMLInputElement).value = ""
            (document.getElementById("medical-next-due-date") as HTMLInputElement).value = ""
            (document.getElementById("medical-notes") as HTMLInputElement).value = ""
            loadMedicalEvents(petId)
        }.catch { err: dynamic ->
            msg?.className = "message error"
            msg?.textContent = err?.message?.toString() ?: I18n.t("failedToAddRecord")
        }
    }

    private fun deleteMedicalEvent(id: Int) {
        if (!window.confirm(I18n.t("confirmDeleteRecord"))) return
        val petId = currentPetIdForVideo ?: return
        ApiClientModule.deleteMedicalEvent(id).then<Unit> { loadMedicalEvents(petId) }
            .catch { err: dynamic -> window.alert(err?.message?.toString() ?: "Error") }
    }

    private fun clampNonNegative(id: String, maxMonths: Boolean = false) {
        val el = document.getElementById(id) as? HTMLInputElement ?: return
        el.addEventListener("input", {
            if ((el.value.toDoubleOrNull() ?: 0.0) < 0) el.value = "0"
        })
        el.addEventListener("blur", {
            if ((el.value.toDoubleOrNull() ?: 0.0) < 0) el.value = "0"
            if (maxMonths && (el.value.toIntOrNull() ?: 0) > 11) el.value = "11"
        })
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

        val editId = params.get("edit") as? String
        if (!editId.isNullOrEmpty()) {
            ApiClientModule.getPet(editId).then<Unit> { pet ->
                fillForm(pet)
                document.getElementById("form-title")?.textContent = "Edit Pet"
                (document.getElementById("form-container") as? HTMLElement)?.style?.display = "block"
            }
        }

        loadAdoptionRequests(pets)
        loadPetAnalytics(pets)
        loadVolunteerApplications()
        loadPetEditSuggestions()
        loadSponsorshipOffers()
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
        val date = js("new Date(o.createdAt)").toLocaleDateString()
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
        val date = js("new Date(s.createdAt)").toLocaleDateString()
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
        val date = js("new Date(a.createdAt)").toLocaleDateString()
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
            val date = js("new Date(p.rescueDate)").toLocaleDateString()
            "<span class=\"label\">${I18n.t("rescued")}</span><span class=\"value\">$date</span>"
        } else ""
        return "<div class=\"pet-card\">$imageHtml<div class=\"pet-card-body\">" +
            "<span class=\"pet-type\">${I18n.t(p.type.toString().lowercase())}</span>" +
            "<span class=\"pet-sex $sexClass\">${I18n.t(p.sex.toString().lowercase())}</span>$sizeHtml" +
            "<div class=\"pet-name\"><h3>${CommonModule.escapeHtml(p.name?.toString())}$urgent$promoted</h3>$breedHtml</div>" +
            "<p class=\"pet-info\"><span class=\"pet-age\"><span class=\"label\">${I18n.t("age")}</span>" +
            "<span class=\"value\">${p.ageYears} ${I18n.t("years")} ${p.ageMonths} ${I18n.t("months")} • ${p.weight} kg</span></span>" +
            "<span class=\"pet-rescue-date\">$rescueDateHtml</span></p>" +
            "<p class=\"pet-status\">${petStatusLabel(p.status)}</p>" +
            "<div class=\"pet-analytics\" id=\"pet-analytics-${p.id}\"></div>" +
            "<div class=\"pet-card-actions\"><a href=\"/pet/${p.id}\" class=\"btn\">${I18n.t("viewDetails")}</a>" +
            "<button class=\"btn btn-secondary\" data-action=\"edit\" data-arg=\"${p.id}\">${I18n.t("edit")}</button>" +
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
        val allRequests = mutableListOf<dynamic>()
        var remaining = pets.size
        if (remaining == 0) {
            container?.innerHTML = "<p>No adoption requests</p>"
            return
        }
        pets.forEach { pet ->
            ApiClientModule.getAdoptionRequests(pet.id as Int).then<Unit> { requests ->
                val list = requests as? Array<dynamic>
                list?.forEach { r ->
                    r.petName = pet.name
                    r.petType = pet.type
                    allRequests.add(r)
                }
            }.catch { }.finally {
                remaining--
                if (remaining == 0) renderAdoptionRequests(allRequests, container)
            }
        }
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
        val date = js("new Date(r.createdAt)").toLocaleDateString()
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
        return "<div class=\"adoption-request-card\"><div class=\"ar-pet\">${emoji[r.petType.toString()] ?: "🐾"} ${CommonModule.escapeHtml(r.petName?.toString())}</div>" +
            "<div class=\"ar-status status-${status.lowercase()}\">$statusLabel</div>" +
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

    private fun fillForm(pet: dynamic) {
        (document.getElementById("pet-id") as HTMLInputElement).value = pet.id.toString()
        (document.getElementById("name") as HTMLInputElement).value = pet.name?.toString() ?: ""
        (document.getElementById("type") as HTMLSelectElement).value = pet.type?.toString() ?: "DOG"
        (document.getElementById("breed") as HTMLInputElement).value = pet.breed?.toString() ?: ""
        (document.getElementById("description") as HTMLTextAreaElement).value = pet.description?.toString() ?: ""
        (document.getElementById("weight") as HTMLInputElement).value = (pet.weight ?: 0).toString()
        (document.getElementById("ageYears") as HTMLInputElement).value = (pet.ageYears ?: 0).toString()
        (document.getElementById("ageMonths") as HTMLInputElement).value = (pet.ageMonths ?: 0).toString()
        (document.getElementById("sex") as HTMLSelectElement).value = pet.sex?.toString() ?: "MALE"
        (document.getElementById("color") as HTMLInputElement).value = pet.color?.toString() ?: ""
        (document.getElementById("size") as HTMLSelectElement).value = pet.size?.toString() ?: ""
        (document.getElementById("temperament") as HTMLInputElement).value = pet.temperament?.toString() ?: ""
        (document.getElementById("energyLevel") as HTMLSelectElement).value = pet.energyLevel?.toString() ?: ""
        (document.getElementById("isSterilized") as HTMLInputElement).checked = pet.isSterilized == true
        (document.getElementById("isMicrochipped") as HTMLInputElement).checked = pet.isMicrochipped == true
        (document.getElementById("microchipId") as HTMLInputElement).value = pet.microchipId?.toString() ?: ""
        (document.getElementById("vaccinations") as HTMLTextAreaElement).value = pet.vaccinations?.toString() ?: ""
        (document.getElementById("isGoodWithKids") as HTMLInputElement).checked = pet.isGoodWithKids != false
        (document.getElementById("isGoodWithDogs") as HTMLInputElement).checked = pet.isGoodWithDogs != false
        (document.getElementById("isGoodWithCats") as HTMLInputElement).checked = pet.isGoodWithCats != false
        (document.getElementById("isHouseTrained") as HTMLInputElement).checked = pet.isHouseTrained == true
        (document.getElementById("rescueLocation") as HTMLInputElement).value = pet.rescueLocation?.toString() ?: ""
        if (pet.rescueDate != null) {
            val iso = js("new Date(pet.rescueDate)").toISOString().toString()
            (document.getElementById("rescueDate") as HTMLInputElement).value = iso.split("T")[0]
        }
        (document.getElementById("specialNeeds") as HTMLTextAreaElement).value = pet.specialNeeds?.toString() ?: ""
        (document.getElementById("adoptionFee") as HTMLInputElement).value = (pet.adoptionFee ?: 0).toString()
        (document.getElementById("currency") as HTMLSelectElement).value = pet.currency?.toString() ?: "USD"
        (document.getElementById("isUrgent") as HTMLInputElement).checked = pet.isUrgent == true
        (document.getElementById("isPromoted") as HTMLInputElement).checked = pet.isPromoted == true
        (document.getElementById("promotedReason") as HTMLSelectElement).value = pet.promotedReason?.toString() ?: ""
        (document.getElementById("promotedReasonDetail") as HTMLTextAreaElement).value = pet.promotedReasonDetail?.toString() ?: ""
        togglePromotedReasonRow()

        existingImages = (pet.images as? Array<dynamic>) ?: arrayOf()
        updatePreviews()

        currentPetIdForVideo = pet.id?.toString()
        (document.getElementById("medical-events-section") as? HTMLElement)?.classList?.remove("hidden")
        loadMedicalEvents(currentPetIdForVideo!!)
        (document.getElementById("foster-placement-section") as? HTMLElement)?.classList?.remove("hidden")
        loadFosterPlacementStatus(currentPetIdForVideo!!)
        val existingVideoDiv = document.getElementById("existing-video")
        val videoUrl = pet.videoUrl?.toString()
        if (videoUrl.isNullOrEmpty()) {
            existingVideoDiv?.innerHTML = ""
        } else {
            existingVideoDiv?.innerHTML = "<video src=\"$videoUrl\" controls style=\"max-width:300px\"></video><br><button type=\"button\" class=\"btn btn-secondary\" id=\"remove-video-btn\">${I18n.t("removeVideo")}</button>"
            document.getElementById("remove-video-btn")?.addEventListener("click", {
                val petId = currentPetIdForVideo ?: return@addEventListener
                ApiClientModule.removeVideo(petId).then<Unit> { existingVideoDiv?.innerHTML = "" }
            })
        }
    }

    private fun editPet(id: Int) {
        ApiClientModule.getPet(id.toString()).then<Unit> { pet ->
            fillForm(pet)
            document.getElementById("form-title")?.textContent = "Edit Pet"
            (document.getElementById("form-container") as? HTMLElement)?.style?.display = "block"
        }
    }

    private fun deletePet(id: Int) {
        if (!window.confirm(I18n.t("confirmDeletePet"))) return
        ApiClientModule.deletePet(id.toString()).then<Unit> { load() }
    }

    private fun openAddForm() {
        (document.getElementById("pet-form") as? HTMLFormElement)?.reset()
        (document.getElementById("pet-id") as? HTMLInputElement)?.value = ""
        (document.getElementById("currency") as? HTMLSelectElement)?.value = "USD"
        togglePromotedReasonRow()
        selectedFiles = mutableListOf()
        existingImages = arrayOf()
        currentPetIdForVideo = null
        (document.getElementById("medical-events-section") as? HTMLElement)?.classList?.add("hidden")
        document.getElementById("medical-events-list")?.innerHTML = ""
        (document.getElementById("medical-event-message"))?.textContent = ""
        (document.getElementById("foster-placement-section") as? HTMLElement)?.classList?.add("hidden")
        document.getElementById("foster-placement-status")?.textContent = ""
        (document.getElementById("end-placement-btn") as? HTMLElement)?.classList?.add("hidden")
        document.getElementById("existing-video")?.innerHTML = ""
        updatePreviews()
        document.getElementById("form-title")?.textContent = "Add Pet"
        (document.getElementById("form-container") as? HTMLElement)?.style?.display = "block"
    }

    private fun closeForm() {
        (document.getElementById("form-container") as? HTMLElement)?.style?.display = "none"
        window.asDynamic().history.replaceState(js("({})"), "", "/my-pets")
    }

    private fun setupDropzone() {
        val dropzone = document.getElementById("storage-dropzone").unsafeCast<HTMLElement?>() ?: return
        val fileInput = document.getElementById("pet-images").unsafeCast<HTMLInputElement?>() ?: return

        dropzone.addEventListener("click", { fileInput.click() })
        dropzone.addEventListener("dragover", { e: Event -> e.preventDefault(); dropzone.classList.add("dragover") })
        dropzone.addEventListener("dragleave", { dropzone.classList.remove("dragover") })
        dropzone.addEventListener("drop", { e: Event ->
            e.preventDefault()
            dropzone.classList.remove("dragover")
            handleFiles(e.asDynamic().dataTransfer.files)
        })
        fileInput.addEventListener("change", { handleFiles(fileInput.asDynamic().files) })
    }

    private fun handleFiles(files: dynamic) {
        val remaining = 12 - selectedFiles.size
        if (remaining <= 0) {
            window.alert("Maximum 12 photos allowed")
            return
        }
        val fileList = js("Array.from(files)") as Array<dynamic>
        fileList.take(remaining).forEach { selectedFiles.add(it) }
        updatePreviews()
        val dataTransfer = js("new DataTransfer()")
        selectedFiles.forEach { f -> dataTransfer.items.add(f) }
        (document.getElementById("pet-images") as HTMLInputElement).asDynamic().files = dataTransfer.files
    }

    private fun updatePreviews() {
        val previewContainer = document.getElementById("storage-previews") ?: return
        val existingHtml = existingImages.mapIndexed { index, img ->
            val primaryClass = if (img.isPrimary == true) " primary" else ""
            val primaryControl = if (img.isPrimary == true) {
                "<span class=\"primary-badge\">★</span>"
            } else {
                "<button type=\"button\" class=\"primary-btn\" data-action=\"setPrimaryImage\" data-arg=\"$index\" title=\"Set as primary\">☆</button>"
            }
            "<div class=\"preview-item$primaryClass\"><img src=\"${img.imageUrl}\">$primaryControl<button type=\"button\" data-action=\"removeExistingImage\" data-arg=\"$index\">×</button></div>"
        }.joinToString("")
        val newFilesHtml = selectedFiles.mapIndexed { index, file ->
            val url = window.asDynamic().URL.createObjectURL(file)
            "<div class=\"preview-item\"><img src=\"$url\"><button type=\"button\" data-action=\"removePreview\" data-arg=\"$index\">×</button></div>"
        }.joinToString("")
        previewContainer.innerHTML = existingHtml + newFilesHtml
    }

    private fun setPrimaryImage(index: Int) {
        val img = existingImages[index]
        val petId = (document.getElementById("pet-id") as HTMLInputElement).value
        ApiClientModule.setPrimaryImage(petId, img.id as Int).then<Unit> {
            existingImages.forEachIndexed { i, image -> image.isPrimary = (i == index) }
            updatePreviews()
        }.catch { err: dynamic -> window.alert("Failed to set primary storage: ${err?.message}") }
    }

    private fun removeExistingImage(index: Int) {
        val img = existingImages[index]
        val petId = (document.getElementById("pet-id") as HTMLInputElement).value
        if (!window.confirm(I18n.t("confirmDeleteStorage"))) return
        ApiClientModule.removeImage(petId, img.id as Int).then<Unit> {
            existingImages = existingImages.filterIndexed { i, _ -> i != index }.toTypedArray()
            updatePreviews()
        }.catch { err: dynamic -> window.alert("Failed to delete storage: ${err?.message}") }
    }

    private fun removePreview(index: Int) {
        selectedFiles.removeAt(index)
        updatePreviews()
        val dataTransfer = js("new DataTransfer()")
        selectedFiles.forEach { f -> dataTransfer.items.add(f) }
        (document.getElementById("pet-images") as? HTMLInputElement)?.asDynamic()?.files = dataTransfer.files
    }

    private fun onSubmit(e: Event) {
        e.preventDefault()
        val msg = document.getElementById("message")
        val id = (document.getElementById("pet-id") as HTMLInputElement).value
        val rescueDateVal = (document.getElementById("rescueDate") as HTMLInputElement).value
        val weight = (document.getElementById("weight") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
        val ageYears = (document.getElementById("ageYears") as HTMLInputElement).value.toIntOrNull() ?: 0
        val ageMonths = (document.getElementById("ageMonths") as HTMLInputElement).value.toIntOrNull() ?: 0
        val adoptionFee = (document.getElementById("adoptionFee") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
        val isPromoted = (document.getElementById("isPromoted") as HTMLInputElement).checked
        val promotedReason = (document.getElementById("promotedReason") as HTMLSelectElement).value.ifEmpty { null }

        fun fail(text: String) {
            msg?.className = "message error"
            msg?.textContent = text
        }
        if (weight < 0) { fail("Weight must be zero or positive"); return }
        if (ageYears < 0) { fail("Age (years) must be zero or positive"); return }
        if (ageMonths < 0 || ageMonths > 11) { fail("Age (months) must be between 0 and 11"); return }
        if (adoptionFee < 0) { fail("Adoption fee must be zero or positive"); return }
        if (isPromoted && promotedReason == null) { fail(I18n.t("promotedReasonRequired")); return }

        val data = js("({})")
        data.name = (document.getElementById("name") as HTMLInputElement).value
        data.type = (document.getElementById("type") as HTMLSelectElement).value
        data.breed = (document.getElementById("breed") as HTMLInputElement).value.ifEmpty { null }
        data.description = (document.getElementById("description") as HTMLTextAreaElement).value
        data.weight = weight
        data.ageYears = ageYears
        data.ageMonths = ageMonths
        data.sex = (document.getElementById("sex") as HTMLSelectElement).value
        data.color = (document.getElementById("color") as HTMLInputElement).value.ifEmpty { null }
        data.size = (document.getElementById("size") as HTMLSelectElement).value.ifEmpty { null }
        data.temperament = (document.getElementById("temperament") as HTMLInputElement).value.ifEmpty { null }
        data.energyLevel = (document.getElementById("energyLevel") as HTMLSelectElement).value.ifEmpty { null }
        data.isSterilized = (document.getElementById("isSterilized") as HTMLInputElement).checked
        data.isMicrochipped = (document.getElementById("isMicrochipped") as HTMLInputElement).checked
        data.microchipId = (document.getElementById("microchipId") as HTMLInputElement).value.ifEmpty { null }
        data.vaccinations = (document.getElementById("vaccinations") as HTMLTextAreaElement).value.ifEmpty { null }
        data.isGoodWithKids = (document.getElementById("isGoodWithKids") as HTMLInputElement).checked
        data.isGoodWithDogs = (document.getElementById("isGoodWithDogs") as HTMLInputElement).checked
        data.isGoodWithCats = (document.getElementById("isGoodWithCats") as HTMLInputElement).checked
        data.isHouseTrained = (document.getElementById("isHouseTrained") as HTMLInputElement).checked
        data.rescueLocation = (document.getElementById("rescueLocation") as HTMLInputElement).value.ifEmpty { null }
        data.rescueDate = if (rescueDateVal.isNotEmpty()) js("new Date(rescueDateVal)").getTime() else null
        data.specialNeeds = (document.getElementById("specialNeeds") as HTMLTextAreaElement).value.ifEmpty { null }
        data.adoptionFee = adoptionFee
        data.currency = (document.getElementById("currency") as HTMLSelectElement).value
        data.isUrgent = (document.getElementById("isUrgent") as HTMLInputElement).checked
        data.isPromoted = isPromoted
        data.promotedReason = if (isPromoted) promotedReason else null
        data.promotedReasonDetail = if (isPromoted) (document.getElementById("promotedReasonDetail") as HTMLTextAreaElement).value.ifEmpty { null } else null

        val savePromise: dynamic = if (id.isNotEmpty()) ApiClientModule.updatePet(id, data) else ApiClientModule.createPet(data)
        savePromise.then { pet: dynamic ->
            val petId = if (id.isNotEmpty()) id else pet.id.toString()
            uploadImages(petId).then<Unit> {
                uploadVideoIfSelected(petId)
            }.then<Unit> {
                msg?.className = "message success"
                msg?.textContent = "Saved!"
                (document.getElementById("form-container") as? HTMLElement)?.style?.display = "none"
                load()
            }
        }.catch { err: dynamic -> fail(err?.message?.toString() ?: "Failed to save pet") }
    }

    private fun uploadVideoIfSelected(petId: String): kotlin.js.Promise<Unit> {
        val fileInput = document.getElementById("pet-video").unsafeCast<HTMLInputElement?>()
        val file = fileInput?.asDynamic()?.files?.item(0) ?: return kotlin.js.Promise.resolve<Unit>(Unit)
        return ApiClientModule.addVideo(petId, file).then<Unit> { Unit }
    }

    private fun uploadImages(petId: String): kotlin.js.Promise<Unit> {
        if (selectedFiles.isEmpty()) return kotlin.js.Promise.resolve<Unit>(Unit)
        var chain: kotlin.js.Promise<dynamic> = kotlin.js.Promise.resolve<dynamic>(Unit)
        selectedFiles.forEachIndexed { i, file ->
            val originalName = (file.name as? String) ?: "photo.jpg"
            chain = chain.then<dynamic> {
                ImageCompression.compress(file).then<dynamic> { compressed ->
                    ApiClientModule.addImage(petId, compressed, i == 0, originalName)
                }
            }
        }
        return chain.then<Unit> { Unit }
    }
}

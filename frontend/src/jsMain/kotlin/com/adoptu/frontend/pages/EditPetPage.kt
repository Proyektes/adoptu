package com.adoptu.frontend.pages

import com.adoptu.frontend.ApiClientModule
import com.adoptu.frontend.CommonModule
import com.adoptu.frontend.I18n
import com.adoptu.frontend.ImageCompression
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event

@JsExport
@JsName("EditPetPage")
object EditPetPageModule {
    private var existingImages: Array<dynamic> = arrayOf()
    private var selectedFiles: MutableList<dynamic> = mutableListOf()
    private var currentPetIdForVideo: String? = null
    private var activePlacementId: Int? = null

    fun init() {
        window.asDynamic().setPrimaryImage = { index: dynamic -> setPrimaryImage(index.toString().toInt()) }
        window.asDynamic().removeExistingImage = { index: dynamic -> removeExistingImage(index.toString().toInt()) }
        window.asDynamic().removePreview = { index: dynamic -> removePreview(index.toString().toInt()) }
        window.asDynamic().deleteMedicalEvent = { id: dynamic -> deleteMedicalEvent(id.toString().toInt()) }

        document.getElementById("add-medical-event-btn")?.addEventListener("click", { addMedicalEvent() })
        document.getElementById("end-placement-btn")?.addEventListener("click", { endCurrentPlacement() })

        listOf("weight", "ageYears", "ageMonths").forEach { id -> clampNonNegative(id, maxMonths = id == "ageMonths") }
        clampNonNegative("adoptionFee")

        setupDropzone()
        document.getElementById("pet-form")?.addEventListener("submit", { e: Event -> onSubmit(e) })
        document.getElementById("isPromoted")?.addEventListener("change", { togglePromotedReasonRow() })

        val params = js("new URLSearchParams(location.search)")
        val id = params.get("id") as? String
        if (!id.isNullOrEmpty()) {
            ApiClientModule.getPet(id).then<Unit> { pet ->
                fillForm(pet)
                document.getElementById("form-title")?.textContent = I18n.t("editPet")
            }.catch { window.location.href = "/my-pets" }
        } else {
            document.getElementById("form-title")?.textContent = I18n.t("addPet")
        }
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
                val since = js("new Date(active.startDate)").toLocaleDateString(I18n.currentLang)
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
        val administeredDate = I18n.formatDateOnly(event.administeredDate)
        val dueHtml = if (event.nextDueDate != null) {
            val dueDateStr = I18n.formatDateOnly(event.nextDueDate)
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
                window.location.href = "/my-pets"
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

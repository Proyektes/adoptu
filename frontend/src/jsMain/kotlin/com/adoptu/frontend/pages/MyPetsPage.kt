package com.adoptu.frontend.pages

import com.adoptu.frontend.ApiClientModule
import com.adoptu.frontend.CommonModule
import com.adoptu.frontend.I18n
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

    fun init() {
        window.asDynamic().edit = { id: Int -> editPet(id) }
        window.asDynamic().del = { id: Int -> deletePet(id) }
        window.asDynamic().approveRequest = { id: Int -> approveRequest(id) }
        window.asDynamic().rejectRequest = { id: Int -> rejectRequest(id) }
        window.asDynamic().setPrimaryImage = { index: Int -> setPrimaryImage(index) }
        window.asDynamic().removeExistingImage = { index: Int -> removeExistingImage(index) }
        window.asDynamic().removePreview = { index: Int -> removePreview(index) }

        document.getElementById("add-btn")?.addEventListener("click", { openAddForm() })
        document.getElementById("cancel-btn")?.addEventListener("click", { closeForm() })

        listOf("weight", "ageYears", "ageMonths").forEach { id -> clampNonNegative(id, maxMonths = id == "ageMonths") }
        clampNonNegative("adoptionFee")

        setupDropzone()
        document.getElementById("pet-form")?.addEventListener("submit", { e: Event -> onSubmit(e) })

        load()
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
        } else "<p>${I18n.t("noPets")}</p>"

        val editId = params.get("edit") as? String
        if (!editId.isNullOrEmpty()) {
            ApiClientModule.getPet(editId).then<Unit> { pet ->
                fillForm(pet)
                document.getElementById("form-title")?.textContent = "Edit Pet"
                (document.getElementById("form-container") as? HTMLElement)?.style?.display = "block"
            }
        }

        loadAdoptionRequests(pets)
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
        val sizeHtml = if (p.size != null) "<span class=\"pet-size\">${CommonModule.escapeHtml(p.size.toString())}</span>" else ""
        val urgent = if (p.isUrgent == true) " ⚠️" else ""
        val breedHtml = if (p.breed != null) "<span class=\"pet-breed\">${CommonModule.escapeHtml(p.breed.toString())}</span>" else ""
        val rescueDateHtml = if (p.rescueDate != null) {
            val date = js("new Date(p.rescueDate)").toLocaleDateString()
            "<span class=\"label\">${I18n.t("rescued")}</span><span class=\"value\">$date</span>"
        } else ""
        return "<div class=\"pet-card\">$imageHtml<div class=\"pet-card-body\">" +
            "<span class=\"pet-type\">${I18n.t(p.type.toString().lowercase())}</span>" +
            "<span class=\"pet-sex $sexClass\">${I18n.t(p.sex.toString().lowercase())}</span>$sizeHtml" +
            "<div class=\"pet-name\"><h3>${CommonModule.escapeHtml(p.name?.toString())}$urgent</h3>$breedHtml</div>" +
            "<p class=\"pet-info\"><span class=\"pet-age\"><span class=\"label\">${I18n.t("age")}</span>" +
            "<span class=\"value\">${p.ageYears}${I18n.t("years")} ${p.ageMonths}${I18n.t("months")} • ${p.weight} kg</span></span>" +
            "<span class=\"pet-rescue-date\">$rescueDateHtml</span></p>" +
            "<p class=\"pet-status\">${CommonModule.escapeHtml(p.status?.toString())}</p>" +
            "<div class=\"pet-card-actions\"><a href=\"/pet/${p.id}\" class=\"btn\">${I18n.t("viewDetails")}</a>" +
            "<button class=\"btn btn-secondary\" onclick=\"edit(${p.id})\">${I18n.t("edit")}</button>" +
            "<button class=\"btn btn-secondary\" onclick=\"del(${p.id})\">${I18n.t("delete")}</button></div></div></div>"
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
            container?.innerHTML = "<p>No adoption requests</p>"
            return
        }
        container?.innerHTML = allRequests.joinToString("") { r ->
            val date = js("new Date(r.createdAt)").toLocaleDateString()
            val message = if (r.message != null) CommonModule.escapeHtml(r.message.toString()) else "No message"
            val actions = if (r.status == "PENDING") {
                "<div class=\"ar-actions\"><button class=\"btn btn-secondary\" onclick=\"approveRequest(${r.id})\">Approve</button>" +
                    "<button class=\"btn btn-secondary\" onclick=\"rejectRequest(${r.id})\">Reject</button></div>"
            } else ""
            "<div class=\"adoption-request-card\"><div class=\"ar-pet\">${emoji[r.petType.toString()] ?: "🐾"} ${CommonModule.escapeHtml(r.petName?.toString())}</div>" +
                "<div class=\"ar-status status-${r.status.toString().lowercase()}\">${CommonModule.escapeHtml(r.status?.toString())}</div>" +
                "<div class=\"ar-message\">$message</div><div class=\"ar-date\">$date</div>$actions</div>"
        }
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
        (document.getElementById("size") as HTMLInputElement).value = pet.size?.toString() ?: ""
        (document.getElementById("temperament") as HTMLInputElement).value = pet.temperament?.toString() ?: ""
        (document.getElementById("energyLevel") as HTMLInputElement).value = pet.energyLevel?.toString() ?: ""
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

        existingImages = (pet.images as? Array<dynamic>) ?: arrayOf()
        updatePreviews()
    }

    private fun editPet(id: Int) {
        ApiClientModule.getPet(id.toString()).then<Unit> { pet ->
            fillForm(pet)
            document.getElementById("form-title")?.textContent = "Edit Pet"
            (document.getElementById("form-container") as? HTMLElement)?.style?.display = "block"
        }
    }

    private fun deletePet(id: Int) {
        if (!window.confirm("Delete this pet?")) return
        ApiClientModule.deletePet(id.toString()).then<Unit> { load() }
    }

    private fun openAddForm() {
        (document.getElementById("pet-form") as? HTMLFormElement)?.reset()
        (document.getElementById("pet-id") as? HTMLInputElement)?.value = ""
        (document.getElementById("currency") as? HTMLSelectElement)?.value = "USD"
        selectedFiles = mutableListOf()
        existingImages = arrayOf()
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
                "<button type=\"button\" class=\"primary-btn\" onclick=\"setPrimaryImage($index)\" title=\"Set as primary\">☆</button>"
            }
            "<div class=\"preview-item$primaryClass\"><img src=\"${img.imageUrl}\">$primaryControl<button type=\"button\" onclick=\"removeExistingImage($index)\">×</button></div>"
        }.joinToString("")
        val newFilesHtml = selectedFiles.mapIndexed { index, file ->
            val url = window.asDynamic().URL.createObjectURL(file)
            "<div class=\"preview-item\"><img src=\"$url\"><button type=\"button\" onclick=\"removePreview($index)\">×</button></div>"
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
        if (!window.confirm("Delete this storage?")) return
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

        fun fail(text: String) {
            msg?.className = "message error"
            msg?.textContent = text
        }
        if (weight < 0) { fail("Weight must be zero or positive"); return }
        if (ageYears < 0) { fail("Age (years) must be zero or positive"); return }
        if (ageMonths < 0 || ageMonths > 11) { fail("Age (months) must be between 0 and 11"); return }
        if (adoptionFee < 0) { fail("Adoption fee must be zero or positive"); return }

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
        data.size = (document.getElementById("size") as HTMLInputElement).value.ifEmpty { null }
        data.temperament = (document.getElementById("temperament") as HTMLInputElement).value.ifEmpty { null }
        data.energyLevel = (document.getElementById("energyLevel") as HTMLInputElement).value.ifEmpty { null }
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

        val savePromise: dynamic = if (id.isNotEmpty()) ApiClientModule.updatePet(id, data) else ApiClientModule.createPet(data)
        savePromise.then { pet: dynamic ->
            val petId = if (id.isNotEmpty()) id else pet.id.toString()
            uploadImages(petId).then<Unit> {
                msg?.className = "message success"
                msg?.textContent = "Saved!"
                (document.getElementById("form-container") as? HTMLElement)?.style?.display = "none"
                load()
            }
        }.catch { err: dynamic -> fail(err?.message?.toString() ?: "Failed to save pet") }
    }

    private fun uploadImages(petId: String): kotlin.js.Promise<Unit> {
        if (selectedFiles.isEmpty()) return kotlin.js.Promise.resolve<Unit>(Unit)
        var chain: kotlin.js.Promise<dynamic> = kotlin.js.Promise.resolve<dynamic>(Unit)
        selectedFiles.forEachIndexed { i, file ->
            chain = chain.then<dynamic> { ApiClientModule.addImage(petId, file, i == 0) }
        }
        return chain.then<Unit> { Unit }
    }
}

package com.adoptu.frontend.pages

import com.adoptu.frontend.ApiClientModule
import com.adoptu.frontend.I18n
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
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
        if (pet.breed != null && pet.breed.toString().isNotEmpty()) sb.append("<p class=\"pet-breed\">${pet.breed}</p>")
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
        if (pet.rescueLocation != null && pet.rescueLocation.toString().isNotEmpty()) sb.append("<div class=\"detail-section\"><strong>${I18n.t("rescueLocation")}:</strong> ${pet.rescueLocation}</div>")
        if (pet.specialNeeds != null && pet.specialNeeds.toString().isNotEmpty()) sb.append("<div class=\"detail-section\"><strong>${I18n.t("specialNeeds")}:</strong><p>${pet.specialNeeds}</p></div>")
        val adoptionFee = pet.adoptionFee?.unsafeCast<Double?>() ?: 0.0
        if (adoptionFee > 0) sb.append("<div class=\"detail-section\"><strong>${I18n.t("adoptionFee")}:</strong> ${currencySymbols[pet.currency.toString()] ?: "$"}$adoptionFee ${pet.currency}</div>")
        if (pet.isUrgent == true) sb.append("<div class=\"urgent-badge\">${I18n.t("urgentBadge")}</div>")

        sb.append("<button type=\"button\" class=\"btn btn-secondary\" id=\"share-pet-btn\">${I18n.t("share")}</button>")

        if (canAdopt) {
            sb.append("<form id=\"adopt-form\"><label for=\"msg\">${I18n.t("messageOptional")}</label><textarea id=\"msg\" name=\"message\"></textarea><button type=\"submit\" class=\"btn\">${I18n.t("requestAdoption")}</button></form>")
        }
        if (isOwner) {
            sb.append("<a href=\"/my-pets?edit=${pet.id}\" class=\"btn\">${I18n.t("editPet")}</a>")
        }
        sb.append("</div>")

        container.innerHTML = sb.toString()

        document.getElementById("share-pet-btn")?.addEventListener("click", { shareCurrentPet() })

        val form = document.getElementById("adopt-form")
        form?.addEventListener("submit", { e: Event ->
            e.preventDefault()
            if (user.id == null) {
                window.location.href = "/login"
                return@addEventListener
            }
            val msg = (document.getElementById("msg") as? HTMLTextAreaElement)?.value ?: ""
            ApiClientModule.adoptPet(petId, msg).then<Unit> {
                (document.getElementById("message") as? HTMLElement)?.let {
                    it.className = "message success"
                    it.textContent = I18n.t("adoptionRequestSubmitted")
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

    // Web Share API (mobile browsers - one native tap opens the OS share sheet, WhatsApp included)
    // where available; desktop/unsupported browsers fall back to a direct WhatsApp share link.
    private fun shareCurrentPet() {
        val pet = currentPet ?: return
        val url = window.location.href
        val text = "${pet.name} - ${I18n.t("adoptU")}"
        val share = window.navigator.asDynamic().share
        if (share != null) {
            window.navigator.asDynamic().share(json("title" to text, "url" to url))
        } else {
            val encoded = window.asDynamic().encodeURIComponent("$text $url")
            window.open("https://wa.me/?text=$encoded", "_blank")
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

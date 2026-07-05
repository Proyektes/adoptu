package com.adoptu.frontend.pages

import com.adoptu.frontend.ApiClientModule
import com.adoptu.frontend.I18n
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event

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
        sb.append("<span class=\"pet-type\">${I18n.t(pet.type.toString().lowercase())}</span><h1>${pet.name}</h1>")
        if (pet.breed != null && pet.breed.toString().isNotEmpty()) sb.append("<p class=\"pet-breed\">${pet.breed}</p>")
        sb.append("<p><strong>Weight:</strong> ${pet.weight} kg | <strong>Age:</strong> ${pet.ageYears}y ${pet.ageMonths}m | <strong>Sex:</strong> ${I18n.t(pet.sex.toString().lowercase())}</p>")
        sb.append("<p><strong>Status:</strong> ${pet.status}</p></div>")

        sb.append("<div class=\"pet-detail-body\">")
        if (isOwner) {
            sb.append("<div class=\"storage-management\"><h3>Photos</h3><div class=\"pet-images-grid\" id=\"pet-images\">${renderImages()}</div></div>")
        }
        val description = pet.description?.toString()?.takeIf { it.isNotEmpty() } ?: "No description."
        sb.append("<p>$description</p>")

        sb.append("<div class=\"pet-details-grid\">")
        if (pet.color != null && pet.color.toString().isNotEmpty()) sb.append("<div class=\"detail-item\"><strong>Color:</strong> ${pet.color}</div>")
        if (pet.size != null && pet.size.toString().isNotEmpty()) sb.append("<div class=\"detail-item\"><strong>Size:</strong> ${pet.size}</div>")
        if (pet.temperament != null && pet.temperament.toString().isNotEmpty()) sb.append("<div class=\"detail-item\"><strong>Temperament:</strong> ${pet.temperament}</div>")
        if (pet.energyLevel != null && pet.energyLevel.toString().isNotEmpty()) sb.append("<div class=\"detail-item\"><strong>Energy:</strong> ${pet.energyLevel}</div>")
        sb.append("</div>")

        sb.append("<div class=\"pet-details-grid\">")
        sb.append("<div class=\"detail-item\"><strong>Sterilized:</strong> ${if (pet.isSterilized == true) "Yes" else "No"}</div>")
        sb.append("<div class=\"detail-item\"><strong>Microchipped:</strong> ${if (pet.isMicrochipped == true) "Yes" else "No"}</div>")
        if (pet.microchipId != null && pet.microchipId.toString().isNotEmpty()) sb.append("<div class=\"detail-item\"><strong>Microchip ID:</strong> ${pet.microchipId}</div>")
        sb.append("</div>")

        sb.append("<div class=\"pet-details-grid\">")
        sb.append("<div class=\"detail-item\"><strong>Good with kids:</strong> ${if (pet.isGoodWithKids == true) "Yes" else "No"}</div>")
        sb.append("<div class=\"detail-item\"><strong>Good with dogs:</strong> ${if (pet.isGoodWithDogs == true) "Yes" else "No"}</div>")
        sb.append("<div class=\"detail-item\"><strong>Good with cats:</strong> ${if (pet.isGoodWithCats == true) "Yes" else "No"}</div>")
        sb.append("<div class=\"detail-item\"><strong>House trained:</strong> ${if (pet.isHouseTrained == true) "Yes" else "No"}</div>")
        sb.append("</div>")

        if (pet.vaccinations != null && pet.vaccinations.toString().isNotEmpty()) sb.append("<div class=\"detail-section\"><strong>Vaccinations:</strong><p>${pet.vaccinations}</p></div>")
        if (pet.rescueLocation != null && pet.rescueLocation.toString().isNotEmpty()) sb.append("<div class=\"detail-section\"><strong>Rescue Location:</strong> ${pet.rescueLocation}</div>")
        if (pet.specialNeeds != null && pet.specialNeeds.toString().isNotEmpty()) sb.append("<div class=\"detail-section\"><strong>Special Needs:</strong><p>${pet.specialNeeds}</p></div>")
        val adoptionFee = pet.adoptionFee?.unsafeCast<Double?>() ?: 0.0
        if (adoptionFee > 0) sb.append("<div class=\"detail-section\"><strong>Adoption Fee:</strong> ${currencySymbols[pet.currency.toString()] ?: "$"}$adoptionFee ${pet.currency}</div>")
        if (pet.isUrgent == true) sb.append("<div class=\"urgent-badge\">URGENT - Needs home soon!</div>")

        if (canAdopt) {
            sb.append("<form id=\"adopt-form\"><label for=\"msg\">Message (optional)</label><textarea id=\"msg\" name=\"message\"></textarea><button type=\"submit\" class=\"btn\">Request Adoption</button></form>")
        }
        if (isOwner) {
            sb.append("<a href=\"/my-pets?edit=${pet.id}\" class=\"btn\">Edit Pet</a>")
        }
        sb.append("</div>")

        container.innerHTML = sb.toString()

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
                    it.textContent = "Adoption request submitted!"
                }
                form.unsafeCast<HTMLElement>().style.display = "none"
            }.catch { err: dynamic ->
                (document.getElementById("message") as? HTMLElement)?.let {
                    it.className = "message error"
                    it.textContent = err?.message?.toString() ?: "Failed to submit request"
                }
            }
        })
    }

    private fun renderImages(): String {
        val images = currentPet?.images as? Array<dynamic>
        if (images == null || images.isEmpty()) return "<p>No photos yet.</p>"
        return images.joinToString("") { img ->
            val primaryClass = if (img.isPrimary == true) " primary" else ""
            val badge = if (img.isPrimary == true) "<span class=\"primary-badge\">Primary</span>" else ""
            "<div class=\"pet-image-item$primaryClass\"><img src=\"${img.imageUrl}\" alt=\"Pet photo\">$badge</div>"
        }
    }
}

package com.adoptu.services

import com.adoptu.dto.input.AdoptionExperience
import com.adoptu.dto.input.AdoptionRequestDto
import com.adoptu.dto.input.CreatePetRequest
import com.adoptu.dto.input.HousingType
import com.adoptu.dto.input.PetDto
import com.adoptu.dto.input.PetImageDto
import com.adoptu.dto.input.Status
import com.adoptu.dto.input.UpdatePetRequest
import com.adoptu.dto.input.UserRole
import com.adoptu.dto.output.PagedResult
import com.adoptu.ports.ImageStoragePort
import com.adoptu.ports.NotificationPort
import com.adoptu.ports.PetRepositoryPort
import com.adoptu.ports.SavedSearchRepositoryPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PetService(
    private val petRepository: PetRepositoryPort,
    private val imageStorage: ImageStoragePort,
    private val notificationPort: NotificationPort,
    private val userService: UserService,
    private val savedSearchRepository: SavedSearchRepositoryPort,
    private val baseUrl: String = "http://localhost:80"
) {

    suspend fun getAll(type: String? = null, showPromotedOnly: Boolean = false, country: String): List<PetDto> {
        require(country.isNotBlank()) { "Country is required" }
        return petRepository.getAll(type, showPromotedOnly, country)
    }

    // Unfiltered listing (any status/country, no rescuer-role restriction) backing the
    // rescuer/admin "my pets" management page - it must keep showing legacy pets that have
    // no country set yet, which the country-required public getAll() above would hide.
    suspend fun getMine(): List<PetDto> = petRepository.getAllUnfiltered()

    suspend fun getAllForAdmin(page: Int = 1, pageSize: Int = 20, search: String? = null, includeInactive: Boolean = false): PagedResult<PetDto> =
        petRepository.getAllForAdmin(page, pageSize, search, includeInactive)

    suspend fun deactivatePet(petId: Int, deactivatedBy: Int): Boolean = petRepository.deactivatePet(petId, deactivatedBy)

    suspend fun reactivatePet(petId: Int): Boolean = petRepository.reactivatePet(petId)

    suspend fun getById(id: Int): PetDto? = petRepository.getById(id)

    suspend fun create(rescuerId: Int, request: CreatePetRequest): PetDto {
        require(request.weight >= 0) { "Weight must be zero or positive" }
        require(request.ageYears >= 0) { "Age (years) must be zero or positive" }
        require(request.ageMonths >= 0) { "Age (months) must be zero or positive" }
        require(request.ageMonths < 12) { "Age (months) must be less than 12" }
        require(request.adoptionFee >= 0) { "Adoption fee must be zero or positive" }
        if (request.isPromoted) {
            requireNotNull(request.promotedReason) { "A reason is required when marking a pet as needing a new home" }
        }
        val resolvedCountry = request.country?.takeIf { it.isNotBlank() }
            ?: userService.getById(rescuerId)?.country
        require(!resolvedCountry.isNullOrBlank()) {
            "Country is required - set one on your profile or specify one for this pet"
        }
        val pet = petRepository.create(
            rescuerId = rescuerId,
            name = request.name,
            type = request.type,
            breed = request.breed,
            description = request.description,
            weight = request.weight,
            ageYears = request.ageYears,
            ageMonths = request.ageMonths,
            sex = request.sex,
            color = request.color,
            size = request.size,
            temperament = request.temperament,
            isSterilized = request.isSterilized,
            isMicrochipped = request.isMicrochipped,
            microchipId = request.microchipId,
            vaccinations = request.vaccinations,
            isGoodWithKids = request.isGoodWithKids,
            isGoodWithDogs = request.isGoodWithDogs,
            isGoodWithCats = request.isGoodWithCats,
            isHouseTrained = request.isHouseTrained,
            energyLevel = request.energyLevel,
            rescueDate = request.rescueDate,
            rescueLocation = request.rescueLocation,
            country = resolvedCountry,
            specialNeeds = request.specialNeeds,
            adoptionFee = request.adoptionFee,
            currency = request.currency,
            isUrgent = request.isUrgent,
            isPromoted = request.isPromoted,
            promotedReason = request.promotedReason,
            promotedReasonDetail = request.promotedReasonDetail
        )
        CoroutineScope(Dispatchers.IO).launch { notifySavedSearchMatches(pet) }
        return pet
    }

    private suspend fun notifySavedSearchMatches(pet: PetDto) {
        val country = pet.country ?: return
        val matches = savedSearchRepository.getMatching(pet.type, country)
        matches.forEach { search ->
            val user = userService.getById(search.userId) ?: return@forEach
            notificationPort.sendEmail(
                to = user.username,
                subject = "A new pet matching your saved search - Adopt-U",
                body = "${pet.name} (${pet.type.lowercase()}) was just listed in $country - take a look: $baseUrl/pet/${pet.id}"
            )
        }
    }

    suspend fun update(id: Int, userId: Int, userRoles: Set<String>, body: UpdatePetRequest): ServiceResult<PetDto> {
        body.weight?.let { require(it >= 0) { "Weight must be zero or positive" } }
        body.ageYears?.let { require(it >= 0) { "Age (years) must be zero or positive" } }
        body.ageMonths?.let { require(it >= 0) { "Age (months) must be zero or positive" } }
        body.ageMonths?.let { require(it < 12) { "Age (months) must be less than 12" } }
        body.adoptionFee?.let { require(it >= 0) { "Adoption fee must be zero or positive" } }
        val existing = petRepository.getById(id) ?: return ServiceResult.NotFound
        if (body.isPromoted == true && body.promotedReason == null && existing.promotedReason == null) {
            return ServiceResult.Error("A reason is required when marking a pet as needing a new home")
        }
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && existing.rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        val pet = petRepository.update(id, body)
        return if (pet != null) ServiceResult.Success(pet) else ServiceResult.NotFound
    }

    suspend fun delete(id: Int, userId: Int, userRoles: Set<String>): ServiceResult<Unit> {
        val existing = petRepository.getById(id) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && existing.rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        petRepository.delete(id)
        return ServiceResult.Success(Unit)
    }

    suspend fun addImage(petId: Int, userId: Int, userRoles: Set<String>, imageUrl: String, isPrimary: Boolean): ServiceResult<PetImageDto> {
        val existing = petRepository.getById(petId) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && existing.rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        val image = petRepository.addImage(petId, imageUrl, isPrimary)
        return ServiceResult.Success(image)
    }

    suspend fun uploadAndAddImage(
        petId: Int,
        userId: Int,
        userRoles: Set<String>,
        imageName: String,
        contentType: String,
        imageData: ByteArray,
        isPrimary: Boolean
    ): ServiceResult<PetImageDto> {
        val existing = petRepository.getById(petId) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && existing.rescuerId != userId) {
            return ServiceResult.Forbidden
        }

        // Client-controlled header - restrict to what this method can actually validate. JPEG/PNG
        // go through ImageCompressor's real decode; WebP has no JVM decoder without a native/JNI
        // dependency, so it's stored as-is (client already resized/compressed it) after
        // WebPDimensionValidator checks the RIFF/VP8 header - a spoofed content-type paired with a
        // polyglot file that fails both would otherwise get stored and served back as non-image data.
        val normalizedContentType = contentType.substringBefore(";").trim().lowercase()
        if (normalizedContentType !in ALLOWED_IMAGE_CONTENT_TYPES) {
            return ServiceResult.Error("Unsupported image type. Allowed: JPEG, PNG, WebP")
        }
        if (imageData.size > MAX_IMAGE_BYTES) {
            return ServiceResult.Error("Image exceeds maximum size of ${MAX_IMAGE_BYTES / (1024 * 1024)}MB")
        }

        val uploadBytes = try {
            if (normalizedContentType == "image/webp") {
                WebPDimensionValidator.validate(imageData)
                imageData
            } else {
                val format = if (normalizedContentType == "image/png") "png" else "jpg"
                ImageCompressor.compress(imageData.inputStream(), format).toByteArray()
            }
        } catch (e: IllegalArgumentException) {
            return ServiceResult.Error(e.message ?: "Invalid image data")
        }
        val imageUrl = imageStorage.uploadImage(petId, imageName, normalizedContentType, uploadBytes.inputStream())

        val image = petRepository.addImage(petId, imageUrl, isPrimary)
        return ServiceResult.Success(image)
    }

    companion object {
        private val ALLOWED_IMAGE_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        // Client compresses to ~2MB before upload; this is a defense-in-depth ceiling for
        // clients that skip/bypass that step, not the expected upload size.
        private const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
        private val ALLOWED_VIDEO_CONTENT_TYPES = setOf("video/mp4", "video/webm")
        // No transcoding pipeline (unlike images) - stored as whatever the browser uploaded, so
        // this ceiling is the real expected max, not just a defense-in-depth backstop.
        private const val MAX_VIDEO_BYTES = 50 * 1024 * 1024
    }

    suspend fun uploadAndSetVideo(
        petId: Int,
        userId: Int,
        userRoles: Set<String>,
        videoName: String,
        contentType: String,
        videoData: ByteArray
    ): ServiceResult<PetDto> {
        val existing = petRepository.getById(petId) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && existing.rescuerId != userId) {
            return ServiceResult.Forbidden
        }

        val normalizedContentType = contentType.substringBefore(";").trim().lowercase()
        if (normalizedContentType !in ALLOWED_VIDEO_CONTENT_TYPES) {
            return ServiceResult.Error("Unsupported video type. Allowed: MP4, WebM")
        }
        if (videoData.size > MAX_VIDEO_BYTES) {
            return ServiceResult.Error("Video exceeds maximum size of ${MAX_VIDEO_BYTES / (1024 * 1024)}MB")
        }

        existing.videoUrl?.let { imageStorage.deleteImage(it) }
        val videoUrl = imageStorage.uploadImage(petId, videoName, normalizedContentType, videoData.inputStream())
        val updated = petRepository.setVideo(petId, videoUrl) ?: return ServiceResult.NotFound
        return ServiceResult.Success(updated)
    }

    suspend fun removeVideo(petId: Int, userId: Int, userRoles: Set<String>): ServiceResult<PetDto> {
        val existing = petRepository.getById(petId) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && existing.rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        existing.videoUrl?.let { imageStorage.deleteImage(it) }
        val updated = petRepository.setVideo(petId, null) ?: return ServiceResult.NotFound
        return ServiceResult.Success(updated)
    }

    suspend fun removeImage(petId: Int, imageId: Int, userId: Int, userRoles: Set<String>): ServiceResult<Unit> {
        val existing = petRepository.getById(petId) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && existing.rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        val images = petRepository.getImages(petId)
        val image = images.find { it.id == imageId } ?: return ServiceResult.NotFound
        imageStorage.deleteImage(image.imageUrl)
        petRepository.removeImage(petId, imageId)
        return ServiceResult.Success(Unit)
    }

    suspend fun updatePetImages(petId: Int, userId: Int, userRoles: Set<String>, imageIds: List<Int>): ServiceResult<List<PetImageDto>> {
        val existing = petRepository.getById(petId) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && existing.rescuerId != userId) {
            return ServiceResult.Forbidden
        }

        val existingImages = petRepository.getImages(petId)
        val existingImageIds = existingImages.map { it.id }.toSet()
        val incomingImageIds = imageIds.toSet()
        val imageIdsToDelete = existingImageIds - incomingImageIds

        imageIdsToDelete.forEach { imageId ->
            val image = existingImages.find { it.id == imageId }
            if (image != null) {
                imageStorage.deleteImage(image.imageUrl)
                petRepository.removeImage(petId, imageId)
            }
        }

        val remainingImages = petRepository.getImages(petId)
        return ServiceResult.Success(remainingImages)
    }

    suspend fun setPrimaryImage(petId: Int, imageId: Int, userId: Int, userRoles: Set<String>): ServiceResult<Unit> {
        val existing = petRepository.getById(petId) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && existing.rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        val success = petRepository.setPrimaryImage(petId, imageId)
        return if (success) ServiceResult.Success(Unit) else ServiceResult.NotFound
    }

    suspend fun getImages(petId: Int): List<PetImageDto> = petRepository.getImages(petId)

    suspend fun createAdoptionRequest(
        petId: Int,
        adopterId: Int,
        message: String,
        housingType: HousingType? = null,
        hasYard: Boolean? = null,
        hasOtherPets: Boolean? = null,
        experienceLevel: AdoptionExperience? = null
    ): AdoptionRequestDto {
        val request = petRepository.createAdoptionRequest(petId, adopterId, message, housingType, hasYard, hasOtherPets, experienceLevel)

        val pet = petRepository.getById(petId)
        if (pet != null) {
            val rescuer = userService.getById(pet.rescuerId)
            val adopter = userService.getById(adopterId)
            if (rescuer?.username != null && rescuer.activeRoles.contains(UserRole.RESCUER) && adopter != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    notificationPort.sendAdoptionRequestNotification(
                        rescuerEmail = rescuer.username,
                        petName = pet.name,
                        adopterName = adopter.displayName,
                        message = message
                    )
                }
            }
        }

        return request
    }

    suspend fun getAdoptionRequestsForPet(petId: Int, userId: Int, userRoles: Set<String>): ServiceResult<List<AdoptionRequestDto>> {
        val pet = petRepository.getById(petId) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && pet.rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        return ServiceResult.Success(petRepository.getAdoptionRequestsForPet(petId))
    }

    // Adopter's own view of their requests - reviewNote is rescuer-private and must never reach
    // the person being reviewed, so it's stripped here rather than at the DTO/repository level
    // (which is shared with the rescuer-facing getAdoptionRequestsForPet above).
    suspend fun getMyAdoptionRequests(userId: Int): List<AdoptionRequestDto> {
        return petRepository.getAdoptionRequestsForUser(userId).map { it.copy(reviewNote = null) }
    }

    suspend fun updateAdoptionRequest(
        requestId: Int,
        status: String,
        userId: Int,
        userRoles: Set<String>,
        reviewNote: String? = null
    ): ServiceResult<AdoptionRequestDto> {
        val request = petRepository.getAdoptionRequestById(requestId) ?: return ServiceResult.NotFound
        val pet = petRepository.getById(request.petId) ?: return ServiceResult.NotFound
        val isAdmin = userRoles.contains("ADMIN")
        if (!isAdmin && pet.rescuerId != userId) {
            return ServiceResult.Forbidden
        }
        // A note-only save re-sends the request's current status (which may still be PENDING)
        // rather than a real transition - allow that even though PENDING itself is never a valid
        // transition *target*.
        val isRealTransition = status != request.status
        if (isRealTransition && status !in listOf("UNDER_REVIEW", "APPROVED", "REJECTED")) {
            return ServiceResult.Forbidden
        }
        petRepository.updateAdoptionRequestStatus(requestId, status, reviewNote)

        if (isRealTransition && status == "APPROVED") {
            petRepository.update(request.petId, UpdatePetRequest(status = Status.ADOPTED))
            petRepository.getAdoptionRequestsForPet(request.petId)
                .filter { it.id != requestId && it.status in listOf("PENDING", "UNDER_REVIEW") }
                .forEach { petRepository.updateAdoptionRequestStatus(it.id, "REJECTED") }
        }

        if (isRealTransition) {
            notifyAdopterOfStatusChange(request, pet, status)
        }

        val updatedRequest = petRepository.getAdoptionRequestById(requestId)
        return if (updatedRequest != null) ServiceResult.Success(updatedRequest) else ServiceResult.NotFound
    }

    private suspend fun notifyAdopterOfStatusChange(request: AdoptionRequestDto, pet: PetDto, status: String) {
        val adopter = userService.getById(request.adopterId) ?: return
        val statusText = when (status) {
            "UNDER_REVIEW" -> "is now under review"
            "APPROVED" -> "was approved"
            "REJECTED" -> "was not approved this time"
            else -> return
        }
        CoroutineScope(Dispatchers.IO).launch {
            notificationPort.sendEmail(
                to = adopter.username,
                subject = "Update on your adoption request for ${pet.name} - Adopt-U",
                body = "Hi ${adopter.displayName},\n\nYour adoption request for ${pet.name} $statusText. " +
                    "$baseUrl/pet/${pet.id}"
            )
        }
    }
}

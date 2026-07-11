package com.adoptu.ports

import com.adoptu.dto.input.AdoptionRequestDto
import com.adoptu.dto.input.Currency
import com.adoptu.dto.input.Gender
import com.adoptu.dto.input.PetDto
import com.adoptu.dto.input.PetImageDto
import com.adoptu.dto.input.UpdatePetRequest
import com.adoptu.dto.output.PagedResult

interface PetRepositoryPort {
    suspend fun getAll(type: String? = null, showPromotedOnly: Boolean = false, country: String): List<PetDto>
    // Returns every pet regardless of status/country/rescuer-role, for the rescuer/admin
    // "my pets" management view - which must keep working for legacy pets with no country set.
    suspend fun getAllUnfiltered(): List<PetDto>
    // Admin-only paginated/searchable overview (kept separate from getAllUnfiltered/"my pets",
    // which has no pagination and stays that way for the rescuer self-service page). No
    // default param values: every call site is PetService.getAllForAdmin(), which already
    // declares its own defaults and always passes explicit args through.
    suspend fun getAllForAdmin(page: Int, pageSize: Int, search: String?, includeInactive: Boolean): PagedResult<PetDto>
    suspend fun getById(id: Int): PetDto?
    suspend fun deactivatePet(petId: Int, deactivatedBy: Int): Boolean
    suspend fun reactivatePet(petId: Int): Boolean
    suspend fun create(
        rescuerId: Int,
        name: String,
        type: String,
        description: String,
        weight: Double,
        ageYears: Int,
        ageMonths: Int,
        sex: Gender,
        breed: String? = null,
        color: String? = null,
        size: String? = null,
        temperament: String? = null,
        isSterilized: Boolean = false,
        isMicrochipped: Boolean = false,
        microchipId: String? = null,
        vaccinations: String? = null,
        isGoodWithKids: Boolean = true,
        isGoodWithDogs: Boolean = true,
        isGoodWithCats: Boolean = true,
        isHouseTrained: Boolean = false,
        energyLevel: String? = null,
        rescueDate: Long? = null,
        rescueLocation: String? = null,
        country: String? = null,
        specialNeeds: String? = null,
        adoptionFee: Double = 0.0,
        currency: Currency = Currency.USD,
        isUrgent: Boolean = false,
        isPromoted: Boolean = false,
        status: String = "AVAILABLE"
    ): PetDto
    suspend fun update(id: Int, body: UpdatePetRequest): PetDto?
    suspend fun delete(petId: Int)
    suspend fun createAdoptionRequest(petId: Int, adopterId: Int, message: String): AdoptionRequestDto
    suspend fun getAdoptionRequestsForPet(petId: Int): List<AdoptionRequestDto>
    suspend fun getAdoptionRequestsForUser(userId: Int): List<AdoptionRequestDto>
    suspend fun updateAdoptionRequestStatus(requestId: Int, status: String): Boolean
    suspend fun getAdoptionRequestById(requestId: Int): AdoptionRequestDto?
    suspend fun addImage(petId: Int, imageUrl: String, isPrimary: Boolean = false, sortOrder: Int = 0): PetImageDto
    suspend fun removeImage(petId: Int, imageId: Int): Boolean
    suspend fun setPrimaryImage(petId: Int, imageId: Int): Boolean
    suspend fun getImages(petId: Int): List<PetImageDto>
}

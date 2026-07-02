package com.adoptu.dto.input

import kotlinx.serialization.Serializable

enum class Gender {
    MALE, FEMALE
}

enum class Currency {
    USD, EUR, GBP, CAD, AUD,
    MXN, ARS, CLP, COP, BRL, PEN, UYU, PYG, BOB, VES, CRC, GTQ, HNL, NIO, DOP, PAB
}

enum class Status {
    AVAILABLE, ADOPTED, DISABLED, PENDING
}

@Serializable
data class PetDto(
    val id: Int,
    val rescuerId: Int,
    val name: String,
    val type: String,
    val breed: String? = null,
    val description: String,
    val weight: Double,
    val ageYears: Int,
    val ageMonths: Int,
    val sex: Gender,
    val status: Status,
    val color: String? = null,
    val size: String? = null,
    val temperament: String? = null,
    val isSterilized: Boolean = false,
    val isMicrochipped: Boolean = false,
    val microchipId: String? = null,
    val vaccinations: String? = null,
    val isGoodWithKids: Boolean = true,
    val isGoodWithDogs: Boolean = true,
    val isGoodWithCats: Boolean = true,
    val isHouseTrained: Boolean = false,
    val energyLevel: String? = null,
    val rescueDate: Long? = null,
    val rescueLocation: String? = null,
    val country: String? = null,
    val specialNeeds: String? = null,
    val adoptionFee: Double = 0.0,
    val currency: Currency = Currency.USD,
    val isUrgent: Boolean = false,
    val isPromoted: Boolean = false,
    val createdAt: Long,
    val images: List<PetImageDto> = emptyList()
)

@Serializable
data class PetImageDto(
    val id: Int,
    val imageUrl: String,
    val isPrimary: Boolean,
    val sortOrder: Int
)

@Serializable
data class CreatePetRequest(
    val name: String,
    val type: String,
    val breed: String? = null,
    val description: String = "",
    val weight: Double = 0.0,
    val ageYears: Int = 0,
    val ageMonths: Int = 0,
    val sex: Gender = Gender.MALE,
    val color: String? = null,
    val size: String? = null,
    val temperament: String? = null,
    val isSterilized: Boolean = false,
    val isMicrochipped: Boolean = false,
    val microchipId: String? = null,
    val vaccinations: String? = null,
    val isGoodWithKids: Boolean = true,
    val isGoodWithDogs: Boolean = true,
    val isGoodWithCats: Boolean = true,
    val isHouseTrained: Boolean = false,
    val energyLevel: String? = null,
    val rescueDate: Long? = null,
    val rescueLocation: String? = null,
    // Optional here: PetService.create() defaults this from the rescuer's own Users.country
    // profile field when omitted, then validates that a country is present one way or another.
    val country: String? = null,
    val specialNeeds: String? = null,
    val adoptionFee: Double = 0.0,
    val currency: Currency = Currency.USD,
    val isUrgent: Boolean = false,
    val isPromoted: Boolean = false
)

@Serializable
data class UpdatePetRequest(
    val name: String? = null,
    val type: String? = null,
    val breed: String? = null,
    val description: String? = null,
    val weight: Double? = null,
    val ageYears: Int? = null,
    val ageMonths: Int? = null,
    val sex: Gender? = null,
    val status: Status? = null,
    val color: String? = null,
    val size: String? = null,
    val temperament: String? = null,
    val isSterilized: Boolean? = null,
    val isMicrochipped: Boolean? = null,
    val microchipId: String? = null,
    val vaccinations: String? = null,
    val isGoodWithKids: Boolean? = null,
    val isGoodWithDogs: Boolean? = null,
    val isGoodWithCats: Boolean? = null,
    val isHouseTrained: Boolean? = null,
    val energyLevel: String? = null,
    val rescueDate: Long? = null,
    val rescueLocation: String? = null,
    val country: String? = null,
    val specialNeeds: String? = null,
    val adoptionFee: Double? = null,
    val currency: Currency? = null,
    val isUrgent: Boolean? = null,
    val isPromoted: Boolean? = null
)




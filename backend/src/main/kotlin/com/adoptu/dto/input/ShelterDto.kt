package com.adoptu.dto.input

import com.universaliun.formats.json.JsonDecodable
import com.universaliun.formats.json.JsonEncodable

@JsonDecodable(strict = false)
@JsonEncodable
data class ShelterDto(
    val id: Int,
    val userId: Int? = null,
    val name: String,
    val country: String,
    val state: String? = null,
    val city: String,
    val neighborhood: String? = null,
    val address: String,
    val zip: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    val fiscalId: String? = null,
    val bankName: String? = null,
    val accountHolderName: String? = null,
    val accountNumber: String? = null,
    val iban: String? = null,
    val swiftBic: String? = null,
    val currency: String = "USD",
    val description: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@JsonDecodable(strict = false)
@JsonEncodable
data class CreateShelterRequest(
    val name: String,
    val country: String,
    val state: String? = null,
    val city: String,
    val neighborhood: String? = null,
    val address: String,
    val zip: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    val fiscalId: String? = null,
    val bankName: String? = null,
    val accountHolderName: String? = null,
    val accountNumber: String? = null,
    val iban: String? = null,
    val swiftBic: String? = null,
    val currency: String = "USD",
    val description: String? = null
)

@JsonDecodable(strict = false)
@JsonEncodable
data class UpdateShelterRequest(
    val name: String? = null,
    val country: String? = null,
    val state: String? = null,
    val city: String? = null,
    val neighborhood: String? = null,
    val address: String? = null,
    val zip: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    val fiscalId: String? = null,
    val bankName: String? = null,
    val accountHolderName: String? = null,
    val accountNumber: String? = null,
    val iban: String? = null,
    val swiftBic: String? = null,
    val currency: String? = null,
    val description: String? = null
)

@JsonDecodable(strict = false)
@JsonEncodable
data class UserShelterDto(
    val userId: Int,
    val name: String,
    val country: String,
    val state: String? = null,
    val city: String,
    val neighborhood: String? = null,
    val address: String,
    val zip: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val website: String? = null,
    val fiscalId: String? = null,
    val bankName: String? = null,
    val accountHolderName: String? = null,
    val accountNumber: String? = null,
    val iban: String? = null,
    val swiftBic: String? = null,
    val currency: String = "USD",
    val description: String? = null,
    val createdAt: Long
)

@JsonDecodable(strict = false)
@JsonEncodable
data class CreateUserShelterRequest(
    val name: String,
    val country: String,
    val state: String? = null,
    val city: String,
    val neighborhood: String? = null,
    val address: String,
    val zip: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    val fiscalId: String? = null,
    val bankName: String? = null,
    val accountHolderName: String? = null,
    val accountNumber: String? = null,
    val iban: String? = null,
    val swiftBic: String? = null,
    val currency: String = "USD",
    val description: String? = null
)

@JsonDecodable(strict = false)
@JsonEncodable
data class UpdateUserShelterRequest(
    val name: String? = null,
    val country: String? = null,
    val state: String? = null,
    val city: String? = null,
    val neighborhood: String? = null,
    val address: String? = null,
    val zip: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    val fiscalId: String? = null,
    val bankName: String? = null,
    val accountHolderName: String? = null,
    val accountNumber: String? = null,
    val iban: String? = null,
    val swiftBic: String? = null,
    val currency: String? = null,
    val description: String? = null
)

package com.adoptu.dto.input

import com.universaliun.formats.json.JsonDecodable
import com.universaliun.formats.json.JsonEncodable

@JsonDecodable(strict = false)
@JsonEncodable
data class SterilizationLocationDto(
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
    val description: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@JsonDecodable(strict = false)
@JsonEncodable
data class CreateSterilizationLocationRequest(
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
    val description: String? = null
)

@JsonDecodable(strict = false)
@JsonEncodable
data class UpdateSterilizationLocationRequest(
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
    val description: String? = null
)

@JsonDecodable(strict = false)
@JsonEncodable
data class SterilizationLocationsByLocation(
    val country: String,
    val states: List<SterilizationLocationsByState>
)

@JsonDecodable(strict = false)
@JsonEncodable
data class SterilizationLocationsByState(
    val state: String?,
    val cities: List<SterilizationLocationsByCity>
)

@JsonDecodable(strict = false)
@JsonEncodable
data class SterilizationLocationsByCity(
    val city: String,
    val locations: List<SterilizationLocationDto>
)

@JsonDecodable(strict = false)
@JsonEncodable
data class UserSterilizationLocationDto(
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
    val description: String? = null,
    val createdAt: Long
)

@JsonDecodable(strict = false)
@JsonEncodable
data class CreateUserSterilizationLocationRequest(
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
    val description: String? = null
)

@JsonDecodable(strict = false)
@JsonEncodable
data class UpdateUserSterilizationLocationRequest(
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
    val description: String? = null
)

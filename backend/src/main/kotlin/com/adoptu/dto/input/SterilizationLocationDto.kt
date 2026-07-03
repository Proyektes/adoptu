package com.adoptu.dto.input


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

data class SterilizationLocationSearchParams(
    val country: String? = null,
    val state: String? = null,
    val city: String? = null
)

data class SterilizationLocationsByLocation(
    val country: String,
    val states: List<SterilizationLocationsByState>
)

data class SterilizationLocationsByState(
    val state: String?,
    val cities: List<SterilizationLocationsByCity>
)

data class SterilizationLocationsByCity(
    val city: String,
    val locations: List<SterilizationLocationDto>
)

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
    val website: String? = null,
    val description: String? = null,
    val createdAt: Long
)

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

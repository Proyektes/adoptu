package com.adoptu.services

import com.adoptu.dto.input.CreateUserSterilizationLocationRequest
import com.adoptu.dto.input.UpdateUserSterilizationLocationRequest
import com.adoptu.dto.input.UserSterilizationLocationDto
import com.adoptu.ports.UserSterilizationLocationRepositoryPort

class UserSterilizationLocationService(
    private val repository: UserSterilizationLocationRepositoryPort,
    private val profileEmailVerificationService: ProfileEmailVerificationService
) {

    suspend fun getByUserId(userId: Int): UserSterilizationLocationDto? = repository.getByUserId(userId)

    suspend fun create(userId: Int, accountEmail: String, displayName: String, request: CreateUserSterilizationLocationRequest): UserSterilizationLocationDto {
        require(request.name.isNotBlank()) { "Name is required" }
        require(request.country.isNotBlank()) { "Country is required" }
        require(request.city.isNotBlank()) { "City is required" }
        require(request.address.isNotBlank()) { "Address is required" }
        if (repository.getByUserId(userId) != null) {
            val updateRequest = UpdateUserSterilizationLocationRequest(
                name = request.name, country = request.country, state = request.state,
                city = request.city, neighborhood = request.neighborhood, address = request.address,
                zip = request.zip, phone = request.phone, email = request.email,
                website = request.website, description = request.description
            )
            return when (val result = update(userId, accountEmail, displayName, updateRequest)) {
                is ServiceResult.Success -> result.data
                is ServiceResult.Error -> throw IllegalArgumentException(result.message)
                else -> throw Exception("Failed to update sterilization location")
            }
        }
        val emailVerified = profileEmailVerificationService.handleProfileEmail(
            userId, VerifiableProfileType.STERILIZATION, accountEmail, request.email, displayName
        )
        return repository.create(userId, request, emailVerified)
    }

    suspend fun update(userId: Int, accountEmail: String, displayName: String, request: UpdateUserSterilizationLocationRequest): ServiceResult<UserSterilizationLocationDto> {
        val existing = repository.getByUserId(userId) ?: return ServiceResult.NotFound
        val emailVerifiedOverride = if (request.email != null && request.email != existing.email) {
            try {
                profileEmailVerificationService.handleProfileEmail(
                    userId, VerifiableProfileType.STERILIZATION, accountEmail, request.email, displayName
                )
            } catch (e: IllegalArgumentException) {
                return ServiceResult.Error(e.message ?: "Invalid email")
            }
        } else {
            null
        }
        val updated = repository.update(userId, request, emailVerifiedOverride)
        return if (updated != null) ServiceResult.Success(updated) else ServiceResult.NotFound
    }

    suspend fun delete(userId: Int): ServiceResult<Unit> {
        val existing = repository.getByUserId(userId) ?: return ServiceResult.NotFound
        val deleted = repository.delete(userId)
        return if (deleted) ServiceResult.Success(Unit) else ServiceResult.NotFound
    }

    suspend fun search(country: String, state: String? = null, city: String? = null, neighborhood: String? = null, zip: String? = null): List<UserSterilizationLocationDto> {
        require(country.isNotBlank()) { "Country is required" }
        return repository.search(country, state, city, neighborhood, zip)
    }
}
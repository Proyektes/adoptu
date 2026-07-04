package com.adoptu.ports

import com.adoptu.dto.input.CreateUserSterilizationLocationRequest
import com.adoptu.dto.input.UpdateUserSterilizationLocationRequest
import com.adoptu.dto.input.UserSterilizationLocationDto

interface UserSterilizationLocationRepositoryPort {
    suspend fun getByUserId(userId: Int): UserSterilizationLocationDto?
    suspend fun create(userId: Int, request: CreateUserSterilizationLocationRequest, emailVerified: Boolean): UserSterilizationLocationDto
    suspend fun update(userId: Int, request: UpdateUserSterilizationLocationRequest, emailVerifiedOverride: Boolean?): UserSterilizationLocationDto?
    suspend fun delete(userId: Int): Boolean
    suspend fun search(country: String, state: String?, city: String?, neighborhood: String?, zip: String?): List<UserSterilizationLocationDto>
}
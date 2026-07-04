package com.adoptu.ports

import com.adoptu.dto.input.CreateUserShelterRequest
import com.adoptu.dto.input.UpdateUserShelterRequest
import com.adoptu.dto.input.UserShelterDto

interface UserShelterRepositoryPort {
    suspend fun getByUserId(userId: Int): UserShelterDto?
    suspend fun create(userId: Int, request: CreateUserShelterRequest, emailVerified: Boolean): UserShelterDto
    suspend fun update(userId: Int, request: UpdateUserShelterRequest, emailVerifiedOverride: Boolean?): UserShelterDto?
    suspend fun delete(userId: Int): Boolean
    suspend fun search(country: String, state: String?, city: String?, neighborhood: String?, zip: String?): List<UserShelterDto>
}
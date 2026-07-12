package com.adoptu.ports

import com.adoptu.dto.input.CreateShelterRequest
import com.adoptu.dto.input.ShelterDto
import com.adoptu.dto.input.UpdateShelterRequest

interface ShelterRepositoryPort {
    suspend fun getById(id: Int): ShelterDto?
    suspend fun getAll(country: String, state: String?, city: String?, neighborhood: String?, zip: String?): List<ShelterDto>
    suspend fun create(request: CreateShelterRequest): ShelterDto
    suspend fun update(id: Int, request: UpdateShelterRequest): ShelterDto?
    suspend fun delete(id: Int): Boolean
    suspend fun getCountries(): List<String>
    suspend fun getStatesByCountry(country: String): List<String>
}

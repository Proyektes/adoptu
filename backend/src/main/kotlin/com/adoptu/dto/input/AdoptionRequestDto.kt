package com.adoptu.dto.input


data class AdoptionRequestDto(
    val id: Int,
    val petId: Int,
    val adopterId: Int,
    val message: String,
    val status: String,
    val createdAt: Long
)

data class CreateAdoptionRequestRequest(
    val message: String = ""
)
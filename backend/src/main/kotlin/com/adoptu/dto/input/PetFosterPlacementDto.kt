package com.adoptu.dto.input

import com.universaliun.formats.json.JsonDecodable
import com.universaliun.formats.json.JsonEncodable

@JsonDecodable(strict = false)
@JsonEncodable
data class PetFosterPlacementDto(
    val id: Int,
    val petId: Int,
    val petName: String? = null,
    val temporalHomeId: Int,
    val temporalHomeAlias: String? = null,
    val startDate: Long,
    val endDate: Long? = null,
    val notes: String? = null,
    val createdAt: Long
)

@JsonDecodable(strict = false)
@JsonEncodable
data class CreateFosterPlacementRequest(
    val temporalHomeId: Int,
    val notes: String? = null
)

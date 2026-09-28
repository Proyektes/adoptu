package com.adoptu.dto.input

import com.universaliun.formats.json.JsonDecodable
import com.universaliun.formats.json.JsonEncodable

@JsonDecodable(strict = false)
@JsonEncodable
data class PetAnalyticsDto(
    val petId: Int,
    val viewCount: Long,
    val inquiryCount: Int,
    val approvedCount: Int,
    // inquiryCount / viewCount - null (not zero) when there's no view data yet, so the frontend
    // can distinguish "0% conversion" from "not enough data".
    val conversionRate: Double? = null
)

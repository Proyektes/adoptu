package com.adoptu.dto.output

data class PagedResult<T>(
    val items: List<T>,
    val total: Int,
    val page: Int,
    val pageSize: Int
)

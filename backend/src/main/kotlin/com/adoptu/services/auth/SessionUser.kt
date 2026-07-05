package com.adoptu.services.auth


data class SessionUser(
    val userId: Int,
    val email: String,
    val displayName: String
)

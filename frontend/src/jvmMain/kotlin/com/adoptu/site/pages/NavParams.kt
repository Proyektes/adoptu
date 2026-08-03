package com.adoptu.site.pages

data class NavParams(
    val isLoggedIn: Boolean = false,
    val isAdmin: Boolean = false,
    val isRescuerOrAdmin: Boolean = false,
    val isTemporalHomeOrAdmin: Boolean = false
)

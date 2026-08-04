package com.adoptu.ports

interface SmsNotificationPort {
    suspend fun sendUrgentRescueAlert(
        phone: String,
        description: String,
        dangerType: String,
        locationLabel: String,
        acceptLink: String
    ): Boolean
}

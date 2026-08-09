package com.adoptu.mocks

import com.adoptu.ports.SmsNotificationPort

class FakeSmsNotificationPort : SmsNotificationPort {
    data class SentAlert(val phone: String, val description: String, val dangerType: String, val locationLabel: String, val acceptLink: String)

    private val sent = mutableListOf<SentAlert>()

    override suspend fun sendUrgentRescueAlert(
        phone: String,
        description: String,
        dangerType: String,
        locationLabel: String,
        acceptLink: String
    ): Boolean {
        sent.add(SentAlert(phone, description, dangerType, locationLabel, acceptLink))
        return true
    }

    fun getSent(): List<SentAlert> = sent.toList()
}

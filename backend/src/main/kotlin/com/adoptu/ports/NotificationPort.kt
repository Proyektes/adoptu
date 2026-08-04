package com.adoptu.ports

interface NotificationPort {
    suspend fun sendEmail(to: String, subject: String, body: String, userId: Int? = null): Boolean
    suspend fun sendPhotographerRequest(
        photographerEmail: String,
        photographerName: String,
        requesterName: String,
        petName: String?,
        message: String,
        fee: Double?,
        currency: String?
    ): Boolean
    suspend fun sendAdoptionRequestNotification(
        rescuerEmail: String,
        petName: String,
        adopterName: String,
        message: String?
    ): Boolean
    suspend fun sendTemporalHomeRequest(
        temporalHomeEmail: String,
        temporalHomeAlias: String,
        rescuerName: String,
        petName: String?,
        message: String,
        spamReportLink: String
    ): Boolean
    suspend fun sendUrgentRescueAlert(
        rescuerEmail: String,
        rescuerName: String,
        description: String,
        dangerType: String,
        locationLabel: String,
        acceptLink: String
    ): Boolean
}

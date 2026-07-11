package com.adoptu.adapters.notification

import com.adoptu.config.AppConfig
import com.adoptu.ports.NotificationPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.mail.DefaultAuthenticator
import org.apache.commons.mail.SimpleEmail
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ses.SesClient
import software.amazon.awssdk.services.ses.model.*
import java.net.URI

private val logger = LoggerFactory.getLogger("SesEmailAdapter")

class SesEmailAdapter(config: AppConfig) : NotificationPort {

    private val env = config.propertyOrNull("env")?.getString() ?: "prod"
    private val isDev = env.lowercase() == "dev"

    private val emailPrefix = if (isDev) "email.dev" else "email.prod"

    private val smtpHost = config.propertyOrNull("$emailPrefix.host")?.getString()
    private val smtpPort = config.propertyOrNull("$emailPrefix.port")?.getString()?.toIntOrNull()
    private val smtpUsername = config.propertyOrNull("$emailPrefix.username")?.getString() ?: ""
    private val smtpPassword = config.propertyOrNull("$emailPrefix.password")?.getString() ?: ""
    private val smtpStarttls = config.propertyOrNull("$emailPrefix.starttls")?.getString()?.toBoolean() ?: !isDev
    private val emailFrom = config.propertyOrNull("$emailPrefix.from")?.getString() ?: "noreply@adopt-u.com"

    private val sesRegion = config.propertyOrNull("ses.region")?.getString() ?: "us-east-1"
    private val sesEndpoint = config.propertyOrNull("ses.endpoint")?.getString()

    @Suppress("DEPRECATION")
    private val sesClient: SesClient? = try {
        val builder = SesClient.builder().region(Region.of(sesRegion))

        if (!isDev) {
            builder.credentialsProvider(DefaultCredentialsProvider.create())
        }

        if (!sesEndpoint.isNullOrBlank()) {
            builder.endpointOverride(URI.create(sesEndpoint))
        }

        builder.build()
    } catch (e: Exception) {
        logger.error("Failed to create SES client: ${e.message}")
        null
    }

    private val isSesConfigured: Boolean
        get() = sesClient != null && !isDev

    private val isSmtpConfigured: Boolean
        get() = !smtpHost.isNullOrBlank() && smtpPort != null

    private val hasSmtpCredentials: Boolean
        get() = smtpUsername.isNotEmpty() && smtpPassword.isNotEmpty()

    private val isConfigured: Boolean
        get() = if (isDev) isSmtpConfigured else isSesConfigured

    override suspend fun sendEmail(to: String, subject: String, body: String, userId: Int?): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            if (isDev) {
                logger.info("SMTP not configured - would have sent email to $to: $subject")
            } else {
                logger.info("SES not configured - would have sent email to $to: $subject")
            }
            return@withContext false
        }

        try {
            val success = if (isDev) {
                sendEmailViaSmtp(to, subject, body)
            } else {
                sendEmailViaSes(to, subject, body)
            }
            success
        } catch (e: Exception) {
            logger.error("Failed to send email to $to: ${e.message}")
            false
        }
    }

    private fun sendEmailViaSmtp(to: String, subject: String, body: String): Boolean {
        return try {
            val email = SimpleEmail()
            email.hostName = smtpHost
            email.setSmtpPort(smtpPort!!)
            if (hasSmtpCredentials) {
                email.setAuthenticator(DefaultAuthenticator(smtpUsername, smtpPassword))
            }
            email.isStartTLSEnabled = smtpStarttls
            email.setFrom(emailFrom, "Adopt-U")
            email.addTo(to)
            email.subject = subject
            email.setMsg(body)
            val result = email.send()
            true
        } catch (e: Exception) {
            logger.error("Failed to send SMTP email: ${e.message}", e)
            false
        }
    }

    private fun sendEmailViaSes(to: String, subject: String, body: String): Boolean {
        val destination = Destination.builder().toAddresses(to).build()

        val content = Content.builder().charset("UTF-8").data(subject).build()
        val bodyContent = Content.builder().charset("UTF-8").data(body).build()
        val bodyObj = Body.builder().text(bodyContent).build()
        val message = Message.builder().subject(content).body(bodyObj).build()

        val request = SendEmailRequest.builder()
            .destination(destination)
            .message(message)
            .source(emailFrom)
            .build()

        val client = sesClient ?: throw IllegalStateException("SES client is not initialized")
        val response = client.sendEmail(request)
        return response.messageId() != null
    }

    override suspend fun sendPhotographerRequest(
        photographerEmail: String,
        photographerName: String,
        requesterName: String,
        petName: String?,
        message: String,
        fee: Double?,
        currency: String?
    ): Boolean {
        val subject = "New Photography Session Request - Adopt-U"
        val body = buildString {
            appendLine("Hello $photographerName,")
            appendLine()
            appendLine("You have received a new photography session request!")
            appendLine()
            appendLine("Request from: $requesterName")
            if (petName != null) {
                appendLine("Pet: $petName")
            }
            if (fee != null && fee > 0) {
                appendLine("Offered fee: $currency $fee")
            }
            appendLine()
            appendLine("Message:")
            appendLine(message)
            appendLine()
            appendLine("Log in to your account to respond to this request.")
            appendLine()
            appendLine("Best regards,")
            appendLine("The Adopt-U Team")
        }
        return sendEmail(photographerEmail, subject, body)
    }

    override suspend fun sendAdoptionRequestNotification(
        rescuerEmail: String,
        petName: String,
        adopterName: String,
        message: String?
    ): Boolean {
        val subject = "New Adoption Request for $petName"
        val body = buildString {
            appendLine("Hello,")
            appendLine()
            appendLine("You have received a new adoption request for $petName.")
            appendLine()
            appendLine("Adopter: $adopterName")
            if (!message.isNullOrBlank()) {
                appendLine()
                appendLine("Message:")
                appendLine(message)
            }
            appendLine()
            appendLine("Log in to your account to review the request.")
            appendLine()
            appendLine("Best regards,")
            appendLine("The Adopt-U Team")
        }
        return sendEmail(rescuerEmail, subject, body)
    }

    override suspend fun sendTemporalHomeRequest(
        temporalHomeEmail: String,
        temporalHomeAlias: String,
        rescuerName: String,
        petName: String?,
        message: String,
        spamReportLink: String
    ): Boolean {
        val subject = "New Pet Care Help Request - Adopt-U"
        val body = buildString {
            appendLine("Hello $temporalHomeAlias,")
            appendLine()
            appendLine("You have received a new pet care help request!")
            appendLine()
            appendLine("Request from: $rescuerName")
            if (petName != null) {
                appendLine("Pet: $petName")
            }
            appendLine()
            appendLine("Message:")
            appendLine(message)
            appendLine()
            appendLine("---")
            appendLine("If you want to block this rescuer from sending you more requests, click here:")
            appendLine(spamReportLink)
            appendLine()
            appendLine("Best regards,")
            appendLine("The Adopt-U Team")
        }
        return sendEmail(temporalHomeEmail, subject, body)
    }
}

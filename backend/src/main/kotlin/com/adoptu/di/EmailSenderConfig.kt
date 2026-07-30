package com.adoptu.di

import com.adoptu.adapters.aws.EcsTaskCredentialsProvider
import com.adoptu.adapters.aws.ecsTaskCredentialsAvailable
import com.adoptu.config.AppConfig
import com.universaliun.email.backend.adapter.out.email.SesEmailAdapter
import com.universaliun.email.backend.adapter.out.email.SesEmailConfig
import com.universaliun.email.backend.adapter.out.email.SmtpEmailAdapter
import com.universaliun.email.backend.adapter.out.email.SmtpEmailConfig
import com.universaliun.email.common.EmailSenderPort
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ses.SesClient
import java.net.URI

private val logger = LoggerFactory.getLogger("EmailSenderConfig")

/** Inert fallback matching the previous adapter's "not configured" behavior (log and report
 *  success=false-shaped, i.e. just don't throw) for when [SesClient] construction itself fails
 *  (e.g. a malformed `ses.endpoint`) -- the app should still boot in that case, not crash at
 *  startup, same as before this migration. */
private class NoOpEmailSenderPort(private val reason: String) : EmailSenderPort {
    override fun send(to: List<String>, subject: String, body: String, cc: List<String>, bcc: List<String>, isHtml: Boolean) {
        logger.warn("Email not sent to {} ({}): {}", to, reason, subject)
    }
}

/**
 * Builds EmailKit's [EmailSenderPort] from this app's own `env`/`email.*`/`ses.*` config keys --
 * same env-key names the previous hand-rolled adapter read, so no config/deployment change is
 * needed. `dev` routes through [SmtpEmailAdapter] (a local catcher like Mailpit); anything else
 * routes through [SesEmailAdapter] (real AWS SES, same ECS-task-credentials-first fallback the
 * previous adapter used). [SesClient] construction is wrapped the same way the previous adapter
 * wrapped it -- a malformed `ses.endpoint` degrades to [NoOpEmailSenderPort] instead of failing
 * app startup.
 */
fun emailSenderPortFromConfig(config: AppConfig): EmailSenderPort {
    val env = config.propertyOrNull("env")?.getString() ?: "prod"
    val isDev = env.lowercase() == "dev"
    val emailPrefix = if (isDev) "email.dev" else "email.prod"
    val fromEmail = config.propertyOrNull("$emailPrefix.from")?.getString() ?: "noreply@adopt-u.com"

    if (isDev) {
        return SmtpEmailAdapter(
            SmtpEmailConfig(
                host = config.propertyOrNull("$emailPrefix.host")?.getString() ?: "localhost",
                port = config.propertyOrNull("$emailPrefix.port")?.getString()?.toIntOrNull() ?: 1025,
                fromEmail = fromEmail,
            )
        )
    }

    val sesRegion = config.propertyOrNull("ses.region")?.getString() ?: "us-east-1"
    val sesEndpoint = config.propertyOrNull("ses.endpoint")?.getString()
    val sesClient = try {
        val builder = SesClient.builder()
            .region(Region.of(sesRegion))
            .credentialsProvider(if (ecsTaskCredentialsAvailable()) EcsTaskCredentialsProvider() else DefaultCredentialsProvider.create())
        if (!sesEndpoint.isNullOrBlank()) {
            builder.endpointOverride(URI.create(sesEndpoint))
        }
        builder.build()
    } catch (e: Exception) {
        logger.error("Failed to create SES client: {}", e.message)
        null
    } ?: return NoOpEmailSenderPort("SES client construction failed")

    return SesEmailAdapter(
        ses = sesClient,
        config = SesEmailConfig(
            region = sesRegion,
            fromEmail = fromEmail,
            configurationSetName = config.propertyOrNull("ses.configurationSetName")?.getString(),
        ),
    )
}

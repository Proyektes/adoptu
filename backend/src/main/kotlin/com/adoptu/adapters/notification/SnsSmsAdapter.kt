package com.adoptu.adapters.notification

import com.adoptu.adapters.aws.EcsTaskCredentialsProvider
import com.adoptu.adapters.aws.ecsTaskCredentialsAvailable
import com.adoptu.ports.SmsNotificationPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.PublishRequest

private val logger = LoggerFactory.getLogger("SnsSmsAdapter")

/**
 * AWS SNS direct-to-phone SMS (Publish with a phoneNumber destination, not a topic ARN) - unlike
 * Twilio, SNS needs no "from number" configured here; AWS manages origination per account/region.
 * Credential chain mirrors [com.adoptu.adapters.storage.S3ImageStorageAdapter]: explicit static
 * keys for local dev/LocalStack, else the ECS task role in prod.
 */
class SnsSmsAdapter(
    private val region: String,
    private val accessKeyId: String?,
    private val secretAccessKey: String?,
    private val endpoint: String?
) : SmsNotificationPort {
    private val snsClient: SnsClient by lazy {
        @Suppress("DEPRECATION")
        val builder = SnsClient.builder()
            .region(Region.of(region))

        if (!accessKeyId.isNullOrEmpty() && !secretAccessKey.isNullOrEmpty()) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))
            )
        } else {
            @Suppress("DEPRECATION")
            builder.credentialsProvider(
                if (ecsTaskCredentialsAvailable()) EcsTaskCredentialsProvider() else DefaultCredentialsProvider.create()
            )
        }

        if (!endpoint.isNullOrEmpty()) {
            builder.endpointOverride(java.net.URI.create(endpoint))
        }

        builder.build()
    }

    override suspend fun sendUrgentRescueAlert(
        phone: String,
        description: String,
        dangerType: String,
        locationLabel: String,
        acceptLink: String
    ): Boolean = withContext(Dispatchers.IO) {
        val body = "URGENT rescue needed near $locationLabel ($dangerType). $description Accept: $acceptLink"
            .take(1500) // stay well under SNS's per-message segment limits

        val request = PublishRequest.builder()
            .phoneNumber(phone)
            .message(body)
            .build()

        try {
            snsClient.publish(request)
            true
        } catch (e: Exception) {
            logger.error("SNS SMS to $phone failed", e)
            false
        }
    }
}

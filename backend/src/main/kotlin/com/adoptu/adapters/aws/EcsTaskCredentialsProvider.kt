package com.adoptu.adapters.aws

import com.adoptu.web.JsonSupport
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

private val logger = LoggerFactory.getLogger("EcsTaskCredentialsProvider")

data class EcsCredentialsPayload(
    val AccessKeyId: String,
    val SecretAccessKey: String,
    val Token: String,
    val Expiration: String
)

/**
 * AWS SDK v2's own ContainerCredentialsProvider needs GraalVM native-image reflection/resource
 * metadata that neither this project nor the community reachability-metadata repo currently
 * provides for the SDK version in use (auth module coverage tops out with no ContainerCredentials-
 * Provider entry at all). This fetches the same ECS task-role credentials over plain HTTP and
 * parses them with the app's existing Jackson setup - both already proven to work in the native
 * binary - instead of relying on the SDK's own reflective credential-loading machinery.
 */
class EcsTaskCredentialsProvider : AwsCredentialsProvider {
    private val relativeUri = System.getenv("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI")
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    @Volatile
    private var cached: AwsCredentials? = null

    @Volatile
    private var expiresAt: Instant = Instant.EPOCH

    override fun resolveCredentials(): AwsCredentials {
        cached?.let { if (Instant.now().isBefore(expiresAt.minus(5, ChronoUnit.MINUTES))) return it }
        synchronized(this) {
            cached?.let { if (Instant.now().isBefore(expiresAt.minus(5, ChronoUnit.MINUTES))) return it }

            val uri = requireNotNull(relativeUri) {
                "AWS_CONTAINER_CREDENTIALS_RELATIVE_URI is not set - not running under an ECS task role"
            }
            val request = HttpRequest.newBuilder(URI.create("http://169.254.170.2$uri"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() == 200) {
                "ECS task credentials endpoint returned HTTP ${response.statusCode()}"
            }

            val payload = JsonSupport.objectMapper.readValue(response.body(), EcsCredentialsPayload::class.java)
            val awsCredentials = AwsSessionCredentials.create(payload.AccessKeyId, payload.SecretAccessKey, payload.Token)
            val expiration = Instant.parse(payload.Expiration)

            cached = awsCredentials
            expiresAt = expiration
            logger.info("Refreshed ECS task credentials, expiring at {}", expiration)

            return awsCredentials
        }
    }
}

/** True when running under an ECS task role (Fargate/EC2 launch type with a task role attached). */
fun ecsTaskCredentialsAvailable(): Boolean =
    !System.getenv("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI").isNullOrBlank()

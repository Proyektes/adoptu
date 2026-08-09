package com.adoptu.adapters.notification

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertTrue

/**
 * Testcontainers-based coverage for [SnsSmsAdapter] - mirrors the LocalStack container wiring
 * pattern used by ApplicationTestcontainersIT.kt (routes package), but targets SNS instead of S3
 * and constructs the adapter directly rather than the whole app/server.
 */
@Testcontainers
class SnsSmsAdapterTest {

    companion object {
        @Container
        val localstackContainer: LocalStackContainer = LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(LocalStackContainer.Service.SNS)
    }

    private fun adapter(endpoint: String? = null): SnsSmsAdapter = SnsSmsAdapter(
        region = localstackContainer.region,
        accessKeyId = localstackContainer.accessKey,
        secretAccessKey = localstackContainer.secretKey,
        endpoint = endpoint ?: localstackContainer.getEndpointOverride(LocalStackContainer.Service.SNS).toString()
    )

    @Test
    fun `sendUrgentRescueAlert publishes directly to a phone number via localstack and returns true`() = runBlocking {
        val result = adapter().sendUrgentRescueAlert(
            phone = "+15555550100",
            description = "Injured dog near the highway overpass, needs immediate help",
            dangerType = "traffic",
            locationLabel = "Route 9 overpass",
            acceptLink = "https://adopt-u.com/rescue/accept/abc123"
        )

        assertTrue(result, "Publish to a phone number should succeed against localstack's fake SNS with no topic/subscription setup")
    }

    @Test
    fun `sendUrgentRescueAlert truncates a very long description without throwing`() = runBlocking {
        val longDescription = "x".repeat(5000)

        val result = adapter().sendUrgentRescueAlert(
            phone = "+15555550101",
            description = longDescription,
            dangerType = "flood",
            locationLabel = "Riverside Park",
            acceptLink = "https://adopt-u.com/rescue/accept/def456"
        )

        assertTrue(result, "A description well over the 1500-char take() limit should still be published successfully")
    }

    @Test
    fun `sendUrgentRescueAlert returns false and logs when the SNS endpoint is unreachable`() = runBlocking {
        // Port with nothing listening -> SnsClient.publish throws, exercising the catch branch
        // (and the file-level logger.error call) deterministically instead of relying on localstack.
        val result = adapter(endpoint = "http://localhost:1").sendUrgentRescueAlert(
            phone = "+15555550102",
            description = "Cat stuck in a storm drain",
            dangerType = "trapped",
            locationLabel = "Elm Street",
            acceptLink = "https://adopt-u.com/rescue/accept/ghi789"
        )

        assertTrue(!result, "An unreachable SNS endpoint should be caught and reported as a failed send")
    }
}

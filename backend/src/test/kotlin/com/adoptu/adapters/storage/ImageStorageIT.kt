package com.adoptu.adapters.storage

import com.universaliun.storagekit.backend.adapter.out.storage.ReturnFormat
import com.universaliun.storagekit.backend.adapter.out.storage.S3ObjectStorageAdapter
import com.universaliun.storagekit.backend.adapter.out.storage.S3StorageConfig as StorageKitS3Config
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.io.ByteArrayInputStream
import kotlin.test.assertTrue

/**
 * Integration test for [AdoptuImageStorageAdapter] (bridging onto StorageKit's real
 * [S3ObjectStorageAdapter]) backed by a real LocalStack S3 service. Same setup pattern used by
 * [com.adoptu.routes.ApplicationTestcontainersIT].
 */
@Testcontainers
class ImageStorageIT {

    companion object {
        @Container
        val localstackContainer: LocalStackContainer =
            LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
                .withServices(LocalStackContainer.Service.S3)
                .withEnv("EAGER_SERVICE_LOADING", "1")
    }

    private fun newAdapter(bucketName: String): AdoptuImageStorageAdapter {
        val endpoint = localstackContainer.getEndpointOverride(LocalStackContainer.Service.S3).toString()
        val s3Client = S3Client.builder()
            .region(Region.of("us-east-1"))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(localstackContainer.accessKey, localstackContainer.secretKey)
                )
            )
            .endpointOverride(java.net.URI.create(endpoint))
            .forcePathStyle(true)
            .build()
        val storage = S3ObjectStorageAdapter(
            s3Client,
            StorageKitS3Config(sseEnabled = false, autoCreateBucket = true, returnFormat = ReturnFormat.PublicUrl(urlBase = endpoint)),
        )
        return AdoptuImageStorageAdapter(storage, bucketName, "us-east-1", endpoint, publicUrl = null, pathStyleAccess = true)
    }

    @Test
    fun `uploadImage auto-creates the bucket and returns a usable url`() = runBlocking {
        // The bucket is intentionally NOT pre-created: uploadImage must catch the
        // NoSuchBucketException thrown by LocalStack, call createBucket(), and retry the
        // upload against the freshly created bucket.
        val adapter = newAdapter("image-storage-it-upload-bucket")
        val content = "hello world".toByteArray()

        val url = adapter.uploadImage(
            petId = 1,
            imageName = "photo.jpg",
            contentType = "image/jpeg",
            inputStream = ByteArrayInputStream(content)
        )

        assertTrue(url.contains("image-storage-it-upload-bucket"), "URL should reference the bucket: $url")
        assertTrue(url.contains("pets/1/"), "URL should contain the pet-scoped key prefix: $url")
        assertTrue(url.endsWith("photo.jpg"), "URL should end with the original image name: $url")
    }

    @Test
    fun `deleteImage removes a previously uploaded object`() = runBlocking {
        val adapter = newAdapter("image-storage-it-delete-bucket")
        val content = "to be deleted".toByteArray()

        val url = adapter.uploadImage(
            petId = 2,
            imageName = "delete-me.jpg",
            contentType = "image/jpeg",
            inputStream = ByteArrayInputStream(content)
        )

        val deleted = adapter.deleteImage(url)

        assertTrue(deleted)
    }
}

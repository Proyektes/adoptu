package com.adoptu.adapters.storage

import com.adoptu.adapters.aws.EcsTaskCredentialsProvider
import com.adoptu.adapters.aws.ecsTaskCredentialsAvailable
import com.adoptu.ports.ImageStoragePort
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.*

class S3ImageStorageAdapter(
    private val bucketName: String,
    private val region: String,
    private val accessKeyId: String?,
    private val secretAccessKey: String?,
    private val endpoint: String?,
    private val pathStyleAccess: Boolean = false,
    // Public-facing URL clients actually load images from (a CDN domain). Distinct from
    // [endpoint], which the S3 SDK client itself uses to reach the bucket - the bucket blocks
    // direct public access, so [endpoint]'s host is never valid to hand back to a browser.
    private val publicUrl: String? = null
) : ImageStoragePort {
    private val log = LoggerFactory.getLogger(S3ImageStorageAdapter::class.java)

    private val s3Client: S3Client by lazy {
        @Suppress("DEPRECATION")
        val builder = S3Client.builder()
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

        builder.forcePathStyle(pathStyleAccess).build()
    }

    override suspend fun uploadImage(
        petId: Int,
        imageName: String,
        contentType: String,
        inputStream: InputStream
    ): String {
        val key = "pets/$petId/${UUID.randomUUID()}-$imageName"

        val request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType(contentType)
            .build()

        return uploadImage(request, inputStream, contentType, petId, key)
    }

    private fun uploadImage(
        request: PutObjectRequest?,
        inputStream: InputStream,
        contentType: String,
        petId: Int,
        key: String,
    ): String {
        val bytes = inputStream.readAllBytes()
        try {
            s3Client.putObject(request, RequestBody.fromBytes(bytes))
        } catch (e: NoSuchBucketException) {
            log.info("Creating bucket: {}", bucketName)
            createBucket()
            return uploadImage(request, ByteArrayInputStream(bytes), contentType, petId, key)
        } catch (e: Exception) {
            log.error("Error uploading storage to S3: {}", e.message, e)
            throw RuntimeException("Failed to upload storage: ${e.message}", e)
        }

        return getImageUrl(petId, key)
    }

    private fun createBucket() {
        val createBucketRequest = CreateBucketRequest.builder()
            .bucket(bucketName)
            .build()
        try {
            s3Client.createBucket(createBucketRequest)
        } catch (e: Exception) {
            log.error("Error creating S3 bucket: {}", e.message, e)
            throw RuntimeException("Failed to create S3 bucket: ${e.message}", e)
        }
    }

    override suspend fun deleteImage(imageUrl: String): Boolean {
        return try {
            val key = extractKeyFromUrl(imageUrl)
            val request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()
            s3Client.deleteObject(request)
            true
        } catch (e: Exception) {
            log.error("Error deleting storage from S3: {}", e.message, e)
            throw RuntimeException("Failed to delete storage: ${e.message}", e)
        }
    }

    override fun getImageUrl(petId: Int, imageKey: String): String {
        return when {
            !publicUrl.isNullOrEmpty() -> "$publicUrl/$imageKey"
            !endpoint.isNullOrEmpty() -> "$endpoint/$bucketName/$imageKey"
            else -> "https://$bucketName.s3.$region.amazonaws.com/$imageKey"
        }
    }

    // The key is always the URL path with the leading slash stripped, except for path-style
    // addressing (LocalStack in dev), where the bucket name is also part of the path and must
    // be stripped too - virtual-hosted-style and CDN URLs both put the bucket in the host (or
    // omit it entirely), never the path.
    private fun extractKeyFromUrl(url: String): String {
        val path = java.net.URI.create(url).path.removePrefix("/")
        return if (pathStyleAccess) path.substringAfter("$bucketName/") else path
    }
}

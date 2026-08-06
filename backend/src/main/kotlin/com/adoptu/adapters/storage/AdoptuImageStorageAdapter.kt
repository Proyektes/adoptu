package com.adoptu.adapters.storage

import com.adoptu.ports.ImageStoragePort
import com.universaliun.storagekit.common.ObjectStoragePort
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.UUID

/**
 * Bridges Adopt-U's own [ImageStoragePort] onto StorageKit's [ObjectStoragePort]. Two real
 * mismatches meant this couldn't be a straight swap onto [ObjectStoragePort.upload]:
 *
 * 1. [uploadImage]'s key is `"pets/$petId/${UUID}-$imageName"` — the original image name (and
 *    therefore its extension) is preserved in the key. `ObjectStoragePort.upload` generates a key
 *    from the content type's extension instead, discarding the original name entirely (proven by
 *    `ImageStorageIT`'s own assertion that the returned URL ends with the original filename) --
 *    so this uses [ObjectStoragePort.uploadAt] with the key computed here, matching the original
 *    [S3ImageStorageAdapter] exactly.
 * 2. [getImageUrl] has two genuinely different URL shapes depending on config: a CDN [publicUrl]
 *    omits the bucket from the path (`"$publicUrl/$imageKey"`), while a local [endpoint] (dev)
 *    includes it (`"$endpoint/$bucket/$imageKey"`). StorageKit's own `ReturnFormat.PublicUrl`
 *    always includes the bucket, so it can only represent one of these two shapes -- this class
 *    ignores whatever URL `uploadAt` returns and formats the URL itself instead, replicating the
 *    original adapter's exact branching.
 *
 * Bucket auto-creation, static-credentials-vs-default-chain resolution, and SSE are all
 * StorageKit's own config concerns now (see `AppModule.createImageStorageAdapter`); the S3Client
 * itself is still built by the host, not `createS3Client()` -- Adopt-U's own
 * `EcsTaskCredentialsProvider` (a GraalVM-native-image-safe ECS credential fetch StorageKit
 * doesn't know about) needs to keep being used for it.
 */
class AdoptuImageStorageAdapter(
    private val storage: ObjectStoragePort,
    private val bucket: String,
    private val region: String,
    private val endpoint: String?,
    private val publicUrl: String?,
    private val pathStyleAccess: Boolean,
) : ImageStoragePort {

    private val log = LoggerFactory.getLogger(AdoptuImageStorageAdapter::class.java)

    override suspend fun uploadImage(
        petId: Int,
        imageName: String,
        contentType: String,
        inputStream: java.io.InputStream,
    ): String {
        val key = "pets/$petId/${UUID.randomUUID()}-$imageName"
        val bytes = inputStream.readAllBytes()
        try {
            storage.uploadAt(bucket, key, bytes, contentType)
        } catch (e: Exception) {
            log.error("Error uploading storage to S3: {}", e.message, e)
            throw RuntimeException("Failed to upload storage: ${e.message}", e)
        }
        return getImageUrl(petId, key)
    }

    override suspend fun deleteImage(imageUrl: String): Boolean {
        return try {
            storage.delete(bucket, extractKeyFromUrl(imageUrl))
            true
        } catch (e: Exception) {
            log.error("Error deleting storage from S3: {}", e.message, e)
            throw RuntimeException("Failed to delete storage: ${e.message}", e)
        }
    }

    override fun getImageUrl(petId: Int, imageKey: String): String = when {
        !publicUrl.isNullOrEmpty() -> "$publicUrl/$imageKey"
        !endpoint.isNullOrEmpty() -> "$endpoint/$bucket/$imageKey"
        else -> "https://$bucket.s3.$region.amazonaws.com/$imageKey"
    }

    // The key is always the URL path with the leading slash stripped, except for path-style
    // addressing (LocalStack in dev), where the bucket name is also part of the path and must
    // be stripped too - virtual-hosted-style and CDN URLs both put the bucket in the host (or
    // omit it entirely), never the path.
    private fun extractKeyFromUrl(url: String): String {
        val path = URI.create(url).path.removePrefix("/")
        return if (pathStyleAccess) path.substringAfter("$bucket/") else path
    }
}

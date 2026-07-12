package com.adoptu.adapters.storage

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class S3ImageStorageAdapterTest {

    @Test
    fun `getImageUrl builds default amazonaws url when endpoint is null`() {
        val adapter = S3ImageStorageAdapter("test-bucket", "us-east-1", null, null, null)

        val url = adapter.getImageUrl(1, "pets/1/photo.jpg")

        assertEquals("https://test-bucket.s3.us-east-1.amazonaws.com/pets/1/photo.jpg", url)
    }

    @Test
    fun `getImageUrl builds default amazonaws url when endpoint is blank`() {
        val adapter = S3ImageStorageAdapter("test-bucket", "us-east-1", null, null, "")

        val url = adapter.getImageUrl(1, "pets/1/photo.jpg")

        assertEquals("https://test-bucket.s3.us-east-1.amazonaws.com/pets/1/photo.jpg", url)
    }

    @Test
    fun `getImageUrl uses custom endpoint when configured`() {
        val adapter = S3ImageStorageAdapter("test-bucket", "us-east-1", null, null, "http://localhost:4566", true)

        val url = adapter.getImageUrl(2, "pets/2/photo.jpg")

        assertEquals("http://localhost:4566/test-bucket/pets/2/photo.jpg", url)
    }

    @Test
    fun `getImageUrl prefers publicUrl over endpoint and the default amazonaws url`() {
        val adapter = S3ImageStorageAdapter(
            "test-bucket", "us-east-1", null, null,
            endpoint = "http://localhost:4566",
            publicUrl = "https://dynamic.adopt-u.org"
        )

        val url = adapter.getImageUrl(3, "pets/3/photo.jpg")

        assertEquals("https://dynamic.adopt-u.org/pets/3/photo.jpg", url)
    }

    // uploadImage/deleteImage build and call a lazily-constructed, private S3Client with no
    // injection seam, so they cannot be unit-tested here. They are exercised end-to-end against
    // a real LocalStack S3 service by the Testcontainers-based ImageStorageIT test instead.
}

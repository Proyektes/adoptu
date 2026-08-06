package com.adoptu.adapters.storage

import com.universaliun.storagekit.common.ObjectStoragePort
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AdoptuImageStorageAdapterTest {

    private val storage = mockk<ObjectStoragePort>()

    private fun adapter(
        endpoint: String? = null,
        publicUrl: String? = null,
        pathStyleAccess: Boolean = false,
    ) = AdoptuImageStorageAdapter(storage, "test-bucket", "us-east-1", endpoint, publicUrl, pathStyleAccess)

    @Test
    fun `getImageUrl builds default amazonaws url when endpoint is null`() {
        val url = adapter().getImageUrl(1, "pets/1/photo.jpg")

        assertEquals("https://test-bucket.s3.us-east-1.amazonaws.com/pets/1/photo.jpg", url)
    }

    @Test
    fun `getImageUrl builds default amazonaws url when endpoint is blank`() {
        val url = adapter(endpoint = "").getImageUrl(1, "pets/1/photo.jpg")

        assertEquals("https://test-bucket.s3.us-east-1.amazonaws.com/pets/1/photo.jpg", url)
    }

    @Test
    fun `getImageUrl uses custom endpoint when configured, with the bucket in the path`() {
        val url = adapter(endpoint = "http://localhost:4566", pathStyleAccess = true).getImageUrl(2, "pets/2/photo.jpg")

        assertEquals("http://localhost:4566/test-bucket/pets/2/photo.jpg", url)
    }

    @Test
    fun `getImageUrl prefers publicUrl over endpoint and the default amazonaws url, with no bucket in the path`() {
        val url = adapter(endpoint = "http://localhost:4566", publicUrl = "https://dynamic.adopt-u.org")
            .getImageUrl(3, "pets/3/photo.jpg")

        assertEquals("https://dynamic.adopt-u.org/pets/3/photo.jpg", url)
    }

    @Test
    fun `uploadImage uploads at a key that preserves the original image name, and returns the computed url`() = runBlocking {
        val key = slot<String>()
        every { storage.uploadAt("test-bucket", capture(key), any(), "image/jpeg") } returns "unused"

        val url = adapter().uploadImage(1, "photo.jpg", "image/jpeg", ByteArrayInputStream("hi".toByteArray()))

        assertTrue(key.captured.startsWith("pets/1/"))
        assertTrue(key.captured.endsWith("-photo.jpg"))
        assertEquals("https://test-bucket.s3.us-east-1.amazonaws.com/${key.captured}", url)
    }

    @Test
    fun `uploadImage wraps a storage failure in a RuntimeException`() = runBlocking {
        every { storage.uploadAt(any(), any(), any(), any()) } throws RuntimeException("s3 down")

        val e = assertFailsWith<RuntimeException> {
            adapter().uploadImage(1, "photo.jpg", "image/jpeg", ByteArrayInputStream("hi".toByteArray()))
        }
        assertTrue(e.message!!.contains("Failed to upload storage"))
    }

    @Test
    fun `deleteImage extracts the key from a virtual-hosted-style url and deletes it`() = runBlocking {
        every { storage.delete("test-bucket", "pets/1/abc-photo.jpg") } returns Unit

        val result = adapter().deleteImage("https://test-bucket.s3.us-east-1.amazonaws.com/pets/1/abc-photo.jpg")

        assertTrue(result)
        verify { storage.delete("test-bucket", "pets/1/abc-photo.jpg") }
    }

    @Test
    fun `deleteImage strips the bucket from a path-style url before deleting`() = runBlocking {
        every { storage.delete("test-bucket", "pets/1/abc-photo.jpg") } returns Unit

        val result = adapter(pathStyleAccess = true).deleteImage("http://localhost:4566/test-bucket/pets/1/abc-photo.jpg")

        assertTrue(result)
        verify { storage.delete("test-bucket", "pets/1/abc-photo.jpg") }
    }

    @Test
    fun `deleteImage wraps a storage failure in a RuntimeException`() = runBlocking {
        every { storage.delete(any(), any()) } throws RuntimeException("s3 down")

        val e = assertFailsWith<RuntimeException> {
            adapter().deleteImage("https://test-bucket.s3.us-east-1.amazonaws.com/pets/1/photo.jpg")
        }
        assertTrue(e.message!!.contains("Failed to delete storage"))
    }
}

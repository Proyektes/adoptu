package com.adoptu.services

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.assertThrows
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageCompressorTest {

    @TempDir
    lateinit var tempDir: File

    private fun createTestImage(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until width) {
            for (y in 0 until height) {
                image.setRGB(x, y, (x + y) % 256)
            }
        }
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", outputStream)
        return outputStream.toByteArray()
    }

    @Test
    fun `compress reduces large image dimensions`() {
        val largeImage = createTestImage(2000, 1500)
        val inputStream = ByteArrayInputStream(largeImage)

        val result = ImageCompressor.compress(inputStream, "jpg")

        val compressedImage = ImageIO.read(ByteArrayInputStream(result.toByteArray()))
        assertTrue(compressedImage.width <= 1200)
        assertTrue(compressedImage.height <= 1200)
    }

    @Test
    fun `compress keeps small image dimensions unchanged`() {
        val smallImage = createTestImage(800, 600)
        val inputStream = ByteArrayInputStream(smallImage)

        val result = ImageCompressor.compress(inputStream, "jpg")

        val compressedImage = ImageIO.read(ByteArrayInputStream(result.toByteArray()))
        assertTrue(compressedImage.width <= 800)
        assertTrue(compressedImage.height <= 600)
    }

    @Test
    fun `compress outputs smaller file size`() {
        val largeImage = createTestImage(2000, 1500)
        val inputStream = ByteArrayInputStream(largeImage)

        val result = ImageCompressor.compress(inputStream, "jpg")

        assertTrue(result.size() < largeImage.size)
    }

    @Test
    fun `compress handles png format`() {
        val image = createTestImage(1000, 800)
        val inputStream = ByteArrayInputStream(image)

        val result = ImageCompressor.compress(inputStream, "png")

        val compressedImage = ImageIO.read(ByteArrayInputStream(result.toByteArray()))
        assertTrue(compressedImage.width <= 1200)
    }

    @Test
    fun `compress handles portrait orientation`() {
        val portraitImage = createTestImage(800, 1200)
        val inputStream = ByteArrayInputStream(portraitImage)

        val result = ImageCompressor.compress(inputStream, "jpg")

        val compressedImage = ImageIO.read(ByteArrayInputStream(result.toByteArray()))
        assertTrue(compressedImage.width <= 1200)
        assertTrue(compressedImage.height <= 1200)
    }

    @Test
    fun `compress throws IllegalArgumentException for unreadable image data`() {
        val garbage = "this is definitely not image data".toByteArray()
        val inputStream = ByteArrayInputStream(garbage)

        val exception = assertThrows<IllegalArgumentException> {
            ImageCompressor.compress(inputStream, "jpg")
        }
        assertEquals("Invalid storage data or unsupported format", exception.message)
    }

    @Test
    fun `compress converts non-RGB image type when no resize is needed`() {
        // A small ARGB image needs no resizing (calculateDimensions short-circuits), so this
        // exercises the "image.type != TYPE_INT_RGB" conversion branch instead of the resize
        // branch that the other tests already cover.
        val argbImage = BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until 400) {
            for (y in 0 until 300) {
                argbImage.setRGB(x, y, (0xFF shl 24) or ((x + y) % 256))
            }
        }
        val originalBytes = ByteArrayOutputStream().also { ImageIO.write(argbImage, "png", it) }.toByteArray()
        val inputStream = ByteArrayInputStream(originalBytes)

        val result = ImageCompressor.compress(inputStream, "jpg")

        val compressedImage = ImageIO.read(ByteArrayInputStream(result.toByteArray()))
        assertEquals(400, compressedImage.width)
        assertEquals(300, compressedImage.height)
    }

    @Test
    fun `compress resizes large portrait image using the height-constrained branch`() {
        // width < height and both exceed the max, so calculateDimensions must take the
        // "else" (height-anchored) branch rather than the width-anchored one the landscape
        // test above already covers.
        val tallImage = createTestImage(1400, 2400)
        val inputStream = ByteArrayInputStream(tallImage)

        val result = ImageCompressor.compress(inputStream, "jpg")

        val compressedImage = ImageIO.read(ByteArrayInputStream(result.toByteArray()))
        assertEquals(1200, compressedImage.height)
        assertTrue(compressedImage.width <= 1200)
        assertTrue(compressedImage.width < compressedImage.height)
    }
}

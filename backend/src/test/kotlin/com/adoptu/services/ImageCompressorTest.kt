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

    // Per-channel variation at three different periodicities, not just a single smooth low-frequency
    // ramp - a near-solid-color gradient is already close to maximally compressed by the initial
    // JPEG encode, so recompressing it at an unchanged-or-higher quality can legitimately fail to
    // shrink it further. Real photos have far more local high-frequency detail than a flat gradient,
    // so this fixture needs some too for "compress actually shrinks the file" to be a meaningful,
    // realistic assertion rather than an artifact of degenerate test content.
    private fun createTestImage(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val r = (x * 37 + y * 17) % 256
                val g = (x * 53 + y * 29) % 256
                val b = (x * 11 + y * 41) % 256
                image.setRGB(x, y, (r shl 16) or (g shl 8) or b)
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
        // Must actually be PNG-encoded - format is now content-sniffed rather than trusted from
        // the caller's label, unlike the old ImageIO.read() path which silently auto-detected any
        // format regardless of what was requested.
        val pngImage = BufferedImage(1000, 800, BufferedImage.TYPE_INT_RGB)
        val image = ByteArrayOutputStream().also { ImageIO.write(pngImage, "png", it) }.toByteArray()
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
    fun `compress preserves an ARGB png source when no resize is needed`() {
        // A small ARGB image needs no resizing (calculateDimensions short-circuits), exercising
        // the "no resize needed" branch of the png path with a source that actually has alpha -
        // PNG supports alpha natively, unlike JPEG, so this no longer needs to be forced to RGB.
        val argbImage = BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until 400) {
            for (y in 0 until 300) {
                argbImage.setRGB(x, y, (0xFF shl 24) or ((x + y) % 256))
            }
        }
        val originalBytes = ByteArrayOutputStream().also { ImageIO.write(argbImage, "png", it) }.toByteArray()
        val inputStream = ByteArrayInputStream(originalBytes)

        val result = ImageCompressor.compress(inputStream, "png")

        val compressedImage = ImageIO.read(ByteArrayInputStream(result.toByteArray()))
        assertEquals(400, compressedImage.width)
        assertEquals(300, compressedImage.height)
    }

    @Test
    fun `compress resizes large png image`() {
        val largeImage = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(2000, 1500, BufferedImage.TYPE_INT_RGB), "png", it)
        }.toByteArray()
        val inputStream = ByteArrayInputStream(largeImage)

        val result = ImageCompressor.compress(inputStream, "png")

        val compressedImage = ImageIO.read(ByteArrayInputStream(result.toByteArray()))
        assertTrue(compressedImage.width <= 1080)
        assertTrue(compressedImage.height <= 1080)
    }

    @Test
    fun `compress throws IllegalArgumentException for unreadable png data`() {
        val garbage = "this is definitely not image data".toByteArray()
        val inputStream = ByteArrayInputStream(garbage)

        val exception = assertThrows<IllegalArgumentException> {
            ImageCompressor.compress(inputStream, "png")
        }
        assertEquals("Invalid storage data or unsupported format", exception.message)
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
        assertEquals(1080, compressedImage.height)
        assertTrue(compressedImage.width <= 1080)
        assertTrue(compressedImage.width < compressedImage.height)
    }
}

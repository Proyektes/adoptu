package com.adoptu.services

import com.adoptu.common.image.JPEGDecoder
import com.adoptu.common.image.JPEGEncoder
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.absoluteValue
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Cross-validates the vendored pure-Kotlin JPEG codec (common/.../image/JPEG{Encoder,Decoder}.kt)
// against the JDK's own javax.imageio codec - the closest thing to independent ground truth
// available without a real JPEG reference decoder on the classpath. This is what actually proves
// the port is correct, as opposed to the codec only ever agreeing with itself.
class JPEGCodecCrossValidationTest {

    private fun testImage(width: Int, height: Int): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = (x * 255) / width
                val g = (y * 255) / height
                val b = ((x + y) * 255) / (width + height)
                image.setRGB(x, y, (r shl 16) or (g shl 8) or b)
            }
        }
        return image
    }

    private fun toRgba(image: BufferedImage): ByteArray {
        val rgba = ByteArray(image.width * image.height * 4)
        var i = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                rgba[i++] = ((argb shr 16) and 0xFF).toByte()
                rgba[i++] = ((argb shr 8) and 0xFF).toByte()
                rgba[i++] = (argb and 0xFF).toByte()
                rgba[i++] = 255.toByte()
            }
        }
        return rgba
    }

    private fun averageChannelDiff(a: ByteArray, b: ByteArray): Double {
        var total = 0L
        var samples = 0
        for (i in a.indices) {
            if (i % 4 == 3) continue // skip alpha
            total += ((a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)).absoluteValue
            samples++
        }
        return total.toDouble() / samples
    }

    @Test
    fun `vendored decoder correctly decodes a real ImageIO-encoded JPEG`() {
        val width = 64
        val height = 48
        val original = testImage(width, height)

        val jpegBytes = ByteArrayOutputStream().also { ImageIO.write(original, "jpg", it) }.toByteArray()

        val decoded = JPEGDecoder.decode(jpegBytes)

        assertEquals(width, decoded.width)
        assertEquals(height, decoded.height)

        val originalRgba = toRgba(original)
        val diff = averageChannelDiff(originalRgba, decoded.rgba)
        assertTrue(diff < 10.0, "average channel diff too high: $diff")
    }

    @Test
    fun `ImageIO correctly decodes a vendored-encoder-produced JPEG`() {
        val width = 64
        val height = 48
        val original = testImage(width, height)
        val originalRgba = toRgba(original)

        val jpegBytes = JPEGEncoder.encode(width, height, originalRgba, quality = 90)

        val decoded = ImageIO.read(ByteArrayInputStream(jpegBytes))
        assertEquals(width, decoded.width)
        assertEquals(height, decoded.height)

        val decodedRgba = toRgba(decoded)
        val diff = averageChannelDiff(originalRgba, decodedRgba)
        assertTrue(diff < 10.0, "average channel diff too high: $diff")
    }

    @Test
    fun `full round trip through the vendored codec alone stays close to source`() {
        val width = 100
        val height = 80
        val original = testImage(width, height)
        val originalRgba = toRgba(original)

        val jpegBytes = JPEGEncoder.encode(width, height, originalRgba, quality = 92)
        val decoded = JPEGDecoder.decode(jpegBytes)

        assertEquals(width, decoded.width)
        assertEquals(height, decoded.height)
        val diff = averageChannelDiff(originalRgba, decoded.rgba)
        assertTrue(diff < 10.0, "average channel diff too high: $diff")
    }
}

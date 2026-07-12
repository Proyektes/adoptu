package com.adoptu.common.image

import kotlin.math.absoluteValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JPEGCodecTest {

    // A synthetic RGBA gradient, avoiding any platform image APIs so this test runs on every
    // KMP target (JVM and JS) the same way.
    private fun gradient(width: Int, height: Int): ByteArray {
        val rgba = ByteArray(width * height * 4)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                rgba[i++] = ((x * 255) / width).toByte()
                rgba[i++] = ((y * 255) / height).toByte()
                rgba[i++] = (((x + y) * 255) / (width + height)).toByte()
                rgba[i++] = 255.toByte()
            }
        }
        return rgba
    }

    @Test
    fun `encode produces a valid JPEG header`() {
        val rgba = gradient(32, 32)
        val jpeg = JPEGEncoder.encode(32, 32, rgba, quality = 90)

        assertTrue(jpeg.size > 4)
        assertEquals(0xFF.toByte(), jpeg[0])
        assertEquals(0xD8.toByte(), jpeg[1]) // SOI marker
        assertEquals(0xFF.toByte(), jpeg[jpeg.size - 2])
        assertEquals(0xD9.toByte(), jpeg[jpeg.size - 1]) // EOI marker
    }

    @Test
    fun `decodeInfo reads dimensions without full decode`() {
        val jpeg = JPEGEncoder.encode(64, 48, gradient(64, 48), quality = 85)
        val info = JPEGDecoder.decodeInfo(jpeg)

        assertEquals(64, info.width)
        assertEquals(48, info.height)
    }

    @Test
    fun `encode then decode round trip preserves dimensions and is visually close`() {
        val width = 32
        val height = 32
        val original = gradient(width, height)

        val jpeg = JPEGEncoder.encode(width, height, original, quality = 95)
        val decoded = JPEGDecoder.decode(jpeg)

        assertEquals(width, decoded.width)
        assertEquals(height, decoded.height)
        assertEquals(width * height * 4, decoded.rgba.size)

        // JPEG is lossy, so pixels won't match exactly - check the average channel error is small.
        var totalDiff = 0L
        var samples = 0
        for (i in original.indices) {
            if (i % 4 == 3) continue // skip alpha, JPEG has no alpha channel
            val a = original[i].toInt() and 0xFF
            val b = decoded.rgba[i].toInt() and 0xFF
            totalDiff += (a - b).absoluteValue
            samples++
        }
        val avgDiff = totalDiff.toDouble() / samples
        assertTrue(avgDiff < 10.0, "average channel diff too high: $avgDiff")
    }

    @Test
    fun `decode rejects data without a valid SOI marker`() {
        val bad = ByteArray(20) { 0 }
        var threw = false
        try {
            JPEGDecoder.decode(bad)
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertTrue(threw)
    }
}

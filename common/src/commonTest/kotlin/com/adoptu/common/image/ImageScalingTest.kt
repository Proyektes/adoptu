package com.adoptu.common.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageScalingTest {

    private fun solidColor(width: Int, height: Int, r: Int, g: Int, b: Int): ByteArray {
        val rgba = ByteArray(width * height * 4)
        var i = 0
        repeat(width * height) {
            rgba[i++] = r.toByte()
            rgba[i++] = g.toByte()
            rgba[i++] = b.toByte()
            rgba[i++] = 255.toByte()
        }
        return rgba
    }

    @Test
    fun `resize returns the same buffer when dimensions are unchanged`() {
        val src = solidColor(10, 10, 100, 150, 200)
        val result = resizeRgba(src, 10, 10, 10, 10)
        assertEquals(src, result)
    }

    @Test
    fun `resize downscales a solid color image without changing its color`() {
        val src = solidColor(100, 100, 10, 20, 30)
        val result = resizeRgba(src, 100, 100, 25, 25)

        assertEquals(25 * 25 * 4, result.size)
        // A solid-color image should stay (approximately) that color after resizing.
        for (i in result.indices step 4) {
            assertTrue((result[i].toInt() and 0xFF - 10) <= 1)
            assertTrue((result[i + 1].toInt() and 0xFF - 20) <= 1)
            assertTrue((result[i + 2].toInt() and 0xFF - 30) <= 1)
        }
    }

    @Test
    fun `resize upscales correctly`() {
        val src = solidColor(10, 10, 50, 60, 70)
        val result = resizeRgba(src, 10, 10, 40, 40)

        assertEquals(40 * 40 * 4, result.size)
    }
}

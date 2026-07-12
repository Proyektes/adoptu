package com.adoptu.common.image

/**
 * Bilinear resize of an interleaved RGBA byte buffer (width * height * 4 bytes). Pure Kotlin,
 * no platform image APIs - this is what lets [JPEGDecoder]/[JPEGEncoder] resize without AWT.
 */
fun resizeRgba(src: ByteArray, srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int): ByteArray {
    if (srcWidth == dstWidth && srcHeight == dstHeight) return src

    val dst = ByteArray(dstWidth * dstHeight * 4)
    val xRatio = srcWidth.toDouble() / dstWidth
    val yRatio = srcHeight.toDouble() / dstHeight

    fun sample(x: Int, y: Int, channel: Int): Int {
        val cx = x.coerceIn(0, srcWidth - 1)
        val cy = y.coerceIn(0, srcHeight - 1)
        return src[(cy * srcWidth + cx) * 4 + channel].toInt() and 0xFF
    }

    for (dy in 0 until dstHeight) {
        val srcYf = dy * yRatio
        val y0 = srcYf.toInt()
        val y1 = y0 + 1
        val yFrac = srcYf - y0

        for (dx in 0 until dstWidth) {
            val srcXf = dx * xRatio
            val x0 = srcXf.toInt()
            val x1 = x0 + 1
            val xFrac = srcXf - x0

            val outIdx = (dy * dstWidth + dx) * 4
            for (channel in 0 until 4) {
                val top = sample(x0, y0, channel) * (1 - xFrac) + sample(x1, y0, channel) * xFrac
                val bottom = sample(x0, y1, channel) * (1 - xFrac) + sample(x1, y1, channel) * xFrac
                val value = (top * (1 - yFrac) + bottom * yFrac).toInt().coerceIn(0, 255)
                dst[outIdx + channel] = value.toByte()
            }
        }
    }
    return dst
}

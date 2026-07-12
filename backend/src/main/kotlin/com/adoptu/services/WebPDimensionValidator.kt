package com.adoptu.services

// No JVM WebP decoder exists without a native/JNI dependency, so uploaded WebP bytes are
// stored as-is (the client already resized/compressed them) rather than run through
// ImageCompressor. This parses just enough of the RIFF/VP8 container header - per Google's
// WebP container spec, RFC 6386 (VP8), and the WebP lossless bitstream spec - to catch a
// spoofed content-type or a maliciously crafted oversized-dimension header before storage.
object WebPDimensionValidator {
    private const val MAX_WIDTH = 4096
    private const val MAX_HEIGHT = 4096
    private const val MIN_HEADER_BYTES = 30

    data class Dimensions(val width: Int, val height: Int)

    fun validate(data: ByteArray): Dimensions {
        val dimensions = parseDimensions(data)
            ?: throw IllegalArgumentException("Invalid or unsupported WebP data")
        if (dimensions.width <= 0 || dimensions.height <= 0 ||
            dimensions.width > MAX_WIDTH || dimensions.height > MAX_HEIGHT
        ) {
            throw IllegalArgumentException("WebP image dimensions exceed maximum of ${MAX_WIDTH}x$MAX_HEIGHT")
        }
        return dimensions
    }

    private fun parseDimensions(data: ByteArray): Dimensions? {
        if (data.size < MIN_HEADER_BYTES) return null
        if (!matches(data, 0, "RIFF") || !matches(data, 8, "WEBP")) return null

        return when {
            matches(data, 12, "VP8X") -> parseVp8x(data)
            matches(data, 12, "VP8L") -> parseVp8l(data)
            matches(data, 12, "VP8 ") -> parseVp8(data)
            else -> null
        }
    }

    // VP8X (extended: alpha/animation/ICC) payload starts at byte 20: 1 byte flags,
    // 3 bytes reserved, then 24-bit LE canvas width-1 (24-26) and height-1 (27-29).
    private fun parseVp8x(data: ByteArray): Dimensions {
        val width = le24(data, 24) + 1
        val height = le24(data, 27) + 1
        return Dimensions(width, height)
    }

    // Simple lossy VP8 payload starts at byte 20: 3-byte frame tag, then the fixed
    // 3-byte start code 0x9d 0x01 0x2a, then two 16-bit LE fields whose low 14 bits are
    // width/height (top 2 bits are horizontal/vertical scale, ignored here).
    private fun parseVp8(data: ByteArray): Dimensions? {
        if (u8(data, 20 + 3) != 0x9d || u8(data, 20 + 4) != 0x01 || u8(data, 20 + 5) != 0x2a) return null
        val width = le16(data, 26) and 0x3FFF
        val height = le16(data, 28) and 0x3FFF
        return Dimensions(width, height)
    }

    // Simple lossless VP8L payload starts at byte 20 with a 1-byte 0x2f signature, then a
    // 32-bit LE value packing two consecutive 14-bit fields: width-1 then height-1.
    private fun parseVp8l(data: ByteArray): Dimensions? {
        if (u8(data, 20) != 0x2f) return null
        val bits = le32(data, 21)
        val width = (bits and 0x3FFF) + 1
        val height = ((bits ushr 14) and 0x3FFF) + 1
        return Dimensions(width, height)
    }

    private fun matches(data: ByteArray, offset: Int, ascii: String): Boolean {
        if (offset + ascii.length > data.size) return false
        for (i in ascii.indices) {
            if (data[offset + i] != ascii[i].code.toByte()) return false
        }
        return true
    }

    private fun u8(data: ByteArray, offset: Int): Int = data[offset].toInt() and 0xFF

    private fun le16(data: ByteArray, offset: Int): Int =
        u8(data, offset) or (u8(data, offset + 1) shl 8)

    private fun le24(data: ByteArray, offset: Int): Int =
        u8(data, offset) or (u8(data, offset + 1) shl 8) or (u8(data, offset + 2) shl 16)

    private fun le32(data: ByteArray, offset: Int): Int =
        u8(data, offset) or (u8(data, offset + 1) shl 8) or (u8(data, offset + 2) shl 16) or (u8(data, offset + 3) shl 24)
}

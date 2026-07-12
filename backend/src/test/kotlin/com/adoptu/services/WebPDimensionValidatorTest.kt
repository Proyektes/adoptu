package com.adoptu.services

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class WebPDimensionValidatorTest {

    private fun ascii(s: String): List<Int> = s.map { it.code }

    private fun le(value: Int, byteCount: Int): List<Int> =
        (0 until byteCount).map { (value shr (it * 8)) and 0xFF }

    private fun riffHeader(fourCc: String, chunkSize: Int = 10): List<Int> =
        ascii("RIFF") + le(0, 4) + ascii("WEBP") + ascii(fourCc) + le(chunkSize, 4)

    private fun vp8xBytes(width: Int, height: Int): ByteArray {
        val payload = le(0, 1) + le(0, 3) + le(width - 1, 3) + le(height - 1, 3)
        return (riffHeader("VP8X") + payload).map { it.toByte() }.toByteArray()
    }

    private fun vp8Bytes(width: Int, height: Int, startCode: List<Int> = listOf(0x9d, 0x01, 0x2a)): ByteArray {
        val frameTag = listOf(0x00, 0x00, 0x00)
        val payload = frameTag + startCode + le(width and 0x3FFF, 2) + le(height and 0x3FFF, 2)
        return (riffHeader("VP8 ") + payload).map { it.toByte() }.toByteArray()
    }

    private fun vp8lBytes(width: Int, height: Int): ByteArray {
        val bits = ((width - 1) and 0x3FFF) or ((((height - 1) and 0x3FFF)) shl 14)
        val payload = listOf(0x2f) + le(bits, 4) + le(0, 5)
        return (riffHeader("VP8L") + payload).map { it.toByte() }.toByteArray()
    }

    @Test
    fun `validate extracts dimensions from VP8X extended header`() {
        val dims = WebPDimensionValidator.validate(vp8xBytes(1080, 810))
        assertEquals(1080, dims.width)
        assertEquals(810, dims.height)
    }

    @Test
    fun `validate extracts dimensions from VP8 lossy header`() {
        val dims = WebPDimensionValidator.validate(vp8Bytes(1080, 720))
        assertEquals(1080, dims.width)
        assertEquals(720, dims.height)
    }

    @Test
    fun `validate extracts dimensions from VP8L lossless header`() {
        val dims = WebPDimensionValidator.validate(vp8lBytes(640, 480))
        assertEquals(640, dims.width)
        assertEquals(480, dims.height)
    }

    @Test
    fun `validate rejects data with wrong RIFF magic bytes`() {
        val bad = vp8xBytes(100, 100).copyOf().also {
            it[0] = 'X'.code.toByte()
        }
        assertThrows<IllegalArgumentException> { WebPDimensionValidator.validate(bad) }
    }

    @Test
    fun `validate rejects truncated data`() {
        val truncated = vp8Bytes(100, 100).copyOf(20)
        assertThrows<IllegalArgumentException> { WebPDimensionValidator.validate(truncated) }
    }

    @Test
    fun `validate rejects VP8 data with wrong start code`() {
        val bad = vp8Bytes(100, 100, startCode = listOf(0x00, 0x00, 0x00))
        assertThrows<IllegalArgumentException> { WebPDimensionValidator.validate(bad) }
    }

    @Test
    fun `validate rejects unrecognized chunk type`() {
        val bad = (riffHeader("ANIM") + le(0, 10)).map { it.toByte() }.toByteArray()
        assertThrows<IllegalArgumentException> { WebPDimensionValidator.validate(bad) }
    }

    @Test
    fun `validate rejects dimensions over the ceiling`() {
        val tooWide = vp8xBytes(5000, 100)
        val error = assertThrows<IllegalArgumentException> { WebPDimensionValidator.validate(tooWide) }
        assertEquals(true, error.message?.contains("exceed maximum"))
    }

    @Test
    fun `validate rejects zero dimensions`() {
        // VP8X/VP8L both encode (dimension - 1), so zero is unreachable through them; VP8's
        // fields are stored directly (no +1), so it's the only chunk type that can produce it.
        val bad = vp8Bytes(0, 100)
        assertThrows<IllegalArgumentException> { WebPDimensionValidator.validate(bad) }
    }
}

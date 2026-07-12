package com.adoptu.services

import com.adoptu.common.image.JPEGDecoder
import com.adoptu.common.image.JPEGEncoder
import com.adoptu.common.image.resizeRgba
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.ImageIO

object ImageCompressor {
    // Matches Instagram's own served resolution (1080px longest side) so re-uploaded
    // photos don't carry resolution far beyond what the site will ever display.
    private const val MAX_WIDTH = 1080
    private const val MAX_HEIGHT = 1080
    private const val JPEG_QUALITY = 80

    fun compress(inputStream: InputStream, format: String): ByteArrayOutputStream {
        return if (format.equals("png", ignoreCase = true)) {
            compressPng(inputStream)
        } else {
            compressJpeg(inputStream.readBytes())
        }
    }

    // Pure-Kotlin path (common/.../image/JPEGEncoder.kt + JPEGDecoder.kt, vendored from
    // korge-image-formats) - no AWT/javax.imageio involved, avoiding the native-image
    // headless-flag/AWT-shared-library cost this format previously required. PNG below still
    // needs javax.imageio - the vendored codec only covers JPEG.
    private fun compressJpeg(bytes: ByteArray): ByteArrayOutputStream {
        val decoded = try {
            JPEGDecoder.decode(bytes)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid storage data or unsupported format", e)
        }
        val (newWidth, newHeight) = calculateDimensions(decoded.width, decoded.height)
        val resizedRgba = resizeRgba(decoded.rgba, decoded.width, decoded.height, newWidth, newHeight)
        val jpegBytes = JPEGEncoder.encode(newWidth, newHeight, resizedRgba, JPEG_QUALITY)
        return ByteArrayOutputStream(jpegBytes.size).apply { write(jpegBytes) }
    }

    private fun compressPng(inputStream: InputStream): ByteArrayOutputStream {
        val image = ImageIO.read(inputStream) ?: throw IllegalArgumentException("Invalid storage data or unsupported format")
        val (newWidth, newHeight) = calculateDimensions(image.width, image.height)

        val resized = if (newWidth != image.width || newHeight != image.height) {
            val resizedImage = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
            val graphics = resizedImage.createGraphics()
            graphics.drawImage(image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH), 0, 0, null)
            graphics.dispose()
            resizedImage
        } else {
            image
        }

        val outputStream = ByteArrayOutputStream()
        ImageIO.write(resized, "png", outputStream)
        return outputStream
    }

    private fun calculateDimensions(width: Int, height: Int): Pair<Int, Int> {
        if (width <= MAX_WIDTH && height <= MAX_HEIGHT) {
            return width to height
        }

        val ratio = width.toFloat() / height.toFloat()
        return if (width > height) {
            MAX_WIDTH to (MAX_WIDTH / ratio).toInt()
        } else {
            (MAX_HEIGHT * ratio).toInt() to MAX_HEIGHT
        }
    }
}

package com.adoptu.services

import com.universaliun.imagekit.exception.InvalidImageException
import com.universaliun.imagekit.exception.UnsupportedImageFormatException
import com.universaliun.imagekit.imaging.ImageResizer
import com.universaliun.imagekit.imaging.RgbaImage
import com.universaliun.imagekit.imaging.jpeg.JpegDecoder
import com.universaliun.imagekit.imaging.jpeg.JpegEncoder
import com.universaliun.imagekit.imaging.png.PngDecoder
import com.universaliun.imagekit.imaging.png.PngEncoder
import java.io.ByteArrayOutputStream
import java.io.InputStream

object ImageCompressor {
    // Matches Instagram's own served resolution (1080px longest side) so re-uploaded
    // photos don't carry resolution far beyond what the site will ever display.
    private const val MAX_WIDTH = 1080
    private const val MAX_HEIGHT = 1080
    private const val JPEG_QUALITY = 80

    fun compress(inputStream: InputStream, format: String): ByteArrayOutputStream {
        val bytes = inputStream.readBytes()
        val encoded = if (format.equals("png", ignoreCase = true)) compressPng(bytes) else compressJpeg(bytes)
        return ByteArrayOutputStream(encoded.size).apply { write(encoded) }
    }

    // ImageKit (com.universaliun.imagekit:imagekit-common) - pure-JVM JPEG/PNG codecs, zero
    // java.awt/javax.imageio dependency. This used to go through javax.imageio for PNG, which
    // crashed GraalVM native-image the first time a PNG was ever uploaded in production
    // (NoClassDefFoundError: java/awt/GraphicsEnvironment - javax.imageio.ImageIO's static
    // initializer loads java.awt.Toolkit, and libawt.so's own JNI_OnLoad calls FindClass on
    // classes never registered for JNI access under native-image's closed-world reflection
    // model). A native-image reflect-config patch could paper over that one crash, but the same
    // init chain keeps calling further un-registered methods one at a time (whack-a-mole) -
    // eliminating the AWT dependency entirely is the durable fix. Extracted into ImageKit
    // (Libraries/ImageKit) so every Universaliun project gets this fix once instead of
    // rediscovering the same crash independently.
    private fun compressJpeg(bytes: ByteArray): ByteArray {
        val decoded = decodeOrThrow(bytes) { JpegDecoder().decode(it) }
        return JpegEncoder(JPEG_QUALITY).encode(resize(decoded))
    }

    private fun compressPng(bytes: ByteArray): ByteArray {
        val decoded = decodeOrThrow(bytes) { PngDecoder().decode(it) }
        return PngEncoder().encode(resize(decoded))
    }

    // ImageKit's decoders throw its own UnsupportedImageFormatException/InvalidImageException
    // (not IllegalArgumentException) - translate at this boundary so PetService's existing
    // catch (e: IllegalArgumentException) around ImageCompressor.compress() keeps working
    // unchanged.
    private fun decodeOrThrow(bytes: ByteArray, decode: (ByteArray) -> RgbaImage): RgbaImage {
        try {
            return decode(bytes)
        } catch (e: UnsupportedImageFormatException) {
            throw IllegalArgumentException("Invalid storage data or unsupported format", e)
        } catch (e: InvalidImageException) {
            throw IllegalArgumentException("Invalid storage data or unsupported format", e)
        }
    }

    private fun resize(image: RgbaImage): RgbaImage {
        val (newWidth, newHeight) = calculateDimensions(image.width, image.height)
        return if (newWidth == image.width && newHeight == image.height) image
        else ImageResizer.resize(image, newWidth, newHeight)
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

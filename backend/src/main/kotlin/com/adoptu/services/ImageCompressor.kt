package com.adoptu.services

import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

object ImageCompressor {
    // Matches Instagram's own served resolution (1080px longest side) so re-uploaded
    // photos don't carry resolution far beyond what the site will ever display.
    private const val MAX_WIDTH = 1080
    private const val MAX_HEIGHT = 1080
    private const val JPEG_QUALITY = 0.8f

    fun compress(inputStream: InputStream, format: String = "jpg"): ByteArrayOutputStream {
        val image = ImageIO.read(inputStream) ?: throw IllegalArgumentException("Invalid storage data or unsupported format")
        val (newWidth, newHeight) = calculateDimensions(image.width, image.height)

        val resized = if (newWidth != image.width || newHeight != image.height) {
            val resizedImage = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
            val graphics = resizedImage.createGraphics()
            graphics.drawImage(image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH), 0, 0, null)
            graphics.dispose()
            resizedImage
        } else {
            if (image.type != BufferedImage.TYPE_INT_RGB) {
                val converted = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
                converted.graphics.drawImage(image, 0, 0, null)
                converted
            } else {
                image
            }
        }

        val outputStream = ByteArrayOutputStream()
        val imageFormat = if (format.equals("png", ignoreCase = true)) "png" else "jpg"
        if (imageFormat == "jpg") {
            writeJpeg(resized, outputStream)
        } else {
            ImageIO.write(resized, imageFormat, outputStream)
        }
        return outputStream
    }

    private fun writeJpeg(image: BufferedImage, outputStream: ByteArrayOutputStream) {
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val params = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = JPEG_QUALITY
        }
        ImageIO.createImageOutputStream(outputStream).use { ios ->
            writer.output = ios
            writer.write(null, IIOImage(image, null, null), params)
        }
        writer.dispose()
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

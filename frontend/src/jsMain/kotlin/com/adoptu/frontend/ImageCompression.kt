package com.adoptu.frontend

import kotlinx.browser.document
import kotlinx.browser.window
import kotlin.js.Promise

// Matches the server's own re-encode target (ImageCompressor.kt) so a phone photo
// is already web-sized before it ever leaves the browser.
private const val MAX_DIMENSION = 1080
private const val INITIAL_QUALITY = 0.8
private const val MIN_QUALITY = 0.4
private const val QUALITY_STEP = 0.1
private const val TARGET_MAX_BYTES = 2 * 1024 * 1024

object ImageCompression {
    fun compress(file: dynamic): Promise<dynamic> {
        return Promise { resolve, _ ->
            val url = window.asDynamic().URL.createObjectURL(file)
            val img = js("new Image()")

            img.onload = {
                window.asDynamic().URL.revokeObjectURL(url)

                val width = img.width as Int
                val height = img.height as Int
                var targetWidth = width
                var targetHeight = height
                if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    if (width >= height) {
                        targetWidth = MAX_DIMENSION
                        targetHeight = (height.toDouble() / width * MAX_DIMENSION).toInt()
                    } else {
                        targetHeight = MAX_DIMENSION
                        targetWidth = (width.toDouble() / height * MAX_DIMENSION).toInt()
                    }
                }

                val canvas = document.createElement("canvas").asDynamic()
                canvas.width = targetWidth
                canvas.height = targetHeight
                canvas.getContext("2d").drawImage(img, 0, 0, targetWidth, targetHeight)

                // Step quality down until the output is under the 2MB target, or we hit
                // the quality floor - whichever comes first wins with whatever it produced.
                fun attempt(quality: Double) {
                    canvas.toBlob({ blob: dynamic ->
                        val size = (blob?.size as? Int) ?: 0
                        when {
                            blob == null -> resolve(file)
                            size <= TARGET_MAX_BYTES || quality <= MIN_QUALITY -> resolve(blob)
                            else -> attempt(quality - QUALITY_STEP)
                        }
                    }, "image/jpeg", quality)
                }
                attempt(INITIAL_QUALITY)
                Unit
            }
            // Fall back to the original file if it can't be decoded client-side
            // (e.g. an <img>-unsupported format) - the server still validates/compresses it.
            img.onerror = { _: dynamic -> resolve(file) }
            img.src = url
        }
    }
}

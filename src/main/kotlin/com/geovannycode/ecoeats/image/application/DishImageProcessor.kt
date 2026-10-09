package com.geovannycode.ecoeats.image.application

import org.springframework.stereotype.Component
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.roundToInt

internal data class ProcessedDishImage(
    val original: ByteArray,
    val thumbnail: ByteArray,
)

@Component
internal class DishImageProcessor {
    fun process(bytes: ByteArray): ProcessedDishImage? {
        val decoded =
            try {
                bytes.inputStream().use { input ->
                    ImageIO.createImageInputStream(input).use { stream ->
                        val readers = ImageIO.getImageReaders(stream)
                        if (!readers.hasNext()) return null
                        val reader = readers.next()
                        try {
                            reader.input = stream
                            // Bound decoded memory before allocating a raster for compressed uploads.
                            val pixels = reader.getWidth(0).toLong() * reader.getHeight(0)
                            if (pixels <= 0 || pixels > 40_000_000) return null
                            reader.read(0)
                        } finally {
                            reader.dispose()
                        }
                    }
                }
            } catch (_: IOException) {
                return null
            } catch (_: IllegalArgumentException) {
                return null
            }
        if (decoded == null) return null
        return ProcessedDishImage(decoded.jpeg(1200, 0.85f), decoded.jpeg(300, 0.8f))
    }
}

private fun BufferedImage.jpeg(
    maxSide: Int,
    quality: Float,
): ByteArray {
    val scale = minOf(1.0, maxSide.toDouble() / maxOf(width, height))
    val resized = BufferedImage(maxOf(1, (width * scale).roundToInt()), maxOf(1, (height * scale).roundToInt()), BufferedImage.TYPE_INT_RGB)
    val graphics = resized.createGraphics()
    try {
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, resized.width, resized.height)
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        graphics.drawImage(this, 0, 0, resized.width, resized.height, null)
    } finally {
        graphics.dispose()
    }
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    return ByteArrayOutputStream().use { output ->
        try {
            ImageIO.createImageOutputStream(output).use { stream ->
                writer.output = stream
                val parameters =
                    writer.defaultWriteParam.apply {
                        compressionMode = ImageWriteParam.MODE_EXPLICIT
                        compressionQuality = quality
                    }
                // No source metadata is carried into the newly encoded JPEG.
                writer.write(null, IIOImage(resized, null, null), parameters)
            }
            output.toByteArray()
        } finally {
            writer.dispose()
        }
    }
}

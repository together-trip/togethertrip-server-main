package com.togethertrip.main.triprecap.service.ai

import org.springframework.stereotype.Component
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.roundToInt

fun interface TripRecapReferenceImageOptimizer {
    fun optimize(content: TripRecapPhotoContent): TripRecapPhotoContent?

    companion object {
        val IDENTITY = TripRecapReferenceImageOptimizer { it }
    }
}

@Component
class DefaultTripRecapReferenceImageOptimizer(
    private val properties: OpenAiTripRecapProperties,
) : TripRecapReferenceImageOptimizer {

    override fun optimize(content: TripRecapPhotoContent): TripRecapPhotoContent? {
        val source = decodeWithinPixelLimit(content.bytes) ?: return null
        val maxDimension = properties.maxReferenceDimension.coerceIn(MIN_REFERENCE_DIMENSION, MAX_REFERENCE_DIMENSION)
        val scale = minOf(1.0, maxDimension.toDouble() / maxOf(source.width, source.height))
        val targetWidth = (source.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).roundToInt().coerceAtLeast(1)
        val targetType = if (source.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val normalized = BufferedImage(targetWidth, targetHeight, targetType)
        normalized.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            drawImage(source, 0, 0, targetWidth, targetHeight, null)
            dispose()
        }
        val bytes = ByteArrayOutputStream().use { output ->
            if (!ImageIO.write(normalized, OUTPUT_FORMAT, output)) {
                return null
            }
            output.toByteArray()
        }
        return TripRecapPhotoContent(
            filename = "${content.filename.substringBeforeLast('.', content.filename)}.png",
            contentType = "image/png",
            bytes = bytes,
        )
    }

    private fun decodeWithinPixelLimit(bytes: ByteArray): BufferedImage? {
        val stream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: return null
        return stream.use { input ->
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) {
                return@use null
            }
            val reader = readers.next()
            try {
                reader.input = input
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                if (width.toLong() * height > MAX_REFERENCE_PIXELS) {
                    null
                } else {
                    reader.read(0)
                }
            } finally {
                reader.dispose()
            }
        }
    }

    companion object {
        private const val OUTPUT_FORMAT = "png"
        private const val MIN_REFERENCE_DIMENSION = 256
        private const val MAX_REFERENCE_DIMENSION = 2_048
        private const val MAX_REFERENCE_PIXELS = 40_000_000L
    }
}

package com.togethertrip.main.triprecap.service.ai

import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TripRecapReferenceImageOptimizerTest {

    @Test
    fun `large jpeg is resized and normalized to metadata free png`() {
        val properties = OpenAiTripRecapProperties().apply { maxReferenceDimension = 1_024 }
        val source = encodedImage(2_000, 1_000, "jpg")

        val optimized = assertNotNull(
            DefaultTripRecapReferenceImageOptimizer(properties).optimize(
                TripRecapPhotoContent("travel.jpg", "image/jpeg", source)
            )
        )
        val image = assertNotNull(ImageIO.read(ByteArrayInputStream(optimized.bytes)))

        assertEquals("travel.png", optimized.filename)
        assertEquals("image/png", optimized.contentType)
        assertEquals(1_024, image.width)
        assertEquals(512, image.height)
        assertTrue(optimized.bytes.size < source.size)
    }

    @Test
    fun `small image keeps dimensions while invalid content is skipped`() {
        val optimizer = DefaultTripRecapReferenceImageOptimizer(OpenAiTripRecapProperties())
        val source = encodedImage(256, 128, "png")

        val optimized = assertNotNull(
            optimizer.optimize(TripRecapPhotoContent("small.png", "image/png", source))
        )

        assertEquals(256, ImageIO.read(ByteArrayInputStream(optimized.bytes)).width)
        assertNull(optimizer.optimize(TripRecapPhotoContent("broken.png", "image/png", byteArrayOf(1, 2, 3))))
    }

    @Test
    fun `configured dimension is clamped and alpha channel is preserved`() {
        val properties = OpenAiTripRecapProperties().apply { maxReferenceDimension = 1 }
        val source = encodedImage(512, 256, "png", BufferedImage.TYPE_INT_ARGB)

        val optimized = assertNotNull(
            DefaultTripRecapReferenceImageOptimizer(properties).optimize(
                TripRecapPhotoContent("transparent", "image/png", source)
            )
        )
        val image = assertNotNull(ImageIO.read(ByteArrayInputStream(optimized.bytes)))

        assertEquals("transparent.png", optimized.filename)
        assertEquals(256, image.width)
        assertEquals(128, image.height)
        assertTrue(image.colorModel.hasAlpha())
    }

    @Test
    fun `configured dimension cannot exceed the safe upper bound`() {
        val properties = OpenAiTripRecapProperties().apply { maxReferenceDimension = 9_999 }
        val source = encodedImage(2_100, 1_050, "png")

        val optimized = assertNotNull(
            DefaultTripRecapReferenceImageOptimizer(properties).optimize(
                TripRecapPhotoContent("large.png", "image/png", source)
            )
        )
        val image = assertNotNull(ImageIO.read(ByteArrayInputStream(optimized.bytes)))

        assertEquals(2_048, image.width)
        assertEquals(1_024, image.height)
    }

    @Test
    fun `identity optimizer returns the original reference`() {
        val content = TripRecapPhotoContent("original.jpg", "image/jpeg", byteArrayOf(1))

        assertSame(content, TripRecapReferenceImageOptimizer.IDENTITY.optimize(content))
    }

    private fun encodedImage(
        width: Int,
        height: Int,
        format: String,
        type: Int = BufferedImage.TYPE_INT_RGB,
    ): ByteArray {
        val image = BufferedImage(width, height, type)
        image.createGraphics().apply {
            color = Color(30, 90, 160)
            fillRect(0, 0, width, height)
            dispose()
        }
        return ByteArrayOutputStream().use { output ->
            check(ImageIO.write(image, format, output))
            output.toByteArray()
        }
    }
}

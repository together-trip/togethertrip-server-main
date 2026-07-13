package com.togethertrip.main.triprecap.service.ai

import com.togethertrip.main.triprecap.domain.TripRecapStyle
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class StubTripRecapGeneratorTest {

    private val generator = StubTripRecapGenerator()

    @Test
    fun `stub recap generator returns valid vertical png images`() {
        val result = generator.generate(request(style = TripRecapStyle.PHOTO))

        assertEquals("stub", result.provider)
        assertEquals("stub-trip-recap-v1", result.model)
        assertEquals(3, result.scenes.size)

        val bytes = result.scenes.first().imageBytes
        assertContentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
            bytes.take(4).toByteArray(),
        )
        val image = ImageIO.read(ByteArrayInputStream(bytes))
        assertNotNull(image)
        assertEquals(540, image.width)
        assertEquals(960, image.height)
    }

    @Test
    fun `stub recap generator does not return raw text bytes`() {
        val bytes = generator.generate(request(style = TripRecapStyle.PHOTO))
            .scenes
            .first()
            .imageBytes

        val text = bytes.toString(Charsets.UTF_8)

        assertFalse(text.contains("stub-trip-recap"))
        assertFalse(text.contains("제주 여행"))
    }

    @Test
    fun `photo and illustration styles produce different placeholder images`() {
        val photo = generator.generate(request(style = TripRecapStyle.PHOTO))
            .scenes
            .first()
            .imageBytes
        val illustration = generator.generate(request(style = TripRecapStyle.ILLUSTRATION))
            .scenes
            .first()
            .imageBytes

        assertFalse(photo.contentEquals(illustration))
    }

    private fun request(style: TripRecapStyle): TripRecapGenerateRequest {
        return TripRecapGenerateRequest(
            tripTitle = "제주 여행",
            startDate = LocalDate.of(2026, 6, 1),
            endDate = LocalDate.of(2026, 6, 5),
            defaultCurrency = "KRW",
            memberCount = 3,
            style = style,
            countries = listOf(
                TripRecapCountryInput(
                    countryCode = "KR",
                    countryName = "대한민국",
                ),
            ),
            places = emptyList(),
            expenseSignals = emptyList(),
            photoReferences = emptyList(),
        )
    }
}

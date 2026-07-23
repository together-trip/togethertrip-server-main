package com.togethertrip.main.triprecap.service.ai

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TripRecapImageOutputProfileTest {

    @Test
    fun `economy is the default and ignores legacy custom values`() {
        val properties = OpenAiTripRecapProperties().apply {
            size = "1152x2048"
            quality = "medium"
        }

        assertEquals(
            TripRecapImageOutputSettings("864x1536", "low"),
            properties.outputSettings(),
        )
    }

    @Test
    fun `balanced and custom profiles resolve their own settings`() {
        val properties = OpenAiTripRecapProperties()

        properties.outputProfile = TripRecapImageOutputProfile.BALANCED
        assertEquals(
            TripRecapImageOutputSettings("1152x2048", "medium"),
            properties.outputSettings(),
        )

        properties.outputProfile = TripRecapImageOutputProfile.CUSTOM
        properties.size = "1440x2560"
        properties.quality = "high"
        assertEquals(
            TripRecapImageOutputSettings("1440x2560", "high"),
            properties.outputSettings(),
        )
    }
}

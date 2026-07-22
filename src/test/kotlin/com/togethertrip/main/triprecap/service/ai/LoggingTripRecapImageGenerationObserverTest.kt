package com.togethertrip.main.triprecap.service.ai

import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class LoggingTripRecapImageGenerationObserverTest {
    private val observer = LoggingTripRecapImageGenerationObserver()

    @Test
    fun `성공 usage와 usage 없는 실패를 안전하게 기록한다`() {
        val success = observation(
            success = true,
            usage = TripRecapImageTokenUsage(100, 20, 80, 10, 10, 0),
        )
        val failure = observation(success = false, usage = null, failureType = "TimeoutException")

        observer.record(success)
        observer.record(failure)
        TripRecapImageGenerationObserver.NOOP.record(success)

        assertNotNull(success.usage)
        assertNotNull(failure.failureType)
    }

    private fun observation(
        success: Boolean,
        usage: TripRecapImageTokenUsage?,
        failureType: String? = null,
    ) = TripRecapImageGenerationObservation(
        operation = "generation",
        model = "gpt-image-2",
        size = "1152x2048",
        quality = "medium",
        referenceImageCount = 0,
        durationMillis = 100,
        success = success,
        usage = usage,
        failureType = failureType,
    )
}

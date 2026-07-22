package com.togethertrip.main.triprecap.service.ai

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LoggingTripRecapImageGenerationObserver : TripRecapImageGenerationObserver {
    override fun record(observation: TripRecapImageGenerationObservation) {
        val usage = observation.usage
        log.info(
            "trip_recap_image operation={} model={} size={} quality={} references={} success={} " +
                "duration_ms={} total_tokens={} input_tokens={} output_tokens={} text_input_tokens={} " +
                "image_input_tokens={} cached_input_tokens={} failure_type={}",
            observation.operation,
            observation.model,
            observation.size,
            observation.quality,
            observation.referenceImageCount,
            observation.success,
            observation.durationMillis,
            usage?.totalTokens,
            usage?.inputTokens,
            usage?.outputTokens,
            usage?.textInputTokens,
            usage?.imageInputTokens,
            usage?.cachedInputTokens,
            observation.failureType,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(LoggingTripRecapImageGenerationObserver::class.java)
    }
}

package com.togethertrip.main.triprecap.service.ai

fun interface TripRecapImageGenerationObserver {
    fun record(observation: TripRecapImageGenerationObservation)

    companion object {
        val NOOP = TripRecapImageGenerationObserver { }
    }
}

data class TripRecapImageGenerationObservation(
    val operation: String,
    val model: String,
    val size: String,
    val quality: String,
    val referenceImageCount: Int,
    val referenceImageBytes: Long = 0,
    val cacheHit: Boolean = false,
    val durationMillis: Long,
    val success: Boolean,
    val usage: TripRecapImageTokenUsage?,
    val failureType: String? = null,
)

data class TripRecapImageTokenUsage(
    val totalTokens: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val textInputTokens: Long,
    val imageInputTokens: Long,
    val cachedInputTokens: Long,
)

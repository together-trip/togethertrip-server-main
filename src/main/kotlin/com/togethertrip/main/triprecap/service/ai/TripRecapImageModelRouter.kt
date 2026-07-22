package com.togethertrip.main.triprecap.service.ai

import org.springframework.stereotype.Component

fun interface TripRecapImageModelRouter {
    fun route(preferredModel: String): TripRecapImageModelRoute
}

data class TripRecapImageModelRoute(
    val model: String,
    val reason: String,
)

@Component
class DefaultTripRecapImageModelRouter : TripRecapImageModelRouter {

    override fun route(preferredModel: String): TripRecapImageModelRoute {
        return when (preferredModel) {
            GPT_IMAGE_2, GPT_IMAGE_2_SNAPSHOT ->
                TripRecapImageModelRoute(preferredModel, "flexible-size-edit-capable")

            GPT_IMAGE_1, GPT_IMAGE_1_MINI ->
                TripRecapImageModelRoute(GPT_IMAGE_2, "legacy-model-upgrade-for-exact-9x16")

            else -> throw IllegalArgumentException("Unsupported OpenAI trip recap image model: $preferredModel")
        }
    }

    companion object {
        private const val GPT_IMAGE_2 = "gpt-image-2"
        private const val GPT_IMAGE_2_SNAPSHOT = "gpt-image-2-2026-04-21"
        private const val GPT_IMAGE_1 = "gpt-image-1"
        private const val GPT_IMAGE_1_MINI = "gpt-image-1-mini"
    }
}

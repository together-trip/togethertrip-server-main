package com.togethertrip.main.triprecap.service.ai

import com.togethertrip.main.triprecap.domain.TripRecapStyle
import org.springframework.stereotype.Component

@Component
class StubTripRecapGenerator : TripRecapGenerator {

    override fun generate(request: TripRecapGenerateRequest): TripRecapGenerateResult {
        val sceneCount = determineSceneCount(request)
        val scenes = (1..sceneCount).map { order ->
            val description = buildSceneDescription(request, order)
            TripRecapGeneratedScene(
                order = order,
                sceneDescription = description,
                imagePrompt = buildImagePrompt(
                    request = request,
                    sceneDescription = description,
                ),
                imageBytes = "stub-trip-recap:${request.tripTitle}:$order".toByteArray(),
            )
        }

        return TripRecapGenerateResult(
            provider = "stub",
            model = "stub-trip-recap-v1",
            scenes = scenes,
        )
    }

    private fun determineSceneCount(request: TripRecapGenerateRequest): Int {
        val richness = request.places.size + request.expenseSignals.size + request.photoReferences.size
        return when {
            richness >= 10 -> 7
            richness >= 5 -> 5
            else -> 3
        }
    }

    private fun buildSceneDescription(
        request: TripRecapGenerateRequest,
        order: Int,
    ): String {
        val styleLabel = when (request.style) {
            TripRecapStyle.PHOTO -> "cinematic travel snapshot"
            TripRecapStyle.ILLUSTRATION -> "emotional travel illustration"
        }
        val place = request.places.getOrNull(order - 1)?.name
            ?: request.countries.getOrNull((order - 1) % request.countries.size.coerceAtLeast(1))?.countryName
            ?: request.tripTitle

        return "$styleLabel scene $order for ${request.memberCount} travelers around $place"
    }

    private fun buildImagePrompt(
        request: TripRecapGenerateRequest,
        sceneDescription: String,
    ): String {
        val baseStyle = when (request.style) {
            TripRecapStyle.PHOTO -> "cinematic realistic travel snapshot"
            TripRecapStyle.ILLUSTRATION -> "warm editorial travel illustration"
        }

        return listOf(
            baseStyle,
            sceneDescription,
            "vertical 9:16 composition",
            "no text, no letters, no captions, no logos",
            "no face close-up, no identifiable real person",
            "people only if natural, seen from behind or as silhouettes",
        ).joinToString(", ")
    }
}

package com.togethertrip.main.triprecap.service.ai

enum class TripRecapImageOutputProfile(
    val size: String?,
    val quality: String?,
) {
    ECONOMY("864x1536", "low"),
    BALANCED("1152x2048", "medium"),
    CUSTOM(null, null),
    ;

    fun resolve(customSize: String, customQuality: String) = TripRecapImageOutputSettings(
        size = size ?: customSize,
        quality = quality ?: customQuality,
    )
}

data class TripRecapImageOutputSettings(
    val size: String,
    val quality: String,
)

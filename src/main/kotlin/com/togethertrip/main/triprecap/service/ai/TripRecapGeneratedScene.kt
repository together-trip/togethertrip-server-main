package com.togethertrip.main.triprecap.service.ai

data class TripRecapGeneratedScene(
    val order: Int,
    val sceneDescription: String,
    val imagePrompt: String,
    val imageBytes: ByteArray,
)

package com.togethertrip.main.triprecap.service

data class TripRecapStoredScene(
    val order: Int,
    val sceneDescription: String,
    val imagePrompt: String,
    val imageObjectKey: String,
    val imageUrl: String,
    val generationProvider: String,
    val generationModel: String,
)

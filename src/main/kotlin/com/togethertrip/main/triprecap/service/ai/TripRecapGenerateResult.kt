package com.togethertrip.main.triprecap.service.ai

data class TripRecapGenerateResult(
    val provider: String,
    val model: String,
    val scenes: List<TripRecapGeneratedScene>,
)

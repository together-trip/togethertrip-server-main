package com.togethertrip.main.triprecap.service.ai

import java.time.Instant

data class TripRecapPlaceInput(
    val name: String,
    val occurredAt: Instant?,
)

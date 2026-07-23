package com.togethertrip.main.triprecap.service

import com.togethertrip.main.triprecap.domain.TripRecapStyle
import com.togethertrip.main.triprecap.service.ai.TripRecapGenerateRequest

data class TripRecapGenerationContext(
    val recapId: Long,
    val tripId: Long,
    val style: TripRecapStyle,
    val request: TripRecapGenerateRequest,
)

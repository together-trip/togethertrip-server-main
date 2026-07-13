package com.togethertrip.main.triprecap.dto.request

import com.togethertrip.main.triprecap.domain.TripRecapStyle
import jakarta.validation.constraints.NotNull

data class TripRecapRetryRequest(
    @field:NotNull
    val style: TripRecapStyle?,
)

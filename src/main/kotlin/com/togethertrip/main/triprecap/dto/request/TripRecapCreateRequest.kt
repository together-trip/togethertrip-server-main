package com.togethertrip.main.triprecap.dto.request

import com.togethertrip.main.triprecap.domain.TripRecapStyle
import jakarta.validation.constraints.NotNull

data class TripRecapCreateRequest(
    @field:NotNull
    val style: TripRecapStyle?,
)

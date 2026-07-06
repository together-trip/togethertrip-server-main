package com.togethertrip.main.triprecap.dto.response

import com.togethertrip.main.triprecap.domain.TripRecapStatus

data class TripRecapCreateResponse(
    val recapId: Long,
    val status: TripRecapStatus,
)

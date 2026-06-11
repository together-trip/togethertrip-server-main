package com.togethertrip.main.trip.dto.request

import jakarta.validation.constraints.Size

data class JoinTripRequest(
    @field:Size(max = 20)
    val code: String? = null,

    @field:Size(max = 100)
    val token: String? = null,
)

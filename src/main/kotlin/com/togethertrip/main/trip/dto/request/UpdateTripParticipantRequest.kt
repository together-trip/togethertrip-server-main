package com.togethertrip.main.trip.dto.request

import jakarta.validation.constraints.Size

data class UpdateTripParticipantRequest(
    @field:Size(max = 50)
    val displayName: String? = null,
    @field:Size(max = 500)
    val profileImageUrl: String? = null,
)

package com.togethertrip.main.trip.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AddTripParticipantRequest(
    @field:NotBlank
    @field:Size(max = 50)
    val displayName: String,
    @field:Size(max = 500)
    val profileImageUrl: String? = null,
)

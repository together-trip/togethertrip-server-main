package com.togethertrip.main.trip.dto.request

import jakarta.validation.constraints.Positive

data class LinkTripParticipantRequest(
    @field:Positive
    val participantId: Long,
    @field:Positive
    val userId: Long,
)

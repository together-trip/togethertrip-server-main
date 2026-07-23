package com.togethertrip.main.trip.domain

data class TripParticipantProfilePatch(
    val displayName: String?,
    val profileImageUrl: FieldChange<String?>,
)

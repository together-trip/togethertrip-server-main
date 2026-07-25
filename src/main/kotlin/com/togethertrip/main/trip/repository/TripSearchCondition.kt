package com.togethertrip.main.trip.repository

import com.togethertrip.main.trip.domain.TripStatus
import java.time.Instant

data class TripSearchCondition(
    val userId: Long,
    val status: TripStatus?,
    val cursorCreatedAt: Instant?,
    val cursorId: Long?,
) {
    init {
        require((cursorCreatedAt == null) == (cursorId == null)) {
            "cursorCreatedAt and cursorId must be provided together"
        }
    }
}

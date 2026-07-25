package com.togethertrip.main.trip.repository

import com.togethertrip.main.trip.domain.Trip
import org.springframework.data.domain.Pageable

fun interface TripQueryRepository {
    fun findAccessibleTrips(
        condition: TripSearchCondition,
        pageable: Pageable,
    ): List<Trip>
}

package com.togethertrip.main.settlement.repository

import com.togethertrip.main.settlement.domain.TripParticipantBalanceSummary
import org.springframework.data.jpa.repository.JpaRepository

interface TripParticipantBalanceSummaryRepository : JpaRepository<TripParticipantBalanceSummary, Long> {

    fun findByTripId(tripId: Long): List<TripParticipantBalanceSummary>

    fun findByTripIdAndTripParticipantId(
        tripId: Long,
        tripParticipantId: Long,
    ): TripParticipantBalanceSummary?
}

package com.togethertrip.main.settlement.repository

import com.togethertrip.main.settlement.domain.TripParticipantBalanceSummary
import org.springframework.data.jpa.repository.JpaRepository

interface TripParticipantBalanceSummaryRepository : JpaRepository<TripParticipantBalanceSummary, Long> {

    fun findByTripIdAndDeletedAtIsNull(tripId: Long): List<TripParticipantBalanceSummary>

    fun findByTripIdAndTripParticipantIdAndDeletedAtIsNull(
        tripId: Long,
        tripParticipantId: Long,
    ): TripParticipantBalanceSummary?
}

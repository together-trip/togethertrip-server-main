package com.togethertrip.main.triprecap.service

import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripSettlementStatus
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

@Component
class TripRecapAvailabilityPolicy {

    private val clock: Clock = Clock.systemDefaultZone()

    fun isAvailable(trip: Trip): Boolean {
        val endDate = trip.endDate ?: return false

        return endDate.isBefore(LocalDate.now(clock)) &&
            trip.settlementStatus == TripSettlementStatus.SETTLED
    }
}

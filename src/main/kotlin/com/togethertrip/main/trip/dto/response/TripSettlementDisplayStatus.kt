package com.togethertrip.main.trip.dto.response

import com.togethertrip.main.trip.domain.TripSettlementStatus

enum class TripSettlementDisplayStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED;

    companion object {
        fun fromTripStatus(settlementStatus: TripSettlementStatus): TripSettlementDisplayStatus {
            return when (settlementStatus) {
                TripSettlementStatus.NOT_STARTED -> NOT_STARTED
                TripSettlementStatus.IN_PROGRESS -> IN_PROGRESS
                TripSettlementStatus.SETTLED -> COMPLETED
            }
        }
    }
}

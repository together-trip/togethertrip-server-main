package com.togethertrip.main.trip.dto.response

import com.togethertrip.main.trip.domain.Trip
import java.time.LocalDate

data class TripSummaryResponse(
    val id: Long,
    val title: String,
    val defaultCurrency: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val tripStatus: String,
    val settlementStatus: String,
    val ownerUserId: Long,
) {
    companion object {
        fun from(trip: Trip): TripSummaryResponse {
            return TripSummaryResponse(
                id = trip.id,
                title = trip.title,
                defaultCurrency = trip.defaultCurrency,
                startDate = trip.startDate,
                endDate = trip.endDate,
                tripStatus = trip.tripStatus.name,
                settlementStatus = trip.settlementStatus.name,
                ownerUserId = trip.ownerUser.id,
            )
        }
    }
}

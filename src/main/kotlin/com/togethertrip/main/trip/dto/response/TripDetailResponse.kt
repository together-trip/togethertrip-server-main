package com.togethertrip.main.trip.dto.response

import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripSettlementStatus
import com.togethertrip.main.trip.domain.TripStatus
import java.time.Instant
import java.time.LocalDate

data class TripDetailResponse(
    val id: Long,
    val ownerUserId: Long,
    val title: String,
    val defaultCurrency: String,
    val exchangeRateBaseDate: LocalDate?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val tripStatus: TripStatus,
    val settlementStatus: TripSettlementStatus,
    val settlementDisplayStatus: TripSettlementDisplayStatus,
    val settledAt: Instant?,
    val countries: List<TripCountryResponse>,
    val participants: List<TripParticipantSummaryResponse>,
) {
    companion object {
        fun from(
            trip: Trip,
            countries: List<TripCountryResponse>,
            participants: List<TripParticipantSummaryResponse>,
            settlementDisplayStatus: TripSettlementDisplayStatus =
                TripSettlementDisplayStatus.fromTripStatus(trip.settlementStatus),
        ): TripDetailResponse {
            return TripDetailResponse(
                id = trip.id,
                ownerUserId = trip.ownerUser.id,
                title = trip.title,
                defaultCurrency = trip.defaultCurrency,
                exchangeRateBaseDate = trip.exchangeRateBaseDate,
                startDate = trip.startDate,
                endDate = trip.endDate,
                tripStatus = trip.tripStatus,
                settlementStatus = trip.settlementStatus,
                settlementDisplayStatus = settlementDisplayStatus,
                settledAt = trip.settledAt,
                countries = countries,
                participants = participants,
            )
        }
    }
}

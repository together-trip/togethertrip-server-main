package com.togethertrip.main.trip.dto.response

import com.togethertrip.main.trip.domain.Trip
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
    val tripStatus: String,
    val settlementStatus: String,
    val settledAt: Instant?,
    val countries: List<TripCountryResponse>,
    val participants: List<TripParticipantSummaryResponse>,
) {
    companion object {
        fun from(
            trip: Trip,
            countries: List<TripCountryResponse>,
            participants: List<TripParticipantSummaryResponse>,
        ): TripDetailResponse {
            return TripDetailResponse(
                id = trip.id,
                ownerUserId = trip.ownerUser.id,
                title = trip.title,
                defaultCurrency = trip.defaultCurrency,
                exchangeRateBaseDate = trip.exchangeRateBaseDate,
                startDate = trip.startDate,
                endDate = trip.endDate,
                tripStatus = trip.tripStatus.name,
                settlementStatus = trip.settlementStatus.name,
                settledAt = trip.settledAt,
                countries = countries,
                participants = participants,
            )
        }
    }
}

package com.togethertrip.main.trip.dto.response

import com.togethertrip.main.trip.domain.Trip

data class TripCountriesResponse(
    val tripId: Long,
    val countries: List<TripCountryResponse>,
) {
    companion object {
        fun from(
            trip: Trip,
            countries: List<TripCountryResponse>,
        ): TripCountriesResponse {
            return TripCountriesResponse(
                tripId = trip.id,
                countries = countries,
            )
        }
    }
}

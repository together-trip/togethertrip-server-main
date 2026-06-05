package com.togethertrip.main.trip.dto.response

data class TripCountriesResponse(
    val tripId: Long,
    val countries: List<TripCountryResponse>,
)

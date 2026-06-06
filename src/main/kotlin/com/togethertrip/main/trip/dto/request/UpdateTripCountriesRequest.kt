package com.togethertrip.main.trip.dto.request

import jakarta.validation.Valid

data class UpdateTripCountriesRequest(
    @field:Valid
    val countries: List<TripCountryInput> = emptyList(),
)

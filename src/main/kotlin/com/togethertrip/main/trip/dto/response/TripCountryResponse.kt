package com.togethertrip.main.trip.dto.response

import com.togethertrip.main.trip.domain.TripCountry

data class TripCountryResponse(
    val id: Long,
    val countryCode: String,
    val countryName: String,
    val sortOrder: Int,
) {
    companion object {
        fun from(tripCountry: TripCountry): TripCountryResponse {
            return TripCountryResponse(
                id = tripCountry.id,
                countryCode = tripCountry.countryCode,
                countryName = tripCountry.countryName,
                sortOrder = tripCountry.sortOrder,
            )
        }
    }
}

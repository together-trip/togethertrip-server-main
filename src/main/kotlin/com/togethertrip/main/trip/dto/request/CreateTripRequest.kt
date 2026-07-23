package com.togethertrip.main.trip.dto.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class CreateTripRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val title: String,
    @field:NotBlank
    @field:Size(min = 3, max = 3)
    val defaultCurrency: String,
    val exchangeRateBaseDate: LocalDate? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    @field:Valid
    val countries: List<TripCountryInput> = emptyList(),
    @field:Valid
    val participants: List<TripCompanionInput> = emptyList(),
)

data class TripCountryInput(
    @field:NotBlank
    @field:Size(min = 2, max = 2)
    val countryCode: String,
    @field:NotBlank
    @field:Size(max = 100)
    val countryName: String,
    val sortOrder: Int? = null,
)

data class TripCompanionInput(
    @field:NotBlank
    @field:Size(max = 50)
    val displayName: String,
    @field:Size(max = 500)
    val profileImageUrl: String? = null,
    @field:Positive
    val userId: Long? = null,
)

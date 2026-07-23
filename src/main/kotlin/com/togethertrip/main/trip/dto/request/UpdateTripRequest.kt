package com.togethertrip.main.trip.dto.request

import jakarta.validation.constraints.Size
import java.time.LocalDate

data class UpdateTripRequest(
    @field:Size(max = 100)
    val title: String? = null,
    @field:Size(min = 3, max = 3)
    val defaultCurrency: String? = null,
    val exchangeRateBaseDate: LocalDate? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
)

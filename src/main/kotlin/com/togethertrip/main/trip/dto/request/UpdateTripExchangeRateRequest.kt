package com.togethertrip.main.trip.dto.request

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class UpdateTripExchangeRateRequest(
    @field:NotNull
    @field:DecimalMin(value = "0.000001")
    val rate: BigDecimal,
)

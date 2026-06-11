package com.togethertrip.main.exchange.dto.request

import java.time.LocalDate

data class ExchangeRateBackfillRequest(
    val from: LocalDate,
    val to: LocalDate,
)

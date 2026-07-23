package com.togethertrip.main.exchange.dto.response

import java.time.LocalDate

data class ExchangeRateSearchResponse(
    val baseCurrency: String,
    val date: LocalDate?,
    val from: LocalDate?,
    val to: LocalDate?,
    val rates: List<ExchangeRateResponse>,
)

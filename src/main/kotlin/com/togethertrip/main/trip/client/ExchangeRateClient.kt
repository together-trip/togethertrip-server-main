package com.togethertrip.main.trip.client

import java.math.BigDecimal
import java.time.LocalDate

interface ExchangeRateClient {

    fun fetchRates(
        baseCurrency: String,
        targetCurrencies: Set<String>,
        rateDate: LocalDate,
    ): List<ExchangeRateQuote>
}

data class ExchangeRateQuote(
    val baseCurrency: String,
    val targetCurrency: String,
    val rate: BigDecimal,
    val rateDate: LocalDate,
    val source: String,
)

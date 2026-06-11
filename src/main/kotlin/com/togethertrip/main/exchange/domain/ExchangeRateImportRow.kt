package com.togethertrip.main.exchange.domain

import java.math.BigDecimal
import java.time.LocalDate

data class ExchangeRateImportRow(
    val baseCurrency: String,
    val targetCurrency: String,
    val rate: BigDecimal,
    val rateDate: LocalDate,
    val source: String,
)

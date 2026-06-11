package com.togethertrip.main.exchange.dto.request

import java.time.LocalDate

data class ExchangeRateBackfillRequest(
    val from: LocalDate,
    val to: LocalDate,
    val maxDaysPerRun: Long? = null,
    val pauseBetweenRequestsMillis: Long? = null,
)

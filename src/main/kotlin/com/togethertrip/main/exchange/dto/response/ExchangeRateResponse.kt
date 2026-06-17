package com.togethertrip.main.exchange.dto.response

import com.togethertrip.main.exchange.domain.ExchangeRate
import java.math.BigDecimal
import java.time.LocalDate

data class ExchangeRateResponse(
    val targetCurrency: String,
    val rate: BigDecimal,
    val rateDate: LocalDate,
    val source: String?,
) {
    companion object {
        fun from(exchangeRate: ExchangeRate): ExchangeRateResponse {
            return ExchangeRateResponse(
                targetCurrency = exchangeRate.targetCurrency,
                rate = exchangeRate.rate,
                rateDate = exchangeRate.rateDate,
                source = exchangeRate.source,
            )
        }
    }
}

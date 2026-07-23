package com.togethertrip.main.trip.dto.response

import com.togethertrip.main.trip.domain.TripExchangeRate
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class TripExchangeRateResponse(
    val id: Long,
    val tripId: Long,
    val baseCurrency: String,
    val targetCurrency: String,
    val rate: BigDecimal,
    val rateDate: LocalDate?,
    val source: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(exchangeRate: TripExchangeRate): TripExchangeRateResponse {
            return TripExchangeRateResponse(
                id = exchangeRate.id,
                tripId = exchangeRate.trip.id,
                baseCurrency = exchangeRate.baseCurrency,
                targetCurrency = exchangeRate.targetCurrency,
                rate = exchangeRate.rate,
                rateDate = exchangeRate.rateDate,
                source = exchangeRate.source,
                createdAt = exchangeRate.createdAt,
                updatedAt = exchangeRate.updatedAt,
            )
        }
    }
}

package com.togethertrip.main.trip.repository

import com.togethertrip.main.trip.domain.TripExchangeRate
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface TripExchangeRateRepository : JpaRepository<TripExchangeRate, Long> {

    fun findByTripIdAndBaseCurrencyAndRateDateAndDeletedAtIsNull(
        tripId: Long,
        baseCurrency: String,
        rateDate: LocalDate,
    ): List<TripExchangeRate>

    fun findByTripIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndDeletedAtIsNull(
        tripId: Long,
        baseCurrency: String,
        targetCurrency: String,
        rateDate: LocalDate,
    ): TripExchangeRate?

    fun findByIdAndTripIdAndBaseCurrencyAndRateDateAndDeletedAtIsNull(
        id: Long,
        tripId: Long,
        baseCurrency: String,
        rateDate: LocalDate,
    ): TripExchangeRate?
}

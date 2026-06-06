package com.togethertrip.main.trip.repository

import com.togethertrip.main.trip.domain.TripExchangeRate
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface TripExchangeRateRepository : JpaRepository<TripExchangeRate, Long> {

    fun findByTripIdAndDeletedAtIsNullOrderByTargetCurrencyAsc(tripId: Long): List<TripExchangeRate>

    fun findByTripIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndDeletedAtIsNull(
        tripId: Long,
        baseCurrency: String,
        targetCurrency: String,
        rateDate: LocalDate,
    ): TripExchangeRate?

    fun findByIdAndTripIdAndDeletedAtIsNull(
        id: Long,
        tripId: Long,
    ): TripExchangeRate?
}

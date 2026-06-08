package com.togethertrip.main.trip.repository

import com.togethertrip.main.trip.domain.ExchangeRate
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface ExchangeRateRepository : JpaRepository<ExchangeRate, Long> {

    fun findFirstByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualAndDeletedAtIsNullOrderByRateDateDesc(
        baseCurrency: String,
        targetCurrency: String,
        rateDate: LocalDate,
    ): ExchangeRate?
}

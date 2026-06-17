package com.togethertrip.main.exchange.repository

import com.togethertrip.main.exchange.domain.ExchangeRate
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface ExchangeRateRepository : JpaRepository<ExchangeRate, Long> {

    fun findFirstByBaseCurrencyAndTargetCurrencyAndRateDateLessThanEqualAndDeletedAtIsNullOrderByRateDateDesc(
        baseCurrency: String,
        targetCurrency: String,
        rateDate: LocalDate,
    ): ExchangeRate?

    fun findByBaseCurrencyAndTargetCurrencyInAndRateDateAndDeletedAtIsNullOrderByTargetCurrencyAsc(
        baseCurrency: String,
        targetCurrencies: Collection<String>,
        rateDate: LocalDate,
    ): List<ExchangeRate>

    fun findByBaseCurrencyAndTargetCurrencyInAndRateDateBetweenAndDeletedAtIsNullOrderByTargetCurrencyAscRateDateDesc(
        baseCurrency: String,
        targetCurrencies: Collection<String>,
        from: LocalDate,
        to: LocalDate,
    ): List<ExchangeRate>
}

package com.togethertrip.main.transaction.domain.exchange

import java.math.BigDecimal
import java.time.LocalDate

data class TransactionExchangeRatePreview(
    val currency: String,
    val baseCurrency: String,
    val rate: BigDecimal,
    val rateDate: LocalDate,
    val source: String?,
) {
    fun toCurrencySnapshot(): TransactionCurrencySnapshot {
        return TransactionCurrencySnapshot.of(
            currency = currency,
            baseCurrency = baseCurrency,
            exchangeRate = rate,
        )
    }
}

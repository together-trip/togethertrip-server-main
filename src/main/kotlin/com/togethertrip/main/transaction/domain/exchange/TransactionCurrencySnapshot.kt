package com.togethertrip.main.transaction.domain.exchange

import java.math.BigDecimal
import java.math.RoundingMode

data class TransactionCurrencySnapshot(
    val currency: String,
    val baseCurrency: String,
    val exchangeRate: BigDecimal,
) {

    fun convert(amount: BigDecimal): BigDecimal {
        return amount.multiply(exchangeRate).setScale(BASE_AMOUNT_SCALE, BASE_AMOUNT_ROUNDING)
    }

    companion object {
        private const val BASE_AMOUNT_SCALE = 2
        private val BASE_AMOUNT_ROUNDING = RoundingMode.HALF_UP

        fun of(
            currency: String,
            baseCurrency: String,
            exchangeRate: BigDecimal,
        ): TransactionCurrencySnapshot {
            return TransactionCurrencySnapshot(
                currency = currency.trim().uppercase(),
                baseCurrency = baseCurrency.trim().uppercase(),
                exchangeRate = exchangeRate,
            )
        }
    }
}

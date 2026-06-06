package com.togethertrip.main.transaction.domain

import java.math.BigDecimal

data class TransactionCurrencySnapshot(
    val currency: String,
    val baseCurrency: String,
    val exchangeRate: BigDecimal,
)

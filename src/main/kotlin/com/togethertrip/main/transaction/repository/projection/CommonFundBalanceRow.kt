package com.togethertrip.main.transaction.repository.projection

import java.math.BigDecimal

data class CommonFundBalanceRow(
    val baseCurrency: String?,
    val chargedBaseAmount: BigDecimal,
    val usedBaseAmount: BigDecimal,
)

package com.togethertrip.main.transaction.dto.response

import java.math.BigDecimal

data class CommonFundBalanceResponse(
    val tripId: Long,
    val baseCurrency: String,
    val chargedBaseAmount: BigDecimal,
    val usedBaseAmount: BigDecimal,
    val balanceBaseAmount: BigDecimal,
)

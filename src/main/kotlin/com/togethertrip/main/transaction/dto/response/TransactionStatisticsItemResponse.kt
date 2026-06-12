package com.togethertrip.main.transaction.dto.response

import java.math.BigDecimal

data class TransactionStatisticsItemResponse(
    val key: String,
    val label: String,
    val transactionCount: Long,
    val totalBaseAmount: BigDecimal,
)

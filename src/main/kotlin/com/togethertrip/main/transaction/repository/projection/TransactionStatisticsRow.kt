package com.togethertrip.main.transaction.repository.projection

import java.math.BigDecimal

data class TransactionStatisticsRow(
    val key: String,
    val label: String,
    val transactionCount: Long,
    val totalBaseAmount: BigDecimal,
)

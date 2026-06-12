package com.togethertrip.main.transaction.dto.response

import java.math.BigDecimal
import java.time.LocalDate

data class TransactionStatisticsResponse(
    val tripId: Long,
    val groupBy: String,
    val from: LocalDate?,
    val to: LocalDate?,
    val totalBaseAmount: BigDecimal,
    val items: List<TransactionStatisticsItemResponse>,
)

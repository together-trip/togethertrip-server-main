package com.togethertrip.main.triprecap.service.ai

import java.math.BigDecimal
import java.time.Instant

data class TripRecapExpenseSignal(
    val category: String?,
    val amount: BigDecimal,
    val currency: String,
    val occurredAt: Instant?,
)

package com.togethertrip.main.settlement.domain.calculation

import java.math.BigDecimal

data class SettlementPaymentInput(
    val participantId: Long,
    val amount: BigDecimal,
)

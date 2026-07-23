package com.togethertrip.main.settlement.domain.calculation

import java.math.BigDecimal

data class SettlementShareInput(
    val participantId: Long,
    val amount: BigDecimal,
)

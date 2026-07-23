package com.togethertrip.main.settlement.repository.projection

import java.math.BigDecimal

data class SettlementShareRow(
    val participantId: Long,
    val amount: BigDecimal,
)

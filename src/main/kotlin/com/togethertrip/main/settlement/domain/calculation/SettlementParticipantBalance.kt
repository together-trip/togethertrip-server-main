package com.togethertrip.main.settlement.domain.calculation

import java.math.BigDecimal

data class SettlementParticipantBalance(
    val participantId: Long,
    val paidAmount: BigDecimal,
    val shareAmount: BigDecimal,
    val netAmount: BigDecimal,
)

package com.togethertrip.main.settlement.domain.calculation

import java.math.BigDecimal

data class SettlementTransferPlan(
    val senderParticipantId: Long,
    val receiverParticipantId: Long,
    val amount: BigDecimal,
)

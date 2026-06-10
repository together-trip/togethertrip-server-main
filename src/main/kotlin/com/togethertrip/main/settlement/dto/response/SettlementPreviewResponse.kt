package com.togethertrip.main.settlement.dto.response

import java.math.BigDecimal

data class SettlementPreviewResponse(
    val tripId: Long,
    val tripExpenseVersion: Long,
    val baseCurrency: String,
    val totalExpenseAmount: BigDecimal,
    val totalShareAmount: BigDecimal,
    val balances: List<SettlementParticipantBalanceResponse>,
    val transfers: List<SettlementTransferResponse>,
)

package com.togethertrip.main.settlement.dto.response

data class BalanceSummaryResponse(
    val tripId: Long,
    val tripExpenseVersion: Long,
    val baseCurrency: String,
    val balances: List<SettlementParticipantBalanceResponse>,
)

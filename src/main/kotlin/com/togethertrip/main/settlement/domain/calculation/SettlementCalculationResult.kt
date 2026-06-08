package com.togethertrip.main.settlement.domain.calculation

import java.math.BigDecimal

data class SettlementCalculationResult(
    val baseCurrency: String,
    val totalExpenseAmount: BigDecimal,
    val totalShareAmount: BigDecimal,
    val balances: List<SettlementParticipantBalance>,
    val transfers: List<SettlementTransferPlan>,
)

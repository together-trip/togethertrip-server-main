package com.togethertrip.main.settlement.domain.calculation

data class SettlementCalculationInput(
    val baseCurrency: String,
    val payments: List<SettlementPaymentInput>,
    val shares: List<SettlementShareInput>,
)

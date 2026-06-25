package com.togethertrip.main.settlement.repository.projection

interface SettlementTransferCompletionSummary {
    val tripId: Long
    val totalCount: Long
    val incompleteCount: Long
}

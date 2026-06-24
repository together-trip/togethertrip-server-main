package com.togethertrip.main.global.outbox.payload.settlement

import java.math.BigDecimal

data class SettlementTransferSummaryPayload(
    val sendCount: Int,
    val totalSendAmount: BigDecimal,
    val currency: String,
    val items: List<SettlementTransferSummaryItemPayload>,
)

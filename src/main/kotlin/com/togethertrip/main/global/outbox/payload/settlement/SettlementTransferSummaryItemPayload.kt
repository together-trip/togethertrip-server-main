package com.togethertrip.main.global.outbox.payload.settlement

import java.math.BigDecimal

data class SettlementTransferSummaryItemPayload(
    val settlementTransferId: Long,
    val receiverParticipantId: Long,
    val receiverParticipantDisplayName: String,
    val amount: BigDecimal,
)

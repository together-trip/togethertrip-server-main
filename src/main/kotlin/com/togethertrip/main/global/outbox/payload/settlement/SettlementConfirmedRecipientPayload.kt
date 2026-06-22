package com.togethertrip.main.global.outbox.payload.settlement

import com.togethertrip.main.global.outbox.payload.common.OutboxRecipientPayload

data class SettlementConfirmedRecipientPayload(
    override val userId: Long,
    val transferSummary: SettlementTransferSummaryPayload?,
) : OutboxRecipientPayload

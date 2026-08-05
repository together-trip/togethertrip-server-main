package com.togethertrip.main.settlement.repository

import com.togethertrip.main.settlement.domain.SettlementTransferStatus

data class SettlementTransferSearchCondition(
    val tripId: Long,
    val settlementId: Long?,
    val participantId: Long?,
    val status: SettlementTransferStatus?,
)

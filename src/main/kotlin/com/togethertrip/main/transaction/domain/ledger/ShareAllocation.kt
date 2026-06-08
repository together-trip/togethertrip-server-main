package com.togethertrip.main.transaction.domain.ledger

import java.math.BigDecimal

data class ShareAllocation(
    val participantId: Long,
    val shareAmount: BigDecimal,
    val shareRatio: BigDecimal? = null,
)

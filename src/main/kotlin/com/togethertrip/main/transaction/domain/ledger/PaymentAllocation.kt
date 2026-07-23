package com.togethertrip.main.transaction.domain.ledger

import java.math.BigDecimal

data class PaymentAllocation(
    val participantId: Long,
    val amount: BigDecimal,
)

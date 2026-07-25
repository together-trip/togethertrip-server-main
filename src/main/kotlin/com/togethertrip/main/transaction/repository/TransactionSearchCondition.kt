package com.togethertrip.main.transaction.repository

import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.transaction.domain.TransactionType
import java.time.Instant

data class TransactionSearchCondition(
    val tripId: Long,
    val status: TransactionStatus,
    val transactionType: TransactionType?,
    val participantId: Long?,
    val cursorCreatedAt: Instant?,
    val cursorId: Long?,
) {
    init {
        require((cursorCreatedAt == null) == (cursorId == null)) {
            "cursorCreatedAt and cursorId must be provided together"
        }
    }
}

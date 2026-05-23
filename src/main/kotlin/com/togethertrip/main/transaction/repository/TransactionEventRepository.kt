package com.togethertrip.main.transaction.repository

import com.togethertrip.main.transaction.domain.TransactionEvent
import org.springframework.data.jpa.repository.JpaRepository

interface TransactionEventRepository : JpaRepository<TransactionEvent, Long> {

    fun findByTransactionIdOrderByAggregateVersionAsc(transactionId: Long): List<TransactionEvent>

    fun findByTripIdOrderByCreatedAtDesc(tripId: Long): List<TransactionEvent>
}

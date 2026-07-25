package com.togethertrip.main.transaction.repository

import com.togethertrip.main.transaction.domain.Transaction
import org.springframework.data.domain.Pageable

interface TransactionQueryRepository {
    fun findTransactions(
        condition: TransactionSearchCondition,
        pageable: Pageable,
    ): List<Transaction>
}

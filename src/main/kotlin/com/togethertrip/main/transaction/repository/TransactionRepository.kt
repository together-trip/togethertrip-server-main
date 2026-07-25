package com.togethertrip.main.transaction.repository

import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TransactionRepository : JpaRepository<Transaction, Long>, TransactionQueryRepository {
    fun findByIdAndDeletedAtIsNull(id: Long): Transaction?

    @Query(
        """
        select tx
        from Transaction tx
        where tx.trip.id = :tripId
          and tx.deletedAt is null
          and tx.status = :status
        order by coalesce(tx.occurredAt, tx.createdAt) asc, tx.id asc
        """
    )
    fun findTripRecapExpenseSignals(
        @Param("tripId") tripId: Long,
        @Param("status") status: TransactionStatus = TransactionStatus.ACTIVE,
        pageable: Pageable,
    ): List<Transaction>
}

package com.togethertrip.main.transaction.repository

import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.transaction.domain.TransactionType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface TransactionRepository : JpaRepository<Transaction, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Transaction?

    @Query(
        """
        select tx
        from Transaction tx
        where tx.deletedAt is null
          and tx.trip.id = :tripId
          and tx.status = :status
          and (:transactionTypeFilterEnabled = false or tx.transactionType = :transactionType)
          and (
            :cursorFilterEnabled = false
            or tx.createdAt < :cursorCreatedAt
            or (tx.createdAt = :cursorCreatedAt and tx.id < :cursorId)
          )
          and (
            :participantFilterEnabled = false
            or exists (
              select 1
              from TransactionPayment payment
              where payment.transaction = tx
                and payment.deletedAt is null
                and payment.tripParticipant.id = :participantId
            )
            or exists (
              select 1
              from TransactionShare share
              where share.transaction = tx
                and share.deletedAt is null
                and share.tripParticipant.id = :participantId
            )
          )
        order by tx.createdAt desc, tx.id desc
        """
    )
    fun findTransactions(
        @Param("tripId") tripId: Long,
        @Param("status") status: TransactionStatus,
        @Param("transactionType") transactionType: TransactionType?,
        @Param("transactionTypeFilterEnabled") transactionTypeFilterEnabled: Boolean,
        @Param("participantId") participantId: Long?,
        @Param("participantFilterEnabled") participantFilterEnabled: Boolean,
        @Param("cursorCreatedAt") cursorCreatedAt: Instant?,
        @Param("cursorId") cursorId: Long?,
        @Param("cursorFilterEnabled") cursorFilterEnabled: Boolean,
        pageable: Pageable,
    ): List<Transaction>
}

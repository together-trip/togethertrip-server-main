package com.togethertrip.main.transaction.repository

import com.togethertrip.main.transaction.domain.TransactionShare
import com.togethertrip.main.transaction.domain.TransactionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TransactionShareRepository : JpaRepository<TransactionShare, Long> {

    fun findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(transactionId: Long): List<TransactionShare>

    @Query(
        """
        select share
        from TransactionShare share
        join fetch share.tripParticipant participant
        join fetch share.transaction transaction
        where share.deletedAt is null
          and transaction.deletedAt is null
          and transaction.trip.id = :tripId
          and transaction.status = :status
        order by share.id asc
        """
    )
    fun findSettlementShares(
        @Param("tripId") tripId: Long,
        @Param("status") status: TransactionStatus = TransactionStatus.ACTIVE,
    ): List<TransactionShare>
}

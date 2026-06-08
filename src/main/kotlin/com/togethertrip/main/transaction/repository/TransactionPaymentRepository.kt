package com.togethertrip.main.transaction.repository

import com.togethertrip.main.transaction.domain.TransactionPayment
import com.togethertrip.main.transaction.domain.TransactionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TransactionPaymentRepository : JpaRepository<TransactionPayment, Long> {

    fun findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(transactionId: Long): List<TransactionPayment>

    @Query(
        """
        select payment
        from TransactionPayment payment
        join fetch payment.tripParticipant participant
        join fetch payment.transaction transaction
        where payment.deletedAt is null
          and transaction.deletedAt is null
          and transaction.trip.id = :tripId
          and transaction.status = :status
        order by payment.id asc
        """
    )
    fun findSettlementPayments(
        @Param("tripId") tripId: Long,
        @Param("status") status: TransactionStatus = TransactionStatus.ACTIVE,
    ): List<TransactionPayment>
}

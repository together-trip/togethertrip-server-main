package com.togethertrip.main.settlement.repository

import com.togethertrip.main.settlement.repository.projection.SettlementPaymentRow
import com.togethertrip.main.settlement.repository.projection.SettlementShareRow
import com.togethertrip.main.transaction.domain.TransactionStatus
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class SettlementTransactionQueryRepository(
    private val entityManager: EntityManager,
) {

    fun findSettlementPaymentRows(
        tripId: Long,
        status: TransactionStatus = TransactionStatus.ACTIVE,
    ): List<SettlementPaymentRow> {
        return entityManager.createNativeQuery(
            """
            select payment.trip_participant_id,
                   sum(payment.base_amount) as amount
            from transaction_payments payment
            join transactions transaction on transaction.id = payment.transaction_id
            where payment.deleted_at is null
              and transaction.deleted_at is null
              and transaction.trip_id = :tripId
              and transaction.status = :status
            group by payment.trip_participant_id
            order by payment.trip_participant_id asc
            """.trimIndent()
        )
            .setParameter("tripId", tripId)
            .setParameter("status", status.name)
            .resultList
            .map { row ->
                val values = row as Array<*>
                SettlementPaymentRow(
                    participantId = (values[0] as Number).toLong(),
                    amount = values[1] as BigDecimal,
                )
            }
    }

    fun findSettlementShareRows(
        tripId: Long,
        status: TransactionStatus = TransactionStatus.ACTIVE,
    ): List<SettlementShareRow> {
        return entityManager.createNativeQuery(
            """
            select share.trip_participant_id,
                   sum(share.base_share_amount) as amount
            from transaction_shares share
            join transactions transaction on transaction.id = share.transaction_id
            where share.deleted_at is null
              and transaction.deleted_at is null
              and transaction.trip_id = :tripId
              and transaction.status = :status
            group by share.trip_participant_id
            order by share.trip_participant_id asc
            """.trimIndent()
        )
            .setParameter("tripId", tripId)
            .setParameter("status", status.name)
            .resultList
            .map { row ->
                val values = row as Array<*>
                SettlementShareRow(
                    participantId = (values[0] as Number).toLong(),
                    amount = values[1] as BigDecimal,
                )
            }
    }
}

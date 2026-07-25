package com.togethertrip.main.settlement.repository

import com.togethertrip.main.settlement.domain.SettlementTransferRow
import jakarta.persistence.EntityManager
import java.math.BigDecimal
import java.time.Instant

class SettlementTransferQueryRepositoryImpl(
    private val entityManager: EntityManager,
) : SettlementTransferQueryRepository {
    override fun findTransferRows(condition: SettlementTransferSearchCondition): List<SettlementTransferRow> {
        val sql = buildString {
            append(BASE_QUERY)
            if (condition.settlementId != null) append(" and settlement.id = :settlementId")
            if (condition.participantId != null) {
                append(" and (transfer.sender_participant_id = :participantId")
                append(" or transfer.receiver_participant_id = :participantId)")
            }
            if (condition.status != null) append(" and transfer.status = :status")
            append(" order by transfer.id asc")
        }
        val query = entityManager.createNativeQuery(sql)
            .setParameter("tripId", condition.tripId)
        condition.settlementId?.let { query.setParameter("settlementId", it) }
        condition.participantId?.let { query.setParameter("participantId", it) }
        condition.status?.let { query.setParameter("status", it.name) }

        return query.resultList.map { raw -> toRow(raw as Array<*>) }
    }

    private fun toRow(row: Array<*>): SettlementTransferRow = SettlementTransferRowData(
        id = (row[0] as Number).toLong(),
        settlementId = (row[1] as Number).toLong(),
        tripName = row[2] as String,
        senderParticipantId = (row[3] as Number).toLong(),
        senderUserId = (row[4] as? Number)?.toLong(),
        senderDisplayName = row[5] as String,
        senderUserStatus = row[6] as String?,
        receiverParticipantId = (row[7] as Number).toLong(),
        receiverUserId = (row[8] as? Number)?.toLong(),
        receiverDisplayName = row[9] as String,
        receiverUserStatus = row[10] as String?,
        amount = row[11] as BigDecimal,
        currency = row[12] as String,
        status = row[13].toString(),
        senderConfirmedAt = row[14] as Instant?,
        receiverConfirmedAt = row[15] as Instant?,
        completedAt = row[16] as Instant?,
        autoConfirmed = row[17] as Boolean,
    )

    private companion object {
        val BASE_QUERY = """
            select transfer.id,
                   settlement.id,
                   trip.title,
                   transfer.sender_participant_id,
                   sender.user_id,
                   sender.display_name,
                   sender_user.status,
                   transfer.receiver_participant_id,
                   receiver.user_id,
                   receiver.display_name,
                   receiver_user.status,
                   transfer.amount,
                   transfer.currency,
                   transfer.status,
                   transfer.sender_confirmed_at,
                   transfer.receiver_confirmed_at,
                   transfer.completed_at,
                   transfer.auto_confirmed
            from settlement_transfers transfer
            join settlements settlement on settlement.id = transfer.settlement_id
            join trips trip on trip.id = settlement.trip_id
            join trip_participants sender on sender.id = transfer.sender_participant_id
            join trip_participants receiver on receiver.id = transfer.receiver_participant_id
            left join users sender_user on sender_user.id = sender.user_id
            left join users receiver_user on receiver_user.id = receiver.user_id
            where transfer.deleted_at is null
              and settlement.deleted_at is null
              and settlement.trip_id = :tripId
        """.trimIndent()
    }
}

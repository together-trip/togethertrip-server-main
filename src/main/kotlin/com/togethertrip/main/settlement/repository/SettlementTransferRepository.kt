package com.togethertrip.main.settlement.repository

import com.togethertrip.main.settlement.domain.SettlementTransfer
import com.togethertrip.main.settlement.domain.SettlementTransferRow
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SettlementTransferRepository : JpaRepository<SettlementTransfer, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): SettlementTransfer?

    @Query(
        value = """
        select transfer.id as "id",
               transfer.sender_participant_id as "senderParticipantId",
               sender.display_name as "senderDisplayName",
               sender_user.status as "senderUserStatus",
               transfer.receiver_participant_id as "receiverParticipantId",
               receiver.display_name as "receiverDisplayName",
               receiver_user.status as "receiverUserStatus",
               transfer.amount as "amount",
               transfer.currency as "currency",
               transfer.status as "status",
               transfer.sender_confirmed_at as "senderConfirmedAt",
               transfer.receiver_confirmed_at as "receiverConfirmedAt",
               transfer.completed_at as "completedAt"
        from settlement_transfers transfer
        join trip_participants sender on sender.id = transfer.sender_participant_id
        join trip_participants receiver on receiver.id = transfer.receiver_participant_id
        left join users sender_user on sender_user.id = sender.user_id
        left join users receiver_user on receiver_user.id = receiver.user_id
        where transfer.deleted_at is null
          and transfer.settlement_id = :settlementId
        order by transfer.id asc
        """,
        nativeQuery = true
    )
    fun findTransferRowsBySettlementId(
        @Param("settlementId") settlementId: Long,
    ): List<SettlementTransferRow>

    @Query(
        value = """
        select transfer.id as "id",
               transfer.sender_participant_id as "senderParticipantId",
               sender.display_name as "senderDisplayName",
               sender_user.status as "senderUserStatus",
               transfer.receiver_participant_id as "receiverParticipantId",
               receiver.display_name as "receiverDisplayName",
               receiver_user.status as "receiverUserStatus",
               transfer.amount as "amount",
               transfer.currency as "currency",
               transfer.status as "status",
               transfer.sender_confirmed_at as "senderConfirmedAt",
               transfer.receiver_confirmed_at as "receiverConfirmedAt",
               transfer.completed_at as "completedAt"
        from settlement_transfers transfer
        join settlements settlement on settlement.id = transfer.settlement_id
        join trip_participants sender on sender.id = transfer.sender_participant_id
        join trip_participants receiver on receiver.id = transfer.receiver_participant_id
        left join users sender_user on sender_user.id = sender.user_id
        left join users receiver_user on receiver_user.id = receiver.user_id
        where transfer.deleted_at is null
          and settlement.deleted_at is null
          and settlement.trip_id = :tripId
          and (:settlementFilterEnabled = false or settlement.id = :settlementId)
          and (
            :participantFilterEnabled = false
            or transfer.sender_participant_id = :participantId
            or transfer.receiver_participant_id = :participantId
          )
          and (:statusFilterEnabled = false or transfer.status = :status)
        order by transfer.id asc
        """,
        nativeQuery = true
    )
    fun findTransferRows(
        @Param("tripId") tripId: Long,
        @Param("settlementFilterEnabled") settlementFilterEnabled: Boolean,
        @Param("settlementId") settlementId: Long,
        @Param("participantFilterEnabled") participantFilterEnabled: Boolean,
        @Param("participantId") participantId: Long,
        @Param("statusFilterEnabled") statusFilterEnabled: Boolean,
        @Param("status") status: String,
    ): List<SettlementTransferRow>

    @Query(
        value = """
        select transfer.id as "id",
               transfer.sender_participant_id as "senderParticipantId",
               sender.display_name as "senderDisplayName",
               sender_user.status as "senderUserStatus",
               transfer.receiver_participant_id as "receiverParticipantId",
               receiver.display_name as "receiverDisplayName",
               receiver_user.status as "receiverUserStatus",
               transfer.amount as "amount",
               transfer.currency as "currency",
               transfer.status as "status",
               transfer.sender_confirmed_at as "senderConfirmedAt",
               transfer.receiver_confirmed_at as "receiverConfirmedAt",
               transfer.completed_at as "completedAt"
        from settlement_transfers transfer
        join trip_participants sender on sender.id = transfer.sender_participant_id
        join trip_participants receiver on receiver.id = transfer.receiver_participant_id
        left join users sender_user on sender_user.id = sender.user_id
        left join users receiver_user on receiver_user.id = receiver.user_id
        where transfer.deleted_at is null
          and transfer.id = :transferId
        """,
        nativeQuery = true
    )
    fun findTransferRowById(
        @Param("transferId") transferId: Long,
    ): SettlementTransferRow?

}

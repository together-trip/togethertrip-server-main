package com.togethertrip.main.settlement.repository

import com.togethertrip.main.settlement.domain.SettlementTransfer
import com.togethertrip.main.settlement.domain.SettlementTransferRow
import com.togethertrip.main.settlement.repository.projection.SettlementTransferCompletionSummary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface SettlementTransferRepository : JpaRepository<SettlementTransfer, Long>, SettlementTransferQueryRepository {

    fun findByIdAndDeletedAtIsNull(id: Long): SettlementTransfer?

    @Query(
        value = """
        select settlement.trip_id as "tripId",
               count(transfer.id) as "totalCount",
               sum(case when transfer.status = 'COMPLETED' then 0 else 1 end) as "incompleteCount"
        from settlements settlement
        join settlement_transfers transfer on transfer.settlement_id = settlement.id
        where settlement.deleted_at is null
          and transfer.deleted_at is null
          and settlement.status = 'CONFIRMED'
          and settlement.trip_id in (:tripIds)
        group by settlement.trip_id
        """,
        nativeQuery = true
    )
    fun findCompletionSummariesByTripIds(
        @Param("tripIds") tripIds: Collection<Long>,
    ): List<SettlementTransferCompletionSummary>

    @Query(
        value = """
        select settlement.trip_id as "tripId",
               count(transfer.id) as "totalCount",
               sum(case when transfer.status = 'COMPLETED' then 0 else 1 end) as "incompleteCount"
        from settlements settlement
        join settlement_transfers transfer on transfer.settlement_id = settlement.id
        where settlement.deleted_at is null
          and transfer.deleted_at is null
          and settlement.status = 'CONFIRMED'
          and settlement.trip_id = :tripId
        group by settlement.trip_id
        """,
        nativeQuery = true
    )
    fun findCompletionSummaryByTripId(
        @Param("tripId") tripId: Long,
    ): SettlementTransferCompletionSummary?

    @Query(
        value = """
        select transfer.id as "id",
               settlement.id as "settlementId",
               trip.title as "tripName",
               transfer.sender_participant_id as "senderParticipantId",
               sender.user_id as "senderUserId",
               sender.display_name as "senderDisplayName",
               sender_user.status as "senderUserStatus",
               transfer.receiver_participant_id as "receiverParticipantId",
               receiver.user_id as "receiverUserId",
               receiver.display_name as "receiverDisplayName",
               receiver_user.status as "receiverUserStatus",
               transfer.amount as "amount",
               transfer.currency as "currency",
               transfer.status as "status",
               transfer.sender_confirmed_at as "senderConfirmedAt",
               transfer.receiver_confirmed_at as "receiverConfirmedAt",
               transfer.completed_at as "completedAt",
               transfer.auto_confirmed as "autoConfirmed"
        from settlement_transfers transfer
        join settlements settlement on settlement.id = transfer.settlement_id
        join trips trip on trip.id = settlement.trip_id
        join trip_participants sender on sender.id = transfer.sender_participant_id
        join trip_participants receiver on receiver.id = transfer.receiver_participant_id
        left join users sender_user on sender_user.id = sender.user_id
        left join users receiver_user on receiver_user.id = receiver.user_id
        where transfer.deleted_at is null
          and settlement.deleted_at is null
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
               settlement.id as "settlementId",
               trip.title as "tripName",
               transfer.sender_participant_id as "senderParticipantId",
               sender.user_id as "senderUserId",
               sender.display_name as "senderDisplayName",
               sender_user.status as "senderUserStatus",
               transfer.receiver_participant_id as "receiverParticipantId",
               receiver.user_id as "receiverUserId",
               receiver.display_name as "receiverDisplayName",
               receiver_user.status as "receiverUserStatus",
               transfer.amount as "amount",
               transfer.currency as "currency",
               transfer.status as "status",
               transfer.sender_confirmed_at as "senderConfirmedAt",
               transfer.receiver_confirmed_at as "receiverConfirmedAt",
               transfer.completed_at as "completedAt",
               transfer.auto_confirmed as "autoConfirmed"
        from settlement_transfers transfer
        join settlements settlement on settlement.id = transfer.settlement_id
        join trips trip on trip.id = settlement.trip_id
        join trip_participants sender on sender.id = transfer.sender_participant_id
        join trip_participants receiver on receiver.id = transfer.receiver_participant_id
        left join users sender_user on sender_user.id = sender.user_id
        left join users receiver_user on receiver_user.id = receiver.user_id
        where transfer.deleted_at is null
          and settlement.deleted_at is null
          and transfer.id = :transferId
        """,
        nativeQuery = true
    )
    fun findTransferRowById(
        @Param("transferId") transferId: Long,
    ): SettlementTransferRow?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
        update settlement_transfers transfer
        set sender_confirmed_at = :confirmedAt,
            status = case
                when transfer.receiver_confirmed_at is not null then 'COMPLETED'
                else 'SENDER_CONFIRMED'
            end,
            completed_at = case
                when transfer.receiver_confirmed_at is not null then coalesce(transfer.completed_at, :confirmedAt)
                else transfer.completed_at
            end,
            updated_at = :confirmedAt
        from settlements settlement
        where settlement.id = transfer.settlement_id
          and settlement.deleted_at is null
          and settlement.trip_id = :tripId
          and settlement.status = 'CONFIRMED'
          and transfer.deleted_at is null
          and transfer.id = :transferId
          and transfer.sender_participant_id = :participantId
          and transfer.sender_confirmed_at is null
          and transfer.status <> 'CANCELLED'
        """,
        nativeQuery = true
    )
    fun confirmAsSenderIfNeeded(
        @Param("transferId") transferId: Long,
        @Param("tripId") tripId: Long,
        @Param("participantId") participantId: Long,
        @Param("confirmedAt") confirmedAt: Instant,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
        update settlement_transfers transfer
        set receiver_confirmed_at = :confirmedAt,
            status = case
                when transfer.sender_confirmed_at is not null then 'COMPLETED'
                else 'RECEIVER_CONFIRMED'
            end,
            completed_at = case
                when transfer.sender_confirmed_at is not null then coalesce(transfer.completed_at, :confirmedAt)
                else transfer.completed_at
            end,
            updated_at = :confirmedAt
        from settlements settlement
        where settlement.id = transfer.settlement_id
          and settlement.deleted_at is null
          and settlement.trip_id = :tripId
          and settlement.status = 'CONFIRMED'
          and transfer.deleted_at is null
          and transfer.id = :transferId
          and transfer.receiver_participant_id = :participantId
          and transfer.receiver_confirmed_at is null
          and transfer.status <> 'CANCELLED'
        """,
        nativeQuery = true
    )
    fun confirmAsReceiverIfNeeded(
        @Param("transferId") transferId: Long,
        @Param("tripId") tripId: Long,
        @Param("participantId") participantId: Long,
        @Param("confirmedAt") confirmedAt: Instant,
    ): Int

}

package com.togethertrip.main.settlement.repository

import com.togethertrip.main.settlement.domain.SettlementTransfer
import com.togethertrip.main.settlement.domain.SettlementTransferStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SettlementTransferRepository : JpaRepository<SettlementTransfer, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): SettlementTransfer?

    fun findBySettlementIdAndDeletedAtIsNullOrderByIdAsc(settlementId: Long): List<SettlementTransfer>

    @Query(
        """
        select transfer
        from SettlementTransfer transfer
        join fetch transfer.settlement settlement
        join fetch transfer.sender sender
        join fetch transfer.receiver receiver
        where transfer.deletedAt is null
          and settlement.deletedAt is null
          and settlement.trip.id = :tripId
          and (:settlementId is null or settlement.id = :settlementId)
          and (:participantId is null or sender.id = :participantId or receiver.id = :participantId)
          and (:status is null or transfer.status = :status)
        order by transfer.id asc
        """
    )
    fun findTransfers(
        @Param("tripId") tripId: Long,
        @Param("settlementId") settlementId: Long?,
        @Param("participantId") participantId: Long?,
        @Param("status") status: SettlementTransferStatus?,
    ): List<SettlementTransfer>
}

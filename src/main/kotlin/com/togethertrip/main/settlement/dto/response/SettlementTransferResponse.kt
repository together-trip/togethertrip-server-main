package com.togethertrip.main.settlement.dto.response

import com.togethertrip.main.settlement.domain.SettlementTransfer
import com.togethertrip.main.settlement.domain.SettlementTransferStatus
import com.togethertrip.main.settlement.domain.calculation.SettlementTransferPlan
import com.togethertrip.main.trip.domain.TripParticipant
import java.math.BigDecimal
import java.time.Instant

data class SettlementTransferResponse(
    val id: Long?,
    val senderParticipantId: Long,
    val senderDisplayName: String,
    val receiverParticipantId: Long,
    val receiverDisplayName: String,
    val amount: BigDecimal,
    val currency: String,
    val status: SettlementTransferStatus,
    val senderConfirmedAt: Instant?,
    val receiverConfirmedAt: Instant?,
    val completedAt: Instant?,
) {
    companion object {
        fun from(
            transfer: SettlementTransfer,
        ): SettlementTransferResponse {
            return SettlementTransferResponse(
                id = transfer.id,
                senderParticipantId = transfer.sender.id,
                senderDisplayName = transfer.sender.displayName,
                receiverParticipantId = transfer.receiver.id,
                receiverDisplayName = transfer.receiver.displayName,
                amount = transfer.amount,
                currency = transfer.currency,
                status = transfer.status,
                senderConfirmedAt = transfer.senderConfirmedAt,
                receiverConfirmedAt = transfer.receiverConfirmedAt,
                completedAt = transfer.completedAt,
            )
        }

        fun from(
            plan: SettlementTransferPlan,
            sender: TripParticipant,
            receiver: TripParticipant,
            currency: String,
        ): SettlementTransferResponse {
            return SettlementTransferResponse(
                id = null,
                senderParticipantId = sender.id,
                senderDisplayName = sender.displayName,
                receiverParticipantId = receiver.id,
                receiverDisplayName = receiver.displayName,
                amount = plan.amount,
                currency = currency,
                status = SettlementTransferStatus.PENDING,
                senderConfirmedAt = null,
                receiverConfirmedAt = null,
                completedAt = null,
            )
        }
    }
}

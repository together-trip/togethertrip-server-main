package com.togethertrip.main.settlement.dto.response

import com.togethertrip.main.settlement.domain.SettlementTransferRow
import com.togethertrip.main.settlement.domain.SettlementTransferStatus
import com.togethertrip.main.settlement.domain.calculation.SettlementTransferPlan
import com.togethertrip.main.settlement.domain.snapshot.SettlementParticipantSnapshot
import com.togethertrip.main.user.domain.UserStatus
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
        fun from(row: SettlementTransferRow): SettlementTransferResponse {
            return SettlementTransferResponse(
                id = row.getId(),
                senderParticipantId = row.getSenderParticipantId(),
                senderDisplayName = displayNameFor(
                    displayName = row.getSenderDisplayName(),
                    userStatus = row.getSenderUserStatus(),
                ),
                receiverParticipantId = row.getReceiverParticipantId(),
                receiverDisplayName = displayNameFor(
                    displayName = row.getReceiverDisplayName(),
                    userStatus = row.getReceiverUserStatus(),
                ),
                amount = row.getAmount(),
                currency = row.getCurrency(),
                status = SettlementTransferStatus.valueOf(row.getStatus()),
                senderConfirmedAt = row.getSenderConfirmedAt(),
                receiverConfirmedAt = row.getReceiverConfirmedAt(),
                completedAt = row.getCompletedAt(),
            )
        }

        fun from(
            plan: SettlementTransferPlan,
            sender: SettlementParticipantSnapshot,
            receiver: SettlementParticipantSnapshot,
            currency: String,
        ): SettlementTransferResponse {
            return SettlementTransferResponse(
                id = null,
                senderParticipantId = sender.participantId,
                senderDisplayName = sender.displayName,
                receiverParticipantId = receiver.participantId,
                receiverDisplayName = receiver.displayName,
                amount = plan.amount,
                currency = currency,
                status = SettlementTransferStatus.PENDING,
                senderConfirmedAt = null,
                receiverConfirmedAt = null,
                completedAt = null,
            )
        }

        private fun displayNameFor(
            displayName: String,
            userStatus: String?,
        ): String {
            val status = userStatus?.let(UserStatus::valueOf)

            return if (status != null && status != UserStatus.ACTIVE) {
                WITHDRAWN_USER_DISPLAY_NAME
            } else {
                displayName
            }
        }

        private const val WITHDRAWN_USER_DISPLAY_NAME = "탈퇴한 사용자"
    }
}

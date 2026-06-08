package com.togethertrip.main.settlement.dto.response

import com.togethertrip.main.settlement.domain.calculation.SettlementParticipantBalance
import com.togethertrip.main.settlement.domain.snapshot.SettlementParticipantSnapshot
import com.togethertrip.main.settlement.domain.snapshot.SettlementSnapshotBalance
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantStatus
import java.math.BigDecimal

data class SettlementParticipantBalanceResponse(
    val participantId: Long,
    val userId: Long?,
    val displayName: String,
    val profileImageUrl: String?,
    val participantStatus: TripParticipantStatus,
    val paidAmount: BigDecimal,
    val shareAmount: BigDecimal,
    val netAmount: BigDecimal,
) {
    companion object {
        fun from(
            balance: SettlementParticipantBalance,
            participant: TripParticipant,
        ): SettlementParticipantBalanceResponse {
            return SettlementParticipantBalanceResponse(
                participantId = participant.id,
                userId = participant.user?.id,
                displayName = participant.displayName,
                profileImageUrl = participant.profileImageUrl,
                participantStatus = participant.participantStatus,
                paidAmount = balance.paidAmount,
                shareAmount = balance.shareAmount,
                netAmount = balance.netAmount,
            )
        }

        fun from(snapshot: SettlementSnapshotBalance): SettlementParticipantBalanceResponse {
            return SettlementParticipantBalanceResponse(
                participantId = snapshot.participantId,
                userId = snapshot.userId,
                displayName = snapshot.displayName,
                profileImageUrl = snapshot.profileImageUrl,
                participantStatus = snapshot.participantStatus,
                paidAmount = snapshot.paidAmount,
                shareAmount = snapshot.shareAmount,
                netAmount = snapshot.netAmount,
            )
        }

        fun from(
            balance: SettlementParticipantBalance,
            participant: SettlementParticipantSnapshot,
        ): SettlementParticipantBalanceResponse {
            return SettlementParticipantBalanceResponse(
                participantId = participant.participantId,
                userId = participant.userId,
                displayName = participant.displayName,
                profileImageUrl = participant.profileImageUrl,
                participantStatus = participant.participantStatus,
                paidAmount = balance.paidAmount,
                shareAmount = balance.shareAmount,
                netAmount = balance.netAmount,
            )
        }
    }
}

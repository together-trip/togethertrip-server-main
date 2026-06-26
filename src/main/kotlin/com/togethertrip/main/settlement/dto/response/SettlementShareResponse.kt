package com.togethertrip.main.settlement.dto.response

import com.togethertrip.main.settlement.domain.Settlement
import com.togethertrip.main.settlement.domain.SettlementStatus
import com.togethertrip.main.settlement.domain.SettlementTransferStatus
import com.togethertrip.main.trip.dto.response.TripSettlementDisplayStatus
import java.math.BigDecimal
import java.time.Instant

data class SettlementShareResponse(
    val status: SettlementStatus,
    val settlementDisplayStatus: TripSettlementDisplayStatus,
    val baseCurrency: String,
    val totalExpenseAmount: BigDecimal,
    val totalShareAmount: BigDecimal,
    val confirmedAt: Instant?,
    val balances: List<SettlementShareBalanceResponse>,
    val transfers: List<SettlementShareTransferResponse>,
) {
    companion object {
        fun from(
            settlement: Settlement,
            balances: List<SettlementParticipantBalanceResponse>,
            transfers: List<SettlementTransferResponse>,
        ): SettlementShareResponse {
            return SettlementShareResponse(
                status = settlement.status,
                settlementDisplayStatus = resolveDisplayStatus(transfers),
                baseCurrency = settlement.baseCurrency,
                totalExpenseAmount = settlement.totalExpenseAmount,
                totalShareAmount = settlement.totalShareAmount,
                confirmedAt = settlement.confirmedAt,
                balances = balances.map(SettlementShareBalanceResponse::from),
                transfers = transfers.map(SettlementShareTransferResponse::from),
            )
        }

        private fun resolveDisplayStatus(
            transfers: List<SettlementTransferResponse>,
        ): TripSettlementDisplayStatus {
            return if (transfers.any { it.status != SettlementTransferStatus.COMPLETED }) {
                TripSettlementDisplayStatus.IN_PROGRESS
            } else {
                TripSettlementDisplayStatus.COMPLETED
            }
        }
    }
}

data class SettlementShareBalanceResponse(
    val displayName: String,
    val paidAmount: BigDecimal,
    val shareAmount: BigDecimal,
    val netAmount: BigDecimal,
) {
    companion object {
        fun from(balance: SettlementParticipantBalanceResponse): SettlementShareBalanceResponse {
            return SettlementShareBalanceResponse(
                displayName = balance.displayName,
                paidAmount = balance.paidAmount,
                shareAmount = balance.shareAmount,
                netAmount = balance.netAmount,
            )
        }
    }
}

data class SettlementShareTransferResponse(
    val senderDisplayName: String,
    val receiverDisplayName: String,
    val amount: BigDecimal,
    val currency: String,
    val status: SettlementTransferStatus,
    val senderConfirmedAt: Instant?,
    val receiverConfirmedAt: Instant?,
    val completedAt: Instant?,
) {
    companion object {
        fun from(transfer: SettlementTransferResponse): SettlementShareTransferResponse {
            return SettlementShareTransferResponse(
                senderDisplayName = transfer.senderDisplayName,
                receiverDisplayName = transfer.receiverDisplayName,
                amount = transfer.amount,
                currency = transfer.currency,
                status = transfer.status,
                senderConfirmedAt = transfer.senderConfirmedAt,
                receiverConfirmedAt = transfer.receiverConfirmedAt,
                completedAt = transfer.completedAt,
            )
        }
    }
}

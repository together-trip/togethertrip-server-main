package com.togethertrip.main.settlement.dto.response

import com.togethertrip.main.settlement.domain.Settlement
import com.togethertrip.main.settlement.domain.SettlementStatus
import com.togethertrip.main.settlement.domain.SettlementTransferStatus
import com.togethertrip.main.trip.dto.response.TripSettlementDisplayStatus
import java.math.BigDecimal
import java.time.Instant

data class SettlementResponse(
    val id: Long,
    val tripId: Long,
    val status: SettlementStatus,
    val settlementDisplayStatus: TripSettlementDisplayStatus,
    val tripExpenseVersion: Long,
    val calculationVersion: String,
    val baseCurrency: String,
    val totalExpenseAmount: BigDecimal,
    val totalShareAmount: BigDecimal,
    val confirmedAt: Instant?,
    val confirmedByUserId: Long?,
    val balances: List<SettlementParticipantBalanceResponse>,
    val transfers: List<SettlementTransferResponse>,
) {
    companion object {
        fun from(
            settlement: Settlement,
            balances: List<SettlementParticipantBalanceResponse>,
            transfers: List<SettlementTransferResponse>,
        ): SettlementResponse {
            return SettlementResponse(
                id = settlement.id,
                tripId = settlement.trip.id,
                status = settlement.status,
                settlementDisplayStatus = resolveDisplayStatus(transfers),
                tripExpenseVersion = settlement.tripExpenseVersion,
                calculationVersion = settlement.calculationVersion,
                baseCurrency = settlement.baseCurrency,
                totalExpenseAmount = settlement.totalExpenseAmount,
                totalShareAmount = settlement.totalShareAmount,
                confirmedAt = settlement.confirmedAt,
                confirmedByUserId = settlement.confirmedBy?.id,
                balances = balances,
                transfers = transfers,
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

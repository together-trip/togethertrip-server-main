package com.togethertrip.main.settlement.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.settlement.domain.Settlement
import com.togethertrip.main.settlement.domain.SettlementStatus
import com.togethertrip.main.settlement.domain.snapshot.SettlementSnapshotPayload
import com.togethertrip.main.settlement.dto.response.SettlementParticipantBalanceResponse
import com.togethertrip.main.settlement.dto.response.SettlementShareResponse
import com.togethertrip.main.settlement.dto.response.SettlementTransferResponse
import com.togethertrip.main.settlement.exception.SettlementErrorCode
import com.togethertrip.main.settlement.repository.SettlementRepository
import com.togethertrip.main.settlement.repository.SettlementTransferRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

@Service
class SettlementShareService(
    private val settlementRepository: SettlementRepository,
    private val settlementTransferRepository: SettlementTransferRepository,
    private val objectMapper: ObjectMapper,
) {

    @Transactional(readOnly = true)
    fun getSettlementByShareToken(token: String): SettlementShareResponse {
        val settlement = settlementRepository.findByShareTokenAndDeletedAtIsNull(token)
            ?: throw BusinessException(SettlementErrorCode.SETTLEMENT_SHARE_TOKEN_NOT_FOUND)

        validateConfirmedSettlement(settlement)

        val balances = objectMapper
            .readValue(settlement.snapshotPayload, SettlementSnapshotPayload::class.java)
            .balances
            .map(SettlementParticipantBalanceResponse::from)
        val transfers = settlementTransferRepository
            .findTransferRowsBySettlementId(settlement.id)
            .map(SettlementTransferResponse::from)

        return SettlementShareResponse.from(
            settlement = settlement,
            balances = balances,
            transfers = transfers,
        )
    }

    private fun validateConfirmedSettlement(settlement: Settlement) {
        if (settlement.status != SettlementStatus.CONFIRMED) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_NOT_CONFIRMED)
        }
    }
}

package com.togethertrip.main.settlement.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.settlement.domain.SettlementTransferDirection
import com.togethertrip.main.settlement.domain.SettlementTransferRow
import com.togethertrip.main.settlement.domain.SettlementTransferStatus
import com.togethertrip.main.settlement.dto.response.SettlementTransferResponse
import com.togethertrip.main.settlement.exception.SettlementErrorCode
import com.togethertrip.main.settlement.repository.SettlementTransferRepository
import com.togethertrip.main.settlement.service.support.SettlementAccessResolver
import com.togethertrip.main.settlement.service.support.SettlementTransferConfirmationProcessor
import com.togethertrip.main.trip.domain.TripParticipant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SettlementTransferService(
    private val settlementTransferRepository: SettlementTransferRepository,
    private val settlementAccessResolver: SettlementAccessResolver,
    private val settlementTransferConfirmationProcessor: SettlementTransferConfirmationProcessor,
) {
    @Transactional(readOnly = true)
    fun getTransfers(
        userId: Long,
        tripId: Long,
        settlementId: Long?,
        participantId: Long?,
        status: String?,
        direction: String?,
    ): List<SettlementTransferResponse> {
        val currentParticipant = settlementAccessResolver.getActiveParticipant(
            userId = userId,
            tripId = tripId,
        )
        val requestedStatus = status?.let(::parseTransferStatus)
        val requestedDirection = SettlementTransferDirection.parse(direction)
        val transfers = settlementTransferRepository.findTransferRows(
            tripId = tripId,
            settlementFilterEnabled = settlementId != null,
            settlementId = settlementId ?: UNUSED_FILTER_ID,
            participantFilterEnabled = participantId != null,
            participantId = participantId ?: UNUSED_FILTER_ID,
            statusFilterEnabled = requestedStatus != null,
            status = requestedStatus?.name ?: UNUSED_FILTER_VALUE,
        )

        return transfers
            .filter { transfer -> matchesDirection(transfer, currentParticipant, requestedDirection) }
            .map(SettlementTransferResponse::from)
    }

    @Transactional
    fun confirmAsSender(
        userId: Long,
        tripId: Long,
        transferId: Long,
    ): SettlementTransferResponse {
        val participant = settlementAccessResolver.getActiveParticipant(
            userId = userId,
            tripId = tripId,
        )
        val transferRow = settlementTransferConfirmationProcessor.confirmAsSender(
            tripId = tripId,
            transferId = transferId,
            participant = participant,
        )

        return SettlementTransferResponse.from(transferRow)
    }

    @Transactional
    fun confirmAsReceiver(
        userId: Long,
        tripId: Long,
        transferId: Long,
    ): SettlementTransferResponse {
        val participant = settlementAccessResolver.getActiveParticipant(
            userId = userId,
            tripId = tripId,
        )
        val transferRow = settlementTransferConfirmationProcessor.confirmAsReceiver(
            tripId = tripId,
            transferId = transferId,
            participant = participant,
        )

        return SettlementTransferResponse.from(transferRow)
    }

    private fun matchesDirection(
        transfer: SettlementTransferRow,
        participant: TripParticipant,
        direction: SettlementTransferDirection?,
    ): Boolean {
        return when (direction) {
            null -> true
            SettlementTransferDirection.SENT -> transfer.getSenderParticipantId() == participant.id
            SettlementTransferDirection.RECEIVED -> transfer.getReceiverParticipantId() == participant.id
        }
    }

    private fun parseTransferStatus(status: String): SettlementTransferStatus {
        return try {
            SettlementTransferStatus.valueOf(status.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            throw BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_TRANSFER_STATUS)
        }
    }

    private companion object {
        private const val UNUSED_FILTER_ID = 0L
        private const val UNUSED_FILTER_VALUE = ""
    }
}

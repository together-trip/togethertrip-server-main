package com.togethertrip.main.settlement.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.settlement.domain.SettlementTransfer
import com.togethertrip.main.settlement.domain.SettlementTransferRow
import com.togethertrip.main.settlement.domain.SettlementTransferStatus
import com.togethertrip.main.settlement.dto.response.SettlementTransferResponse
import com.togethertrip.main.settlement.exception.SettlementErrorCode
import com.togethertrip.main.settlement.repository.SettlementTransferRepository
import com.togethertrip.main.settlement.service.support.SettlementTripAccessGuard
import com.togethertrip.main.trip.domain.TripParticipant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class SettlementTransferService(
    private val settlementTransferRepository: SettlementTransferRepository,
    private val settlementTripAccessGuard: SettlementTripAccessGuard,
) {
    private val clock = Clock.systemDefaultZone()

    @Transactional(readOnly = true)
    fun getTransfers(
        userId: Long,
        tripId: Long,
        settlementId: Long?,
        participantId: Long?,
        status: String?,
        direction: String?,
    ): List<SettlementTransferResponse> {
        val currentParticipant = settlementTripAccessGuard.getActiveParticipant(
            userId = userId,
            tripId = tripId,
        )
        val requestedStatus = status?.let(::parseTransferStatus)
        val requestedDirection = parseTransferDirection(direction)
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
        val participant = settlementTripAccessGuard.getActiveParticipant(
            userId = userId,
            tripId = tripId,
        )
        val transfer = getTransferInTrip(
            transferId = transferId,
            tripId = tripId,
        )
        val transferRow = getTransferRowOrThrow(transferId)

        if (transferRow.getSenderParticipantId() != participant.id) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_TRANSFER_ACCESS_DENIED)
        }

        transfer.confirmAsSender(Instant.now(clock))

        return readTransferResponse(transferId)
    }

    @Transactional
    fun confirmAsReceiver(
        userId: Long,
        tripId: Long,
        transferId: Long,
    ): SettlementTransferResponse {
        val participant = settlementTripAccessGuard.getActiveParticipant(
            userId = userId,
            tripId = tripId,
        )
        val transfer = getTransferInTrip(
            transferId = transferId,
            tripId = tripId,
        )
        val transferRow = getTransferRowOrThrow(transferId)

        if (transferRow.getReceiverParticipantId() != participant.id) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_TRANSFER_ACCESS_DENIED)
        }

        transfer.confirmAsReceiver(Instant.now(clock))

        return readTransferResponse(transferId)
    }

    private fun getTransferInTrip(
        transferId: Long,
        tripId: Long,
    ): SettlementTransfer {
        val transfer = settlementTransferRepository.findByIdAndDeletedAtIsNull(transferId)
            ?: throw BusinessException(SettlementErrorCode.SETTLEMENT_TRANSFER_NOT_FOUND)

        if (transfer.settlement.trip.id != tripId) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_TRANSFER_TRIP_MISMATCH)
        }

        return transfer
    }

    private fun matchesDirection(
        transfer: SettlementTransferRow,
        participant: TripParticipant,
        direction: TransferDirection?,
    ): Boolean {
        return when (direction) {
            null -> true
            TransferDirection.SENT -> transfer.getSenderParticipantId() == participant.id
            TransferDirection.RECEIVED -> transfer.getReceiverParticipantId() == participant.id
        }
    }

    private fun readTransferResponse(transferId: Long): SettlementTransferResponse {
        return SettlementTransferResponse.from(getTransferRowOrThrow(transferId))
    }

    private fun getTransferRowOrThrow(transferId: Long): SettlementTransferRow {
        return settlementTransferRepository.findTransferRowById(transferId)
            ?: throw BusinessException(SettlementErrorCode.SETTLEMENT_TRANSFER_NOT_FOUND)
    }

    private fun parseTransferStatus(status: String): SettlementTransferStatus {
        return try {
            SettlementTransferStatus.valueOf(status.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            throw BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_TRANSFER_STATUS)
        }
    }

    private fun parseTransferDirection(direction: String?): TransferDirection? {
        return when (direction?.trim()?.uppercase()) {
            null -> null
            "SENT", "SEND", "SENDER" -> TransferDirection.SENT
            "RECEIVED", "RECEIVE", "RECEIVER" -> TransferDirection.RECEIVED
            else -> throw BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_TRANSFER_DIRECTION)
        }
    }

    private companion object {
        private const val UNUSED_FILTER_ID = 0L
        private const val UNUSED_FILTER_VALUE = ""
    }

    private enum class TransferDirection {
        SENT,
        RECEIVED
    }
}

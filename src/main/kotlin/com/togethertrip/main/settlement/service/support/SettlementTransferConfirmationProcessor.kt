package com.togethertrip.main.settlement.service.support

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.settlement.domain.SettlementStatus
import com.togethertrip.main.settlement.domain.SettlementTransferRow
import com.togethertrip.main.settlement.exception.SettlementErrorCode
import com.togethertrip.main.settlement.repository.SettlementTransferRepository
import com.togethertrip.main.trip.domain.TripParticipant
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

@Component
class SettlementTransferConfirmationProcessor(
    private val settlementTransferRepository: SettlementTransferRepository,
    private val clock: Clock,
) {

    fun confirmAsSender(
        tripId: Long,
        transferId: Long,
        participant: TripParticipant,
    ): SettlementTransferConfirmationResult {
        validateTransferInConfirmedTrip(
            transferId = transferId,
            tripId = tripId,
        )
        val updatedCount = settlementTransferRepository.confirmAsSenderIfNeeded(
            transferId = transferId,
            tripId = tripId,
            participantId = participant.id,
            confirmedAt = Instant.now(clock),
        )

        val transferRow = getTransferRowOrThrow(transferId)
        validateSenderUpdateResult(
            transferRow = transferRow,
            participant = participant,
            updatedCount = updatedCount,
        )

        return SettlementTransferConfirmationResult(
            transferRow = transferRow,
            confirmationChanged = updatedCount > 0,
        )
    }

    fun confirmAsReceiver(
        tripId: Long,
        transferId: Long,
        participant: TripParticipant,
    ): SettlementTransferConfirmationResult {
        validateTransferInConfirmedTrip(
            transferId = transferId,
            tripId = tripId,
        )
        val updatedCount = settlementTransferRepository.confirmAsReceiverIfNeeded(
            transferId = transferId,
            tripId = tripId,
            participantId = participant.id,
            confirmedAt = Instant.now(clock),
        )

        val transferRow = getTransferRowOrThrow(transferId)
        validateReceiverUpdateResult(
            transferRow = transferRow,
            participant = participant,
            updatedCount = updatedCount,
        )

        return SettlementTransferConfirmationResult(
            transferRow = transferRow,
            confirmationChanged = updatedCount > 0,
        )
    }

    private fun validateTransferInConfirmedTrip(
        transferId: Long,
        tripId: Long,
    ) {
        val transfer = settlementTransferRepository.findByIdAndDeletedAtIsNull(transferId)
            ?: throw BusinessException(SettlementErrorCode.SETTLEMENT_TRANSFER_NOT_FOUND)

        if (transfer.settlement.trip.id != tripId) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_TRANSFER_TRIP_MISMATCH)
        }
        if (transfer.settlement.status != SettlementStatus.CONFIRMED) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_NOT_CONFIRMED)
        }
    }

    private fun validateSenderUpdateResult(
        transferRow: SettlementTransferRow,
        participant: TripParticipant,
        updatedCount: Int,
    ) {
        if (updatedCount > 0) {
            return
        }
        if (transferRow.getSenderParticipantId() != participant.id) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_TRANSFER_ACCESS_DENIED)
        }
        if (transferRow.getSenderConfirmedAt() == null) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_NOT_CONFIRMED)
        }
    }

    private fun validateReceiverUpdateResult(
        transferRow: SettlementTransferRow,
        participant: TripParticipant,
        updatedCount: Int,
    ) {
        if (updatedCount > 0) {
            return
        }
        if (transferRow.getReceiverParticipantId() != participant.id) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_TRANSFER_ACCESS_DENIED)
        }
        if (transferRow.getReceiverConfirmedAt() == null) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_NOT_CONFIRMED)
        }
    }

    private fun getTransferRowOrThrow(transferId: Long): SettlementTransferRow {
        return settlementTransferRepository.findTransferRowById(transferId)
            ?: throw BusinessException(SettlementErrorCode.SETTLEMENT_TRANSFER_NOT_FOUND)
    }
}

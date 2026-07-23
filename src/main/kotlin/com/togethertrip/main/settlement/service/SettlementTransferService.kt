package com.togethertrip.main.settlement.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.outbox.domain.OutboxAggregateType
import com.togethertrip.main.global.outbox.domain.OutboxEventType
import com.togethertrip.main.global.outbox.payload.common.DefaultOutboxRecipientPayload
import com.togethertrip.main.global.outbox.payload.settlement.SettlementTransferCompletedPayload
import com.togethertrip.main.global.outbox.payload.settlement.SettlementTransferConfirmedBySenderPayload
import com.togethertrip.main.global.outbox.service.OutboxEventPublisher
import com.togethertrip.main.settlement.domain.SettlementTransferDirection
import com.togethertrip.main.settlement.domain.SettlementTransferRow
import com.togethertrip.main.settlement.domain.SettlementTransferStatus
import com.togethertrip.main.settlement.dto.response.SettlementTransferResponse
import com.togethertrip.main.settlement.exception.SettlementErrorCode
import com.togethertrip.main.settlement.repository.SettlementTransferRepository
import com.togethertrip.main.settlement.service.support.SettlementAccessResolver
import com.togethertrip.main.settlement.service.support.SettlementTransferConfirmationResult
import com.togethertrip.main.settlement.service.support.SettlementTransferConfirmationProcessor
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripSettlementStatus
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.user.domain.UserStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class SettlementTransferService(
    private val settlementTransferRepository: SettlementTransferRepository,
    private val tripRepository: TripRepository,
    private val settlementAccessResolver: SettlementAccessResolver,
    private val settlementTransferConfirmationProcessor: SettlementTransferConfirmationProcessor,
    private val outboxEventPublisher: OutboxEventPublisher,
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
        val result = settlementTransferConfirmationProcessor.confirmAsSender(
            tripId = tripId,
            transferId = transferId,
            participant = participant,
        )

        publishSenderConfirmationIfNeeded(
            actor = participant,
            result = result,
        )
        publishCompletedIfNeeded(
            actor = participant,
            result = result,
        )
        markTripSettledIfAllTransfersCompleted(participant, result)

        return SettlementTransferResponse.from(result.transferRow)
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
        val result = settlementTransferConfirmationProcessor.confirmAsReceiver(
            tripId = tripId,
            transferId = transferId,
            participant = participant,
        )

        publishCompletedIfNeeded(
            actor = participant,
            result = result,
        )
        markTripSettledIfAllTransfersCompleted(participant, result)

        return SettlementTransferResponse.from(result.transferRow)
    }

    private fun markTripSettledIfAllTransfersCompleted(
        participant: TripParticipant,
        result: SettlementTransferConfirmationResult,
    ) {
        if (!result.completedChanged) {
            return
        }

        val trip = participant.trip
        if (trip.settlementStatus == TripSettlementStatus.SETTLED) {
            return
        }

        val completionSummary = settlementTransferRepository.findCompletionSummaryByTripId(trip.id)
            ?: return
        if (completionSummary.totalCount > 0 && completionSummary.incompleteCount == 0L) {
            trip.markSettled()
            tripRepository.save(trip)
        }
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

    private fun publishSenderConfirmationIfNeeded(
        actor: TripParticipant,
        result: SettlementTransferConfirmationResult,
    ) {
        if (!result.confirmationChanged) {
            return
        }

        val transfer = result.transferRow
        outboxEventPublisher.publish(
            aggregateType = OutboxAggregateType.SETTLEMENT_TRANSFER,
            aggregateId = transfer.getId(),
            eventType = OutboxEventType.SETTLEMENT_TRANSFER_CONFIRMED_BY_SENDER,
            payload = SettlementTransferConfirmedBySenderPayload(
                recipients = listOfNotNull(activeUserIdOrNull(transfer.getReceiverUserId(), transfer.getReceiverUserStatus()))
                    .map(::DefaultOutboxRecipientPayload),
                actorUserId = actor.user?.id ?: return,
                tripId = actor.trip.id,
                settlementId = transfer.getSettlementId(),
                settlementTransferId = transfer.getId(),
                tripName = transfer.getTripName(),
                actorDisplayName = actor.user?.nickname ?: actor.displayName,
                amount = transfer.getAmount(),
                currency = transfer.getCurrency(),
                occurredAt = Instant.now(),
            ),
        )
    }

    private fun publishCompletedIfNeeded(
        actor: TripParticipant,
        result: SettlementTransferConfirmationResult,
    ) {
        if (!result.completedChanged) {
            return
        }

        val actorUserId = actor.user?.id ?: return
        val transfer = result.transferRow
        val recipients = listOfNotNull(
            activeUserIdOrNull(transfer.getSenderUserId(), transfer.getSenderUserStatus()),
            activeUserIdOrNull(transfer.getReceiverUserId(), transfer.getReceiverUserStatus()),
        ).distinct().map(::DefaultOutboxRecipientPayload)

        outboxEventPublisher.publish(
            aggregateType = OutboxAggregateType.SETTLEMENT_TRANSFER,
            aggregateId = transfer.getId(),
            eventType = OutboxEventType.SETTLEMENT_TRANSFER_COMPLETED,
            payload = SettlementTransferCompletedPayload(
                recipients = recipients,
                actorUserId = actorUserId,
                tripId = actor.trip.id,
                settlementId = transfer.getSettlementId(),
                settlementTransferId = transfer.getId(),
                tripName = transfer.getTripName(),
                actorDisplayName = actor.user?.nickname ?: actor.displayName,
                amount = transfer.getAmount(),
                currency = transfer.getCurrency(),
                occurredAt = Instant.now(),
            ),
        )
    }

    private fun activeUserIdOrNull(
        userId: Long?,
        userStatus: String?,
    ): Long? {
        return userId?.takeIf { userStatus == UserStatus.ACTIVE.name }
    }

    private companion object {
        private const val UNUSED_FILTER_ID = 0L
        private const val UNUSED_FILTER_VALUE = ""
    }
}

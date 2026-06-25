package com.togethertrip.main.settlement.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.outbox.domain.OutboxAggregateType
import com.togethertrip.main.global.outbox.domain.OutboxEventType
import com.togethertrip.main.global.outbox.payload.settlement.SettlementConfirmedPayload
import com.togethertrip.main.global.outbox.payload.settlement.SettlementConfirmedRecipientPayload
import com.togethertrip.main.global.outbox.payload.settlement.SettlementTransferSummaryItemPayload
import com.togethertrip.main.global.outbox.payload.settlement.SettlementTransferSummaryPayload
import com.togethertrip.main.global.outbox.service.OutboxEventPublisher
import com.togethertrip.main.settlement.domain.Settlement
import com.togethertrip.main.settlement.domain.SettlementTransferRow
import com.togethertrip.main.settlement.domain.SettlementStatus
import com.togethertrip.main.settlement.domain.SettlementTransfer
import com.togethertrip.main.settlement.domain.SettlementTransferStatus
import com.togethertrip.main.settlement.domain.calculation.SettlementCalculationResult
import com.togethertrip.main.settlement.domain.snapshot.SettlementParticipantSnapshot
import com.togethertrip.main.settlement.dto.response.BalanceSummaryResponse
import com.togethertrip.main.settlement.dto.response.SettlementParticipantBalanceResponse
import com.togethertrip.main.settlement.dto.response.SettlementPreviewResponse
import com.togethertrip.main.settlement.dto.response.SettlementResponse
import com.togethertrip.main.settlement.dto.response.SettlementShareTokenResponse
import com.togethertrip.main.settlement.dto.response.SettlementTransferResponse
import com.togethertrip.main.settlement.exception.SettlementErrorCode
import com.togethertrip.main.settlement.repository.SettlementRepository
import com.togethertrip.main.settlement.repository.SettlementTransferRepository
import com.togethertrip.main.settlement.service.support.SettlementAccessResolver
import com.togethertrip.main.settlement.service.support.SettlementCalculationService
import com.togethertrip.main.settlement.service.support.SettlementShareTokenIssuer
import com.togethertrip.main.settlement.service.support.SettlementSnapshotMapper
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripSettlementStatus
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.trip.service.support.TripNotificationRecipientResolver
import com.togethertrip.main.user.domain.User
import jakarta.persistence.OptimisticLockException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant

@Service
class SettlementService(
    private val settlementRepository: SettlementRepository,
    private val settlementTransferRepository: SettlementTransferRepository,
    private val settlementCalculationService: SettlementCalculationService,
    private val settlementSnapshotMapper: SettlementSnapshotMapper,
    private val settlementShareTokenIssuer: SettlementShareTokenIssuer,
    private val settlementAccessResolver: SettlementAccessResolver,
    private val tripRepository: TripRepository,
    private val tripParticipantRepository: TripParticipantRepository,
    private val outboxEventPublisher: OutboxEventPublisher,
    private val tripNotificationRecipientResolver: TripNotificationRecipientResolver,
) {

    private val clock = Clock.systemDefaultZone()

    @Transactional(readOnly = true)
    fun previewSettlement(
        userId: Long,
        tripId: Long,
    ): SettlementPreviewResponse {
        val trip = settlementAccessResolver.getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )
        val calculation = calculateValidSettlement(tripId)
        val participants = settlementCalculationService.getParticipantsById(
            tripId = tripId,
            calculation = calculation,
        )
        val balances = settlementCalculationService.createBalanceResponses(
            balances = calculation.balances,
            participants = participants,
        )
        val transfers = settlementCalculationService.createTransferResponses(
            calculation = calculation,
            participants = participants,
        )

        return SettlementPreviewResponse(
            tripId = trip.id,
            tripExpenseVersion = trip.expenseVersion,
            baseCurrency = calculation.baseCurrency,
            totalExpenseAmount = calculation.totalExpenseAmount,
            totalShareAmount = calculation.totalShareAmount,
            balances = balances,
            transfers = transfers,
        )
    }

    @Transactional(readOnly = true)
    fun getBalanceSummary(
        userId: Long,
        tripId: Long,
    ): BalanceSummaryResponse {
        val preview = previewSettlement(
            userId = userId,
            tripId = tripId,
        )

        return BalanceSummaryResponse(
            tripId = preview.tripId,
            tripExpenseVersion = preview.tripExpenseVersion,
            baseCurrency = preview.baseCurrency,
            balances = preview.balances,
        )
    }

    @Transactional
    fun confirmSettlement(
        userId: Long,
        tripId: Long,
    ): SettlementResponse {
        val user = settlementAccessResolver.getActiveUser(userId)
        val trip = settlementAccessResolver.getOwnedTrip(
            userId = userId,
            tripId = tripId,
        )
        validateConfirmableTrip(trip)
        validateNoConfirmedSettlement(tripId)

        val calculation = calculateValidSettlement(tripId)
        val participants = settlementCalculationService.getParticipantsById(
            tripId = tripId,
            calculation = calculation,
        )
        val balances = settlementCalculationService.createBalanceResponses(
            balances = calculation.balances,
            participants = participants,
        )
        val snapshotPayload = settlementSnapshotMapper.write(balances)
        val settlement = saveConfirmedSettlementSnapshot(
            trip = trip,
            user = user,
            calculation = calculation,
            snapshotPayload = snapshotPayload,
            participants = participants,
        )
        val transferRows = settlementTransferRepository.findTransferRowsBySettlementId(settlement.id)
        val transfers = transferRows.map(SettlementTransferResponse::from)
        publishSettlementConfirmed(
            actor = user,
            settlement = settlement,
            transferRows = transferRows,
        )

        return SettlementResponse.from(
            settlement = settlement,
            balances = balances,
            transfers = transfers,
        )
    }

    @Transactional(readOnly = true)
    fun getSettlement(
        userId: Long,
        tripId: Long,
        settlementId: Long,
    ): SettlementResponse {
        val settlement = getSettlementOrThrow(settlementId)
        validateSettlementTrip(
            settlement = settlement,
            tripId = tripId,
        )
        val balances = readBalanceResponses(settlement)
        val transfers = readTransferResponses(settlement)

        return SettlementResponse.from(
            settlement = settlement,
            balances = balances,
            transfers = transfers,
        )
    }

    @Transactional
    fun createShareToken(
        userId: Long,
        tripId: Long,
        settlementId: Long,
    ): SettlementShareTokenResponse {
        val settlement = getSettlementOrThrow(settlementId)
        validateSettlementTrip(
            settlement = settlement,
            tripId = tripId,
        )
        validateConfirmedSettlement(settlement)

        return SettlementShareTokenResponse(
            settlementId = settlement.id,
            shareToken = settlementShareTokenIssuer.issue(settlement),
        )
    }

    private fun calculateValidSettlement(tripId: Long): SettlementCalculationResult {
        val calculation = settlementCalculationService.calculate(tripId)
        validateCalculationTotal(calculation)

        return calculation
    }

    private fun saveConfirmedSettlementSnapshot(
        trip: Trip,
        user: User,
        calculation: SettlementCalculationResult,
        snapshotPayload: String,
        participants: Map<Long, SettlementParticipantSnapshot>,
    ): Settlement {
        val settlement = Settlement(
            trip = trip,
            status = SettlementStatus.CONFIRMED,
            tripExpenseVersion = trip.expenseVersion,
            calculationVersion = CALCULATION_VERSION,
            baseCurrency = calculation.baseCurrency,
            totalExpenseAmount = calculation.totalExpenseAmount,
            totalShareAmount = calculation.totalShareAmount,
            snapshotPayload = snapshotPayload,
            confirmedAt = Instant.now(clock),
            confirmedBy = user,
        )

        try {
            val savedSettlement = settlementRepository.saveAndFlush(settlement)
            val savedTransfers = saveTransfers(
                settlement = savedSettlement,
                calculation = calculation,
                participants = participants,
            )
            if (savedTransfers.all { it.status == SettlementTransferStatus.COMPLETED }) {
                trip.markSettled(Instant.now(clock))
            } else {
                trip.markSettlementInProgress(Instant.now(clock))
            }
            tripRepository.saveAndFlush(trip)

            return savedSettlement
        } catch (_: DataIntegrityViolationException) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_ALREADY_CONFIRMED)
        } catch (_: ObjectOptimisticLockingFailureException) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_ALREADY_CONFIRMED)
        } catch (_: OptimisticLockException) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_ALREADY_CONFIRMED)
        }
    }

    private fun saveTransfers(
        settlement: Settlement,
        calculation: SettlementCalculationResult,
        participants: Map<Long, SettlementParticipantSnapshot>,
    ): List<SettlementTransfer> {
        return calculation.transfers.map { plan ->
            val sender = tripParticipantRepository.getReferenceById(plan.senderParticipantId)
            val receiver = tripParticipantRepository.getReferenceById(plan.receiverParticipantId)
            val transfer = SettlementTransfer(
                settlement = settlement,
                sender = sender,
                receiver = receiver,
                amount = plan.amount,
                currency = calculation.baseCurrency,
                status = SettlementTransferStatus.PENDING,
            )
            val confirmedAt = Instant.now(clock)
            val senderSnapshot = participants[plan.senderParticipantId]
            val receiverSnapshot = participants[plan.receiverParticipantId]

            if (senderSnapshot?.requiresAutoConfirmation == true) {
                transfer.autoConfirmSender(
                    reason = PARTICIPANT_AUTO_CONFIRM_REASON,
                    confirmedAt = confirmedAt,
                )
            }
            if (receiverSnapshot?.requiresAutoConfirmation == true) {
                transfer.autoConfirmReceiver(
                    reason = PARTICIPANT_AUTO_CONFIRM_REASON,
                    confirmedAt = confirmedAt,
                )
            }

            settlementTransferRepository.save(transfer)
        }
    }

    private fun readTransferResponses(settlement: Settlement): List<SettlementTransferResponse> {
        return settlementTransferRepository
            .findTransferRowsBySettlementId(settlement.id)
            .map(SettlementTransferResponse::from)
    }

    private fun readBalanceResponses(settlement: Settlement): List<SettlementParticipantBalanceResponse> {
        return settlementSnapshotMapper
            .read(settlement)
            .balances
            .map(SettlementParticipantBalanceResponse::from)
    }

    private fun publishSettlementConfirmed(
        actor: User,
        settlement: Settlement,
        transferRows: List<SettlementTransferRow>,
    ) {
        val transferRowsBySenderUserId = transferRows
            .filter { row -> row.getSenderUserId() != null }
            .groupBy { row -> row.getSenderUserId()!! }
        val recipients = tripNotificationRecipientResolver.findActiveUserIds(
            tripId = settlement.trip.id,
            actorUserId = actor.id,
        ).map { userId ->
            SettlementConfirmedRecipientPayload(
                userId = userId,
                transferSummary = transferRowsBySenderUserId[userId]?.let(::createTransferSummary),
            )
        }

        outboxEventPublisher.publish(
            aggregateType = OutboxAggregateType.SETTLEMENT,
            aggregateId = settlement.id,
            eventType = OutboxEventType.SETTLEMENT_CONFIRMED,
            payload = SettlementConfirmedPayload(
                recipients = recipients,
                actorUserId = actor.id,
                tripId = settlement.trip.id,
                settlementId = settlement.id,
                tripName = settlement.trip.title,
                occurredAt = Instant.now(clock),
            ),
        )
    }

    private fun createTransferSummary(
        transferRows: List<SettlementTransferRow>,
    ): SettlementTransferSummaryPayload {
        val currency = transferRows.first().getCurrency()

        return SettlementTransferSummaryPayload(
            sendCount = transferRows.size,
            totalSendAmount = transferRows.fold(BigDecimal.ZERO) { total, row -> total + row.getAmount() },
            currency = currency,
            items = transferRows.map { row ->
                SettlementTransferSummaryItemPayload(
                    settlementTransferId = row.getId(),
                    receiverParticipantId = row.getReceiverParticipantId(),
                    receiverParticipantDisplayName = row.getReceiverDisplayName(),
                    amount = row.getAmount(),
                )
            },
        )
    }

    private fun validateNoConfirmedSettlement(tripId: Long) {
        val settlement = settlementRepository.findFirstByTripIdAndStatusAndDeletedAtIsNull(
            tripId = tripId,
            status = SettlementStatus.CONFIRMED,
        )

        if (settlement != null) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_ALREADY_CONFIRMED)
        }
    }

    private fun validateConfirmableTrip(trip: Trip) {
        if (trip.settlementStatus != TripSettlementStatus.NOT_STARTED) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_ALREADY_CONFIRMED)
        }
    }

    private fun validateCalculationTotal(calculation: SettlementCalculationResult) {
        if (calculation.totalExpenseAmount.compareTo(calculation.totalShareAmount) != 0) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_TOTAL_MISMATCH)
        }
    }

    private fun validateSettlementTrip(
        settlement: Settlement,
        tripId: Long,
    ) {
        if (settlement.trip.id != tripId) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_TRIP_MISMATCH)
        }
    }

    private fun validateConfirmedSettlement(settlement: Settlement) {
        if (settlement.status != SettlementStatus.CONFIRMED) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_NOT_CONFIRMED)
        }
    }

    private fun getSettlementOrThrow(settlementId: Long): Settlement {
        return settlementRepository.findByIdAndDeletedAtIsNull(settlementId)
            ?: throw BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND)
    }

    private companion object {
        private const val CALCULATION_VERSION = "settlement-v1"
        private const val PARTICIPANT_AUTO_CONFIRM_REASON = "PARTICIPANT_AUTO_CONFIRMED"
    }
}

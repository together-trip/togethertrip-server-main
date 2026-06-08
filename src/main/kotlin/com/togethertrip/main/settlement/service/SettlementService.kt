package com.togethertrip.main.settlement.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.settlement.domain.Settlement
import com.togethertrip.main.settlement.domain.SettlementStatus
import com.togethertrip.main.settlement.domain.SettlementTransfer
import com.togethertrip.main.settlement.domain.SettlementTransferStatus
import com.togethertrip.main.settlement.domain.calculation.SettlementCalculationResult
import com.togethertrip.main.settlement.dto.response.BalanceSummaryResponse
import com.togethertrip.main.settlement.dto.response.SettlementParticipantBalanceResponse
import com.togethertrip.main.settlement.dto.response.SettlementPreviewResponse
import com.togethertrip.main.settlement.dto.response.SettlementResponse
import com.togethertrip.main.settlement.dto.response.SettlementShareTokenResponse
import com.togethertrip.main.settlement.dto.response.SettlementTransferResponse
import com.togethertrip.main.settlement.exception.SettlementErrorCode
import com.togethertrip.main.settlement.repository.SettlementRepository
import com.togethertrip.main.settlement.repository.SettlementTransferRepository
import com.togethertrip.main.settlement.service.support.SettlementCalculationService
import com.togethertrip.main.settlement.service.support.SettlementShareTokenGenerator
import com.togethertrip.main.settlement.service.support.SettlementSnapshotMapper
import com.togethertrip.main.settlement.service.support.SettlementTripAccessGuard
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripSettlementStatus
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.user.domain.User
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class SettlementService(
    private val settlementRepository: SettlementRepository,
    private val settlementTransferRepository: SettlementTransferRepository,
    private val settlementCalculationService: SettlementCalculationService,
    private val settlementSnapshotMapper: SettlementSnapshotMapper,
    private val settlementShareTokenGenerator: SettlementShareTokenGenerator,
    private val settlementTripAccessGuard: SettlementTripAccessGuard,
) {

    private val clock = Clock.systemDefaultZone()

    @Transactional(readOnly = true)
    fun previewSettlement(
        userId: Long,
        tripId: Long,
    ): SettlementPreviewResponse {
        val trip = settlementTripAccessGuard.getAccessibleTrip(
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
        val user = settlementTripAccessGuard.getActiveUser(userId)
        val trip = settlementTripAccessGuard.getOwnedTrip(
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
        val settlement = saveConfirmedSettlement(
            trip = trip,
            user = user,
            calculation = calculation,
            snapshotPayload = snapshotPayload,
        )
        val transfers = saveTransfers(
            settlement = settlement,
            calculation = calculation,
            participants = participants,
        )
        trip.markSettled(Instant.now(clock))

        return SettlementResponse.from(
            settlement = settlement,
            balances = balances,
            transfers = transfers.map(SettlementTransferResponse::from),
        )
    }

    @Transactional(readOnly = true)
    fun getSettlement(
        userId: Long,
        tripId: Long,
        settlementId: Long,
    ): SettlementResponse {
        settlementTripAccessGuard.getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )
        val settlement = getSettlementOrThrow(settlementId)
        validateSettlementTrip(
            settlement = settlement,
            tripId = tripId,
        )
        val balances = readBalanceResponses(settlement)
        val transfers = settlementTransferRepository
            .findBySettlementIdAndDeletedAtIsNullOrderByIdAsc(settlement.id)
            .map(SettlementTransferResponse::from)

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
        settlementTripAccessGuard.getOwnedTrip(
            userId = userId,
            tripId = tripId,
        )
        val settlement = getSettlementOrThrow(settlementId)
        validateSettlementTrip(
            settlement = settlement,
            tripId = tripId,
        )
        validateConfirmedSettlement(settlement)

        if (settlement.shareToken == null) {
            settlement.shareToken = settlementShareTokenGenerator.generate()
        }

        return SettlementShareTokenResponse(
            settlementId = settlement.id,
            shareToken = settlement.shareToken
                ?: throw BusinessException(SettlementErrorCode.SETTLEMENT_SHARE_TOKEN_NOT_FOUND),
        )
    }

    private fun calculateValidSettlement(tripId: Long): SettlementCalculationResult {
        val calculation = settlementCalculationService.calculate(tripId)
        validateCalculationTotal(calculation)

        return calculation
    }

    private fun saveConfirmedSettlement(
        trip: Trip,
        user: User,
        calculation: SettlementCalculationResult,
        snapshotPayload: String,
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

        return try {
            settlementRepository.save(settlement)
        } catch (_: DataIntegrityViolationException) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_ALREADY_CONFIRMED)
        }
    }

    private fun saveTransfers(
        settlement: Settlement,
        calculation: SettlementCalculationResult,
        participants: Map<Long, TripParticipant>,
    ): List<SettlementTransfer> {
        return calculation.transfers.map { plan ->
            val sender = participants[plan.senderParticipantId]
                ?: throw BusinessException(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND)
            val receiver = participants[plan.receiverParticipantId]
                ?: throw BusinessException(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND)
            val transfer = SettlementTransfer(
                settlement = settlement,
                sender = sender,
                receiver = receiver,
                amount = plan.amount,
                currency = calculation.baseCurrency,
                status = SettlementTransferStatus.PENDING,
            )

            settlementTransferRepository.save(transfer)
        }
    }

    private fun readBalanceResponses(settlement: Settlement): List<SettlementParticipantBalanceResponse> {
        return settlementSnapshotMapper
            .read(settlement)
            .balances
            .map(SettlementParticipantBalanceResponse::from)
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
    }
}

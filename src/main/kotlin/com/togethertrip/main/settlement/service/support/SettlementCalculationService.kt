package com.togethertrip.main.settlement.service.support

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.settlement.domain.calculation.SettlementCalculationInput
import com.togethertrip.main.settlement.domain.calculation.SettlementCalculationResult
import com.togethertrip.main.settlement.domain.calculation.SettlementCalculator
import com.togethertrip.main.settlement.domain.calculation.SettlementParticipantBalance
import com.togethertrip.main.settlement.domain.calculation.SettlementPaymentInput
import com.togethertrip.main.settlement.domain.calculation.SettlementShareInput
import com.togethertrip.main.settlement.domain.snapshot.SettlementParticipantSnapshot
import com.togethertrip.main.settlement.dto.response.SettlementParticipantBalanceResponse
import com.togethertrip.main.settlement.dto.response.SettlementTransferResponse
import com.togethertrip.main.settlement.repository.SettlementTransactionQueryRepository
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripParticipantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class SettlementCalculationService(
    private val settlementTransactionQueryRepository: SettlementTransactionQueryRepository,
    private val tripParticipantRepository: TripParticipantRepository,
) {

    private val settlementCalculator = SettlementCalculator()

    @Transactional(readOnly = true)
    fun calculate(tripId: Long): SettlementCalculationResult {
        val payments = settlementTransactionQueryRepository.findSettlementPaymentRows(tripId)
        val shares = settlementTransactionQueryRepository.findSettlementShareRows(tripId)

        return settlementCalculator.calculate(
            SettlementCalculationInput(
                baseCurrency = BASE_CURRENCY,
                payments = payments.map { payment ->
                    SettlementPaymentInput(
                        participantId = payment.participantId,
                        amount = payment.amount,
                    )
                },
                shares = shares.map { share ->
                    SettlementShareInput(
                        participantId = share.participantId,
                        amount = share.amount,
                    )
                },
            )
        )
    }

    @Transactional(readOnly = true)
    fun getParticipantsById(
        tripId: Long,
        calculation: SettlementCalculationResult,
    ): Map<Long, SettlementParticipantSnapshot> {
        val calculationParticipantIds = calculation.balances.map(SettlementParticipantBalance::participantId).toSet()
        if (calculationParticipantIds.isEmpty()) {
            return emptyMap()
        }

        val participants = tripParticipantRepository
            .findSettlementParticipantRows(
                tripId = tripId,
                participantIds = calculationParticipantIds,
            )
            .map(SettlementParticipantSnapshot::from)
            .associateBy(SettlementParticipantSnapshot::participantId)
        val missingParticipantId = calculationParticipantIds.firstOrNull { participantId ->
            participants[participantId] == null
        }

        if (missingParticipantId != null) {
            throw BusinessException(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND)
        }

        return participants
    }

    fun createBalanceResponses(
        balances: List<SettlementParticipantBalance>,
        participants: Map<Long, SettlementParticipantSnapshot>,
    ): List<SettlementParticipantBalanceResponse> {
        val balanceByParticipantId = balances.associateBy(SettlementParticipantBalance::participantId)

        return participants.values.map { participant ->
            val balance = balanceByParticipantId[participant.participantId]
                ?: SettlementParticipantBalance(
                    participantId = participant.participantId,
                    paidAmount = ZERO_AMOUNT,
                    shareAmount = ZERO_AMOUNT,
                    netAmount = ZERO_AMOUNT,
                )
            SettlementParticipantBalanceResponse.from(
                balance = balance,
                participant = participant,
            )
        }
    }

    fun createTransferResponses(
        calculation: SettlementCalculationResult,
        participants: Map<Long, SettlementParticipantSnapshot>,
    ): List<SettlementTransferResponse> {
        return calculation.transfers.map { plan ->
            val sender = participants[plan.senderParticipantId]
                ?: throw BusinessException(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND)
            val receiver = participants[plan.receiverParticipantId]
                ?: throw BusinessException(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND)

            SettlementTransferResponse.from(
                plan = plan,
                sender = sender,
                receiver = receiver,
                currency = calculation.baseCurrency,
            )
        }
    }

    private companion object {
        private const val BASE_CURRENCY = "KRW"
        private val ZERO_AMOUNT = BigDecimal("0.00")
    }
}

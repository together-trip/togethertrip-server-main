package com.togethertrip.main.settlement.service.support

import com.togethertrip.main.settlement.domain.TripParticipantBalanceSummary
import com.togethertrip.main.settlement.repository.TripParticipantBalanceSummaryRepository
import com.togethertrip.main.transaction.domain.TransactionPayment
import com.togethertrip.main.transaction.domain.TransactionShare
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.repository.TripParticipantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class TripParticipantBalanceSummaryProjectionService(
    private val balanceSummaryRepository: TripParticipantBalanceSummaryRepository,
    private val tripParticipantRepository: TripParticipantRepository,
) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun applyTransactionCreated(
        trip: Trip,
        payments: List<TransactionPayment>,
        shares: List<TransactionShare>,
    ) {
        applyDeltas(
            trip = trip,
            paidDeltas = payments.toPaidDeltas(multiplier = BigDecimal.ONE),
            shareDeltas = shares.toShareDeltas(multiplier = BigDecimal.ONE),
        )
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun applyTransactionUpdated(
        trip: Trip,
        previousPayments: List<TransactionPayment>,
        previousShares: List<TransactionShare>,
        currentPayments: List<TransactionPayment>,
        currentShares: List<TransactionShare>,
    ) {
        applyDeltas(
            trip = trip,
            paidDeltas = previousPayments.toPaidDeltas(multiplier = NEGATIVE_ONE)
                .merge(currentPayments.toPaidDeltas(multiplier = BigDecimal.ONE)),
            shareDeltas = previousShares.toShareDeltas(multiplier = NEGATIVE_ONE)
                .merge(currentShares.toShareDeltas(multiplier = BigDecimal.ONE)),
        )
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun applyTransactionVoided(
        trip: Trip,
        payments: List<TransactionPayment>,
        shares: List<TransactionShare>,
    ) {
        applyDeltas(
            trip = trip,
            paidDeltas = payments.toPaidDeltas(multiplier = NEGATIVE_ONE),
            shareDeltas = shares.toShareDeltas(multiplier = NEGATIVE_ONE),
        )
    }

    private fun applyDeltas(
        trip: Trip,
        paidDeltas: Map<Long, BigDecimal>,
        shareDeltas: Map<Long, BigDecimal>,
    ) {
        val participantIds = paidDeltas.keys + shareDeltas.keys

        participantIds.forEach { participantId ->
            val paidDelta = paidDeltas[participantId] ?: BigDecimal.ZERO
            val shareDelta = shareDeltas[participantId] ?: BigDecimal.ZERO
            if (paidDelta.compareTo(BigDecimal.ZERO) == 0 && shareDelta.compareTo(BigDecimal.ZERO) == 0) {
                return@forEach
            }

            val summary = balanceSummaryRepository.findByTripIdAndTripParticipantIdAndDeletedAtIsNull(
                tripId = trip.id,
                tripParticipantId = participantId,
            ) ?: TripParticipantBalanceSummary(
                trip = trip,
                tripParticipant = tripParticipantRepository.getReferenceById(participantId),
            )

            summary.paidBaseAmount = summary.paidBaseAmount.add(paidDelta)
            summary.shareBaseAmount = summary.shareBaseAmount.add(shareDelta)
            summary.netBaseAmount = summary.paidBaseAmount.subtract(summary.shareBaseAmount)
            summary.projectionVersion = trip.expenseVersion
            balanceSummaryRepository.save(summary)
        }
    }

    private fun List<TransactionPayment>.toPaidDeltas(
        multiplier: BigDecimal,
    ): Map<Long, BigDecimal> {
        return groupBy { payment -> payment.tripParticipant.id }
            .mapValues { (_, payments) ->
                payments.fold(BigDecimal.ZERO) { total, payment ->
                    total.add(payment.baseAmount.multiply(multiplier))
                }
            }
    }

    private fun List<TransactionShare>.toShareDeltas(
        multiplier: BigDecimal,
    ): Map<Long, BigDecimal> {
        return groupBy { share -> share.tripParticipant.id }
            .mapValues { (_, shares) ->
                shares.fold(BigDecimal.ZERO) { total, share ->
                    total.add(share.baseShareAmount.multiply(multiplier))
                }
            }
    }

    private fun Map<Long, BigDecimal>.merge(
        other: Map<Long, BigDecimal>,
    ): Map<Long, BigDecimal> {
        return (keys + other.keys).associateWith { participantId ->
            (this[participantId] ?: BigDecimal.ZERO).add(other[participantId] ?: BigDecimal.ZERO)
        }
    }

    private companion object {
        private val NEGATIVE_ONE = BigDecimal("-1")
    }
}

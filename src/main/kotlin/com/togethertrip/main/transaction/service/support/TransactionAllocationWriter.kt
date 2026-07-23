package com.togethertrip.main.transaction.service.support

import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionPayment
import com.togethertrip.main.transaction.domain.TransactionShare
import com.togethertrip.main.transaction.domain.exchange.TransactionCurrencySnapshot
import com.togethertrip.main.transaction.domain.ledger.PaymentAllocation
import com.togethertrip.main.transaction.domain.ledger.ShareAllocation
import com.togethertrip.main.transaction.repository.TransactionPaymentRepository
import com.togethertrip.main.transaction.repository.TransactionShareRepository
import com.togethertrip.main.trip.service.support.TripAccessResolver
import org.springframework.stereotype.Component

@Component
class TransactionAllocationWriter(
    private val transactionPaymentRepository: TransactionPaymentRepository,
    private val transactionShareRepository: TransactionShareRepository,
    private val tripAccessResolver: TripAccessResolver,
) {

    fun replacePayments(
        transaction: Transaction,
        tripId: Long,
        allocations: List<PaymentAllocation>,
        snapshot: TransactionCurrencySnapshot,
        previousPayments: List<TransactionPayment>,
    ): List<TransactionPayment> {
        previousPayments.forEach { it.markDeleted() }
        return savePayments(
            transaction = transaction,
            tripId = tripId,
            allocations = allocations,
            snapshot = snapshot,
        )
    }

    fun replaceShares(
        transaction: Transaction,
        tripId: Long,
        allocations: List<ShareAllocation>,
        snapshot: TransactionCurrencySnapshot,
        previousShares: List<TransactionShare>,
    ): List<TransactionShare> {
        previousShares.forEach { it.markDeleted() }
        return saveShares(
            transaction = transaction,
            tripId = tripId,
            allocations = allocations,
            snapshot = snapshot,
        )
    }

    fun savePayments(
        transaction: Transaction,
        tripId: Long,
        allocations: List<PaymentAllocation>,
        snapshot: TransactionCurrencySnapshot,
    ): List<TransactionPayment> {
        val baseAmounts = snapshot.convertAllocations(
            amounts = allocations.map { it.amount },
            expectedTotal = transaction.baseAmount,
        )

        return allocations.mapIndexed { index, allocation ->
            val participant = tripAccessResolver.getActiveParticipantById(
                tripId = tripId,
                participantId = allocation.participantId,
            )
            val payment = TransactionPayment(
                transaction = transaction,
                tripParticipant = participant,
                amount = allocation.amount,
                currency = snapshot.currency,
                exchangeRate = snapshot.exchangeRate,
                baseCurrency = snapshot.baseCurrency,
                baseAmount = baseAmounts[index],
            )

            transactionPaymentRepository.save(payment)
        }
    }

    fun saveShares(
        transaction: Transaction,
        tripId: Long,
        allocations: List<ShareAllocation>,
        snapshot: TransactionCurrencySnapshot,
    ): List<TransactionShare> {
        val baseShareAmounts = snapshot.convertAllocations(
            amounts = allocations.map { it.shareAmount },
            expectedTotal = transaction.baseAmount,
        )

        return allocations.mapIndexed { index, allocation ->
            val participant = tripAccessResolver.getActiveParticipantById(
                tripId = tripId,
                participantId = allocation.participantId,
            )
            val share = TransactionShare(
                transaction = transaction,
                tripParticipant = participant,
                shareAmount = allocation.shareAmount,
                currency = snapshot.currency,
                exchangeRate = snapshot.exchangeRate,
                baseCurrency = snapshot.baseCurrency,
                baseShareAmount = baseShareAmounts[index],
                shareRatio = allocation.shareRatio,
            )

            transactionShareRepository.save(share)
        }
    }
}

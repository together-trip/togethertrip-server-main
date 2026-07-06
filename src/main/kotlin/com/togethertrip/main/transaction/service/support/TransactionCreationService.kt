package com.togethertrip.main.transaction.service.support

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionEventType
import com.togethertrip.main.transaction.domain.exchange.TransactionCurrencySnapshot
import com.togethertrip.main.transaction.dto.request.CreateTransactionRequest
import com.togethertrip.main.transaction.exception.TransactionErrorCode
import com.togethertrip.main.transaction.repository.TransactionRepository
import com.togethertrip.main.transaction.service.TransactionExchangeRateResolver
import com.togethertrip.main.settlement.service.support.TripParticipantBalanceSummaryProjectionService
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.service.support.TripAccessResolver
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@Service
class TransactionCreationService(
    private val transactionRepository: TransactionRepository,
    private val transactionExchangeRateResolver: TransactionExchangeRateResolver,
    private val balanceSummaryProjectionService: TripParticipantBalanceSummaryProjectionService,
    private val tripAccessResolver: TripAccessResolver,
    private val transactionEventRecorder: TransactionEventRecorder,
    private val transactionAllocationWriter: TransactionAllocationWriter,
    private val clock: Clock,
) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun create(
        userId: Long,
        tripId: Long,
        request: CreateTransactionRequest,
    ): TransactionCreationResult {
        val user = tripAccessResolver.getActiveUser(userId)
        val trip = tripAccessResolver.getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )
        requireTransactionWritable(trip)
        val actorParticipant = tripAccessResolver.getActiveParticipantByUserId(
            tripId = tripId,
            userId = userId,
        )

        val ledgerEntry = request.toLedgerEntry()
        val currencySnapshot = resolveCurrencySnapshot(
            currency = ledgerEntry.currency,
            occurredAt = request.occurredAt,
        )
        val transaction = transactionRepository.save(
            Transaction.create(
                trip = trip,
                createdBy = user,
                ledgerEntry = ledgerEntry,
                currencySnapshot = currencySnapshot,
                category = request.category,
                occurredAt = request.occurredAt,
            )
        )
        val payments = transactionAllocationWriter.savePayments(
            transaction = transaction,
            tripId = tripId,
            allocations = ledgerEntry.payments,
            snapshot = currencySnapshot,
        )
        val shares = transactionAllocationWriter.saveShares(
            transaction = transaction,
            tripId = tripId,
            allocations = ledgerEntry.shares,
            snapshot = currencySnapshot,
        )

        transactionEventRecorder.record(
            trip = trip,
            transaction = transaction,
            eventType = TransactionEventType.CREATED,
            createdBy = user,
        )
        balanceSummaryProjectionService.applyTransactionCreated(
            trip = trip,
            payments = payments,
            shares = shares,
        )

        return TransactionCreationResult(
            trip = trip,
            actor = user,
            actorParticipant = actorParticipant,
            transaction = transaction,
            payments = payments,
            shares = shares,
        )
    }

    private fun resolveCurrencySnapshot(
        currency: String,
        occurredAt: Instant?,
    ): TransactionCurrencySnapshot {
        return transactionExchangeRateResolver.resolve(
            currency = currency,
            spendingDate = resolveSpendingDate(occurredAt),
        ).toCurrencySnapshot()
    }

    private fun resolveSpendingDate(occurredAt: Instant?): LocalDate? {
        return occurredAt?.atZone(clock.zone)?.toLocalDate()
    }

    private fun requireTransactionWritable(trip: Trip) {
        if (!trip.canChangeTransactions()) {
            throw BusinessException(TransactionErrorCode.TRANSACTION_LOCKED_BY_SETTLEMENT)
        }
    }
}

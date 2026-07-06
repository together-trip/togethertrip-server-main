package com.togethertrip.main.transaction.service.support

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionEvent
import com.togethertrip.main.transaction.domain.TransactionEventType
import com.togethertrip.main.transaction.domain.TransactionPayment
import com.togethertrip.main.transaction.domain.TransactionShare
import com.togethertrip.main.transaction.domain.event.TransactionEventPayload
import com.togethertrip.main.transaction.domain.exchange.TransactionCurrencySnapshot
import com.togethertrip.main.transaction.domain.ledger.PaymentAllocation
import com.togethertrip.main.transaction.domain.ledger.ShareAllocation
import com.togethertrip.main.transaction.dto.request.CreateTransactionRequest
import com.togethertrip.main.transaction.exception.TransactionErrorCode
import com.togethertrip.main.transaction.repository.TransactionEventRepository
import com.togethertrip.main.transaction.repository.TransactionPaymentRepository
import com.togethertrip.main.transaction.repository.TransactionRepository
import com.togethertrip.main.transaction.repository.TransactionShareRepository
import com.togethertrip.main.transaction.service.TransactionExchangeRateResolver
import com.togethertrip.main.settlement.service.support.TripParticipantBalanceSummaryProjectionService
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.domain.TripSettlementStatus
import com.togethertrip.main.trip.service.support.TripAccessResolver
import com.togethertrip.main.user.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@Service
class TransactionCreationService(
    private val transactionRepository: TransactionRepository,
    private val transactionShareRepository: TransactionShareRepository,
    private val transactionPaymentRepository: TransactionPaymentRepository,
    private val transactionEventRepository: TransactionEventRepository,
    private val transactionExchangeRateResolver: TransactionExchangeRateResolver,
    private val balanceSummaryProjectionService: TripParticipantBalanceSummaryProjectionService,
    private val tripAccessResolver: TripAccessResolver,
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
        validateWritableTrip(trip)
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
            Transaction(
                trip = trip,
                createdBy = user,
                transactionType = ledgerEntry.transactionType,
                amount = ledgerEntry.amount,
                currency = currencySnapshot.currency,
                exchangeRate = currencySnapshot.exchangeRate,
                baseCurrency = currencySnapshot.baseCurrency,
                baseAmount = currencySnapshot.convert(ledgerEntry.amount),
                category = request.category,
                occurredAt = request.occurredAt,
            )
        )
        val payments = savePayments(
            transaction = transaction,
            tripId = tripId,
            allocations = ledgerEntry.payments,
            snapshot = currencySnapshot,
        )
        val shares = saveShares(
            transaction = transaction,
            tripId = tripId,
            allocations = ledgerEntry.shares,
            snapshot = currencySnapshot,
        )

        recordEvent(
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

    private fun savePayments(
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

    private fun saveShares(
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

    private fun recordEvent(
        trip: Trip,
        transaction: Transaction,
        eventType: TransactionEventType,
        createdBy: User,
    ) {
        trip.expenseVersion += 1

        val payload = TransactionEventPayload.from(
            transaction = transaction,
            eventType = eventType,
        ).toJson()
        val event = TransactionEvent(
            transaction = transaction,
            trip = trip,
            eventType = eventType,
            aggregateVersion = trip.expenseVersion,
            payload = payload,
            createdBy = createdBy,
        )

        transactionEventRepository.save(event)
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

    private fun validateWritableTrip(trip: Trip) {
        if (trip.settlementStatus != TripSettlementStatus.NOT_STARTED) {
            throw BusinessException(TransactionErrorCode.TRANSACTION_LOCKED_BY_SETTLEMENT)
        }
    }
}

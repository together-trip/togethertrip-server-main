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
import com.togethertrip.main.trip.domain.TripParticipant
import com.togethertrip.main.trip.domain.TripParticipantStatus
import com.togethertrip.main.trip.domain.TripSettlementStatus
import com.togethertrip.main.trip.exception.TripErrorCode
import com.togethertrip.main.trip.repository.TripParticipantRepository
import com.togethertrip.main.trip.repository.TripRepository
import com.togethertrip.main.user.domain.User
import com.togethertrip.main.user.domain.UserStatus
import com.togethertrip.main.user.exception.UserErrorCode
import com.togethertrip.main.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class TransactionCreationService(
    private val transactionRepository: TransactionRepository,
    private val transactionShareRepository: TransactionShareRepository,
    private val transactionPaymentRepository: TransactionPaymentRepository,
    private val transactionEventRepository: TransactionEventRepository,
    private val tripRepository: TripRepository,
    private val tripParticipantRepository: TripParticipantRepository,
    private val transactionExchangeRateResolver: TransactionExchangeRateResolver,
    private val userRepository: UserRepository,
    private val balanceSummaryProjectionService: TripParticipantBalanceSummaryProjectionService,
) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun create(
        userId: Long,
        tripId: Long,
        request: CreateTransactionRequest,
    ): TransactionCreationResult {
        val user = getActiveUser(userId)
        val trip = getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )
        validateWritableTrip(trip)
        val actorParticipant = getActiveParticipant(
            tripId = tripId,
            participantId = null,
            userId = userId,
        )

        val ledgerEntry = request.toLedgerEntry()
        val currencySnapshot = resolveCurrencySnapshot(
            currency = ledgerEntry.currency,
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
        return allocations.map { allocation ->
            val participant = getActiveParticipant(
                tripId = tripId,
                participantId = allocation.participantId,
                userId = null,
            )
            val payment = TransactionPayment(
                transaction = transaction,
                tripParticipant = participant,
                amount = allocation.amount,
                currency = snapshot.currency,
                exchangeRate = snapshot.exchangeRate,
                baseCurrency = snapshot.baseCurrency,
                baseAmount = snapshot.convert(allocation.amount),
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
        return allocations.map { allocation ->
            val participant = getActiveParticipant(
                tripId = tripId,
                participantId = allocation.participantId,
                userId = null,
            )
            val share = TransactionShare(
                transaction = transaction,
                tripParticipant = participant,
                shareAmount = allocation.shareAmount,
                currency = snapshot.currency,
                exchangeRate = snapshot.exchangeRate,
                baseCurrency = snapshot.baseCurrency,
                baseShareAmount = snapshot.convert(allocation.shareAmount),
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
    ): TransactionCurrencySnapshot {
        return transactionExchangeRateResolver.resolve(
            currency = currency,
            spendingDate = null,
        ).toCurrencySnapshot()
    }

    private fun getAccessibleTrip(
        userId: Long,
        tripId: Long,
    ): Trip {
        val trip = tripRepository.findByIdAndDeletedAtIsNull(tripId)
            ?: throw BusinessException(TripErrorCode.TRIP_NOT_FOUND)

        if (trip.ownerUser.id == userId) {
            return trip
        }

        val participant = tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
            tripId = tripId,
            userId = userId,
            participantStatus = TripParticipantStatus.ACTIVE,
        ) ?: throw BusinessException(TripErrorCode.TRIP_ACCESS_DENIED)

        return participant.trip
    }

    private fun getActiveParticipant(
        tripId: Long,
        participantId: Long?,
        userId: Long?,
    ): TripParticipant {
        if (participantId != null) {
            return tripParticipantRepository.findByIdAndTripIdAndParticipantStatusAndDeletedAtIsNull(
                id = participantId,
                tripId = tripId,
                participantStatus = TripParticipantStatus.ACTIVE,
            ) ?: throw BusinessException(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND)
        }

        if (userId != null) {
            return tripParticipantRepository.findByTripIdAndUserIdAndParticipantStatusAndDeletedAtIsNull(
                tripId = tripId,
                userId = userId,
                participantStatus = TripParticipantStatus.ACTIVE,
            ) ?: throw BusinessException(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND)
        }

        throw BusinessException(TripErrorCode.TRIP_PARTICIPANT_NOT_FOUND)
    }

    private fun getActiveUser(userId: Long): User {
        val user = userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: throw BusinessException(UserErrorCode.USER_NOT_FOUND)

        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException(UserErrorCode.INACTIVE_USER)
        }

        return user
    }

    private fun validateWritableTrip(trip: Trip) {
        if (trip.settlementStatus != TripSettlementStatus.NOT_STARTED) {
            throw BusinessException(TransactionErrorCode.TRANSACTION_LOCKED_BY_SETTLEMENT)
        }
    }
}

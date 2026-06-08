package com.togethertrip.main.transaction.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.response.CursorResponse
import com.togethertrip.main.transaction.domain.PaymentAllocation
import com.togethertrip.main.transaction.domain.ShareAllocation
import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionCurrencySnapshot
import com.togethertrip.main.transaction.domain.TransactionEvent
import com.togethertrip.main.transaction.domain.TransactionEventPayload
import com.togethertrip.main.transaction.domain.TransactionEventType
import com.togethertrip.main.transaction.domain.TransactionPayment
import com.togethertrip.main.transaction.domain.TransactionShare
import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.transaction.domain.TransactionType
import com.togethertrip.main.transaction.dto.request.CreateTransactionRequest
import com.togethertrip.main.transaction.dto.request.TransactionPaymentInput
import com.togethertrip.main.transaction.dto.request.TransactionShareInput
import com.togethertrip.main.transaction.dto.request.UpdateTransactionPaymentsRequest
import com.togethertrip.main.transaction.dto.request.UpdateTransactionRequest
import com.togethertrip.main.transaction.dto.request.UpdateTransactionSharesRequest
import com.togethertrip.main.transaction.dto.response.TransactionDetailResponse
import com.togethertrip.main.transaction.dto.response.TransactionEventResponse
import com.togethertrip.main.transaction.dto.response.TransactionExchangeRatePreviewResponse
import com.togethertrip.main.transaction.dto.response.TransactionSummaryResponse
import com.togethertrip.main.transaction.exception.TransactionErrorCode
import com.togethertrip.main.transaction.pagination.TransactionCursor
import com.togethertrip.main.transaction.repository.TransactionEventRepository
import com.togethertrip.main.transaction.repository.TransactionPaymentRepository
import com.togethertrip.main.transaction.repository.TransactionRepository
import com.togethertrip.main.transaction.repository.TransactionShareRepository
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
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val transactionShareRepository: TransactionShareRepository,
    private val transactionPaymentRepository: TransactionPaymentRepository,
    private val transactionEventRepository: TransactionEventRepository,
    private val tripRepository: TripRepository,
    private val tripParticipantRepository: TripParticipantRepository,
    private val transactionExchangeRateResolver: TransactionExchangeRateResolver,
    private val userRepository: UserRepository,
) {

    @Transactional
    fun createTransaction(
        userId: Long,
        tripId: Long,
        request: CreateTransactionRequest,
    ): TransactionDetailResponse {
        val user = getActiveUser(userId)
        val trip = getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )
        validateWritableTrip(trip)
        getActiveParticipant(
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

        return TransactionDetailResponse.from(
            transaction = transaction,
            payments = payments,
            shares = shares,
        )
    }

    fun getTransactions(
        userId: Long,
        tripId: Long,
        type: String?,
        participantId: Long?,
        cursor: String?,
        size: Int?,
    ): CursorResponse<TransactionSummaryResponse> {
        getActiveUser(userId)
        getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )
        val requestedSize = (size ?: DEFAULT_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)
        val transactionType = type?.let(::parseTransactionType)
        val parsedCursor = cursor?.let(::parseCursor)
        val transactions = transactionRepository.findTransactions(
            tripId = tripId,
            status = TransactionStatus.ACTIVE,
            transactionType = transactionType,
            participantId = participantId,
            cursorCreatedAt = parsedCursor?.createdAt,
            cursorId = parsedCursor?.id,
            pageable = PageRequest.of(0, requestedSize + 1),
        )
        val responseItems = transactions.take(requestedSize)
        val hasNext = transactions.size > requestedSize
        val nextCursor = if (hasNext && responseItems.isNotEmpty()) {
            val lastTransaction = responseItems.last()
            TransactionCursor(
                createdAt = lastTransaction.createdAt,
                id = lastTransaction.id,
            ).encode()
        } else {
            null
        }

        return CursorResponse(
            items = responseItems.map(TransactionSummaryResponse::from),
            nextCursor = nextCursor,
            hasNext = hasNext,
            size = responseItems.size,
        )
    }

    fun getTransactionExchangeRatePreview(
        userId: Long,
        tripId: Long,
        currency: String,
        spendingDate: LocalDate?,
    ): TransactionExchangeRatePreviewResponse {
        getActiveUser(userId)
        val trip = getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )
        validateWritableTrip(trip)
        getActiveParticipant(
            tripId = tripId,
            participantId = null,
            userId = userId,
        )

        return TransactionExchangeRatePreviewResponse.from(
            transactionExchangeRateResolver.resolve(
                currency = currency,
                spendingDate = spendingDate,
            )
        )
    }

    fun getTransaction(
        userId: Long,
        tripId: Long,
        transactionId: Long,
    ): TransactionDetailResponse {
        getActiveUser(userId)
        getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )
        val transaction = getTransactionOrThrow(
            tripId = tripId,
            transactionId = transactionId,
        )
        val payments = transactionPaymentRepository.findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(transaction.id)
        val shares = transactionShareRepository.findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(transaction.id)

        return TransactionDetailResponse.from(
            transaction = transaction,
            payments = payments,
            shares = shares,
        )
    }

    @Transactional
    fun updateTransaction(
        userId: Long,
        tripId: Long,
        transactionId: Long,
        request: UpdateTransactionRequest,
    ): TransactionDetailResponse {
        val user = getActiveUser(userId)
        val trip = getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )
        validateWritableTrip(trip)
        val transaction = getTransactionOrThrow(
            tripId = tripId,
            transactionId = transactionId,
        )
        val ledgerEntry = request.toLedgerEntry()
        val currencySnapshot = resolveCurrencySnapshot(
            currency = ledgerEntry.currency,
        )

        transaction.updateSnapshot(
            ledgerEntry = ledgerEntry,
            currencySnapshot = currencySnapshot,
        )
        replacePayments(
            transaction = transaction,
            tripId = tripId,
            allocations = ledgerEntry.payments,
            snapshot = currencySnapshot,
        )
        replaceShares(
            transaction = transaction,
            tripId = tripId,
            allocations = ledgerEntry.shares,
            snapshot = currencySnapshot,
        )

        recordEvent(
            trip = trip,
            transaction = transaction,
            eventType = TransactionEventType.UPDATED,
            createdBy = user,
        )

        return getTransaction(
            userId = userId,
            tripId = tripId,
            transactionId = transactionId,
        )
    }

    @Transactional
    fun deleteTransaction(
        userId: Long,
        tripId: Long,
        transactionId: Long,
    ) {
        val user = getActiveUser(userId)
        val trip = getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )
        validateWritableTrip(trip)
        val transaction = getTransactionOrThrow(
            tripId = tripId,
            transactionId = transactionId,
        )

        transaction.void()

        recordEvent(
            trip = trip,
            transaction = transaction,
            eventType = TransactionEventType.VOIDED,
            createdBy = user,
        )
    }

    fun getTransactionEvents(
        userId: Long,
        tripId: Long,
        transactionId: Long,
    ): List<TransactionEventResponse> {
        getActiveUser(userId)
        getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )
        getTransactionOrThrow(
            tripId = tripId,
            transactionId = transactionId,
        )

        return transactionEventRepository
            .findByTransactionIdOrderByAggregateVersionAsc(transactionId)
            .map(TransactionEventResponse::from)
    }

    @Transactional
    fun updateTransactionPayments(
        userId: Long,
        tripId: Long,
        transactionId: Long,
        request: UpdateTransactionPaymentsRequest,
    ): TransactionDetailResponse {
        val transaction = getTransaction(
            userId = userId,
            tripId = tripId,
            transactionId = transactionId,
        )

        return updateTransaction(
            userId = userId,
            tripId = tripId,
            transactionId = transactionId,
            request = UpdateTransactionRequest(
                transactionType = transaction.summary.transactionType,
                amount = transaction.summary.amount,
                currency = transaction.summary.currency,
                payments = request.payments,
                shares = transaction.shares.map { share ->
                    TransactionShareInput(
                        participantId = share.participantId,
                        shareAmount = share.shareAmount,
                        shareRatio = share.shareRatio,
                    )
                },
            ),
        )
    }

    @Transactional
    fun updateTransactionShares(
        userId: Long,
        tripId: Long,
        transactionId: Long,
        request: UpdateTransactionSharesRequest,
    ): TransactionDetailResponse {
        val transaction = getTransaction(
            userId = userId,
            tripId = tripId,
            transactionId = transactionId,
        )

        return updateTransaction(
            userId = userId,
            tripId = tripId,
            transactionId = transactionId,
            request = UpdateTransactionRequest(
                transactionType = transaction.summary.transactionType,
                amount = transaction.summary.amount,
                currency = transaction.summary.currency,
                payments = transaction.payments.map { payment ->
                    TransactionPaymentInput(
                        participantId = payment.participantId,
                        amount = payment.amount,
                    )
                },
                shares = request.shares,
            ),
        )
    }

    private fun replacePayments(
        transaction: Transaction,
        tripId: Long,
        allocations: List<PaymentAllocation>,
        snapshot: TransactionCurrencySnapshot,
    ) {
        transactionPaymentRepository.findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(transaction.id)
            .forEach { it.markDeleted() }
        savePayments(
            transaction = transaction,
            tripId = tripId,
            allocations = allocations,
            snapshot = snapshot,
        )
    }

    private fun replaceShares(
        transaction: Transaction,
        tripId: Long,
        allocations: List<ShareAllocation>,
        snapshot: TransactionCurrencySnapshot,
    ) {
        transactionShareRepository.findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(transaction.id)
            .forEach { it.markDeleted() }
        saveShares(
            transaction = transaction,
            tripId = tripId,
            allocations = allocations,
            snapshot = snapshot,
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
            transactionPaymentRepository.save(
                TransactionPayment(
                    transaction = transaction,
                    tripParticipant = participant,
                    amount = allocation.amount,
                    currency = snapshot.currency,
                    exchangeRate = snapshot.exchangeRate,
                    baseCurrency = snapshot.baseCurrency,
                    baseAmount = snapshot.convert(allocation.amount),
                )
            )
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
            transactionShareRepository.save(
                TransactionShare(
                    transaction = transaction,
                    tripParticipant = participant,
                    shareAmount = allocation.shareAmount,
                    currency = snapshot.currency,
                    exchangeRate = snapshot.exchangeRate,
                    baseCurrency = snapshot.baseCurrency,
                    baseShareAmount = snapshot.convert(allocation.shareAmount),
                    shareRatio = allocation.shareRatio,
                )
            )
        }
    }

    private fun recordEvent(
        trip: Trip,
        transaction: Transaction,
        eventType: TransactionEventType,
        createdBy: User,
    ) {
        trip.expenseVersion += 1

        transactionEventRepository.save(
            TransactionEvent(
                transaction = transaction,
                trip = trip,
                eventType = eventType,
                aggregateVersion = trip.expenseVersion,
                payload = TransactionEventPayload.from(
                    transaction = transaction,
                    eventType = eventType,
                ).toJson(),
                createdBy = createdBy,
            )
        )
    }

    private fun resolveCurrencySnapshot(
        currency: String,
    ): TransactionCurrencySnapshot {
        return transactionExchangeRateResolver.resolve(
            currency = currency,
            spendingDate = null,
        ).toCurrencySnapshot()
    }

    private fun getTransactionOrThrow(
        tripId: Long,
        transactionId: Long,
    ): Transaction {
        val transaction = transactionRepository.findByIdAndDeletedAtIsNull(transactionId)
            ?: throw BusinessException(TransactionErrorCode.TRANSACTION_NOT_FOUND)

        if (transaction.trip.id != tripId) {
            throw BusinessException(TransactionErrorCode.TRANSACTION_TRIP_MISMATCH)
        }

        return transaction
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

    private fun parseTransactionType(type: String): TransactionType {
        return try {
            TransactionType.valueOf(type.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
    }

    private fun parseCursor(cursor: String): TransactionCursor {
        return try {
            TransactionCursor.decode(cursor)
        } catch (_: RuntimeException) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
        private const val MAX_PAGE_SIZE = 100
    }
}

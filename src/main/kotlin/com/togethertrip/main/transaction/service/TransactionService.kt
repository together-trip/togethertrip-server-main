package com.togethertrip.main.transaction.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.post.repository.PostRepository
import com.togethertrip.main.global.response.CursorResponse
import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionEvent
import com.togethertrip.main.transaction.domain.TransactionEventType
import com.togethertrip.main.transaction.domain.TransactionPayment
import com.togethertrip.main.transaction.domain.TransactionShare
import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.transaction.domain.TransactionType
import com.togethertrip.main.transaction.domain.event.TransactionEventPayload
import com.togethertrip.main.transaction.domain.exchange.TransactionCurrencySnapshot
import com.togethertrip.main.transaction.domain.ledger.PaymentAllocation
import com.togethertrip.main.transaction.domain.ledger.ShareAllocation
import com.togethertrip.main.transaction.dto.request.CreateTransactionRequest
import com.togethertrip.main.transaction.dto.request.TransactionPaymentInput
import com.togethertrip.main.transaction.dto.request.TransactionShareInput
import com.togethertrip.main.transaction.dto.request.UpdateTransactionPaymentsRequest
import com.togethertrip.main.transaction.dto.request.UpdateTransactionRequest
import com.togethertrip.main.transaction.dto.request.UpdateTransactionSharesRequest
import com.togethertrip.main.transaction.dto.response.CommonFundBalanceResponse
import com.togethertrip.main.transaction.dto.response.TransactionDetailResponse
import com.togethertrip.main.transaction.dto.response.TransactionEventResponse
import com.togethertrip.main.transaction.dto.response.TransactionExchangeRatePreviewResponse
import com.togethertrip.main.transaction.dto.response.TransactionPaymentResponse
import com.togethertrip.main.transaction.dto.response.TransactionShareResponse
import com.togethertrip.main.transaction.dto.response.TransactionStatisticsItemResponse
import com.togethertrip.main.transaction.dto.response.TransactionStatisticsResponse
import com.togethertrip.main.transaction.dto.response.TransactionSummaryResponse
import com.togethertrip.main.transaction.exception.TransactionErrorCode
import com.togethertrip.main.transaction.pagination.TransactionCursor
import com.togethertrip.main.transaction.repository.TransactionEventRepository
import com.togethertrip.main.transaction.repository.TransactionPaymentRepository
import com.togethertrip.main.transaction.repository.TransactionRepository
import com.togethertrip.main.transaction.repository.TransactionShareRepository
import com.togethertrip.main.transaction.repository.TransactionStatisticsQueryRepository
import com.togethertrip.main.transaction.repository.projection.TransactionStatisticsRow
import com.togethertrip.main.transaction.service.support.TransactionStatisticsGroupBy
import com.togethertrip.main.transaction.service.support.TransactionStatisticsPeriod
import com.togethertrip.main.transaction.service.support.TransactionCreationService
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
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@Service
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val transactionShareRepository: TransactionShareRepository,
    private val transactionPaymentRepository: TransactionPaymentRepository,
    private val transactionEventRepository: TransactionEventRepository,
    private val transactionStatisticsQueryRepository: TransactionStatisticsQueryRepository,
    private val postRepository: PostRepository,
    private val tripRepository: TripRepository,
    private val tripParticipantRepository: TripParticipantRepository,
    private val transactionExchangeRateResolver: TransactionExchangeRateResolver,
    private val transactionCreationService: TransactionCreationService,
    private val userRepository: UserRepository,
    private val balanceSummaryProjectionService: TripParticipantBalanceSummaryProjectionService,
    private val clock: Clock,
) {

    @Transactional
    fun createTransaction(
        userId: Long,
        tripId: Long,
        request: CreateTransactionRequest,
    ): TransactionDetailResponse {
        val result = transactionCreationService.create(
            userId = userId,
            tripId = tripId,
            request = request,
        )

        return TransactionDetailResponse.from(
            transaction = result.transaction,
            payments = result.payments,
            shares = result.shares,
        )
    }

    @Transactional(readOnly = true)
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
            transactionTypeFilterEnabled = transactionType != null,
            participantId = participantId,
            participantFilterEnabled = participantId != null,
            cursorCreatedAt = parsedCursor?.createdAt,
            cursorId = parsedCursor?.id,
            cursorFilterEnabled = parsedCursor != null,
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

    @Transactional(readOnly = true)
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
        val preview = transactionExchangeRateResolver.resolve(
            currency = currency,
            spendingDate = spendingDate,
        )

        return TransactionExchangeRatePreviewResponse.from(preview)
    }

    @Transactional(readOnly = true)
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
        val payments = transactionPaymentRepository
            .findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(transaction.id)
        val shares = transactionShareRepository
            .findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(transaction.id)

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
        validateActiveTransaction(transaction)
        val ledgerEntry = request.toLedgerEntry()
        val currencySnapshot = resolveCurrencySnapshot(
            currency = ledgerEntry.currency,
            occurredAt = request.occurredAt,
        )
        val previousPayments = transactionPaymentRepository.findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(transaction.id)
        val previousShares = transactionShareRepository.findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(transaction.id)

        transaction.updateSnapshot(
            ledgerEntry = ledgerEntry,
            currencySnapshot = currencySnapshot,
            category = request.category,
            occurredAt = request.occurredAt,
        )
        syncLinkedExpensePostsMetadata(transaction)
        val currentPayments = replacePayments(
            transaction = transaction,
            tripId = tripId,
            allocations = ledgerEntry.payments,
            snapshot = currencySnapshot,
            previousPayments = previousPayments,
        )
        val currentShares = replaceShares(
            transaction = transaction,
            tripId = tripId,
            allocations = ledgerEntry.shares,
            snapshot = currencySnapshot,
            previousShares = previousShares,
        )

        recordEvent(
            trip = trip,
            transaction = transaction,
            eventType = TransactionEventType.UPDATED,
            createdBy = user,
        )
        balanceSummaryProjectionService.applyTransactionUpdated(
            trip = trip,
            previousPayments = previousPayments,
            previousShares = previousShares,
            currentPayments = currentPayments,
            currentShares = currentShares,
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
        validateActiveTransaction(transaction)
        val payments = transactionPaymentRepository.findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(transaction.id)
        val shares = transactionShareRepository.findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(transaction.id)

        transaction.void()
        markLinkedExpensePostsDeleted(transaction)

        recordEvent(
            trip = trip,
            transaction = transaction,
            eventType = TransactionEventType.VOIDED,
            createdBy = user,
        )
        balanceSummaryProjectionService.applyTransactionVoided(
            trip = trip,
            payments = payments,
            shares = shares,
        )
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    fun getCommonFundBalance(
        userId: Long,
        tripId: Long,
    ): CommonFundBalanceResponse {
        getActiveUser(userId)
        val trip = getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )
        val row = transactionStatisticsQueryRepository.findCommonFundBalance(
            tripId = tripId,
            status = TransactionStatus.ACTIVE,
        )
        val chargedBaseAmount = row.chargedBaseAmount
        val usedBaseAmount = row.usedBaseAmount

        // 공동경비 잔액은 충전 합계에서 사용 합계를 차감한다.
        return CommonFundBalanceResponse(
            tripId = tripId,
            baseCurrency = row.baseCurrency ?: trip.defaultCurrency,
            chargedBaseAmount = chargedBaseAmount,
            usedBaseAmount = usedBaseAmount,
            balanceBaseAmount = chargedBaseAmount - usedBaseAmount,
        )
    }

    @Transactional(readOnly = true)
    fun getTransactionStatistics(
        userId: Long,
        tripId: Long,
        from: String?,
        to: String?,
        groupBy: String?,
    ): TransactionStatisticsResponse {
        getActiveUser(userId)
        getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )
        val parsedPeriod = TransactionStatisticsPeriod.from(
            from = from,
            to = to,
        )
        val parsedGroupBy = TransactionStatisticsGroupBy.from(groupBy)
        // groupBy 값에 따라 거래 원장 집계 기준을 분기한다.
        val rows = when (parsedGroupBy) {
            TransactionStatisticsGroupBy.TYPE -> transactionStatisticsQueryRepository.findTypeStatistics(
                tripId = tripId,
                from = parsedPeriod.fromInstant,
                toExclusive = parsedPeriod.toExclusiveInstant,
            )

            TransactionStatisticsGroupBy.CATEGORY -> transactionStatisticsQueryRepository.findCategoryStatistics(
                tripId = tripId,
                from = parsedPeriod.fromInstant,
                toExclusive = parsedPeriod.toExclusiveInstant,
            )

            TransactionStatisticsGroupBy.PARTICIPANT -> transactionStatisticsQueryRepository.findParticipantShareStatistics(
                tripId = tripId,
                from = parsedPeriod.fromInstant,
                toExclusive = parsedPeriod.toExclusiveInstant,
            )
        }

        // 전체 합계는 응답 항목 합계를 기준으로 계산한다.
        return TransactionStatisticsResponse(
            tripId = tripId,
            groupBy = parsedGroupBy.value,
            from = parsedPeriod.from,
            to = parsedPeriod.to,
            totalBaseAmount = rows.fold(BigDecimal.ZERO) { total, row -> total + row.totalBaseAmount },
            items = rows.map(::toStatisticsItemResponse),
        )
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
        val updateRequest = createPaymentsUpdateRequest(
            transaction = transaction,
            payments = request.payments,
        )

        return updateTransaction(
            userId = userId,
            tripId = tripId,
            transactionId = transactionId,
            request = updateRequest,
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
        val updateRequest = createSharesUpdateRequest(
            transaction = transaction,
            shares = request.shares,
        )

        return updateTransaction(
            userId = userId,
            tripId = tripId,
            transactionId = transactionId,
            request = updateRequest,
        )
    }

    private fun createPaymentsUpdateRequest(
        transaction: TransactionDetailResponse,
        payments: List<TransactionPaymentInput>,
    ): UpdateTransactionRequest {
        return UpdateTransactionRequest(
            transactionType = transaction.summary.transactionType,
            amount = transaction.summary.amount,
            currency = transaction.summary.currency,
            category = transaction.summary.category,
            occurredAt = transaction.summary.occurredAt,
            payments = payments,
            shares = transaction.shares.map(::toShareInput),
        )
    }

    private fun createSharesUpdateRequest(
        transaction: TransactionDetailResponse,
        shares: List<TransactionShareInput>,
    ): UpdateTransactionRequest {
        return UpdateTransactionRequest(
            transactionType = transaction.summary.transactionType,
            amount = transaction.summary.amount,
            currency = transaction.summary.currency,
            category = transaction.summary.category,
            occurredAt = transaction.summary.occurredAt,
            payments = transaction.payments.map(::toPaymentInput),
            shares = shares,
        )
    }

    private fun toPaymentInput(payment: TransactionPaymentResponse): TransactionPaymentInput {
        return TransactionPaymentInput(
            participantId = payment.participantId,
            amount = payment.amount,
        )
    }

    private fun toShareInput(share: TransactionShareResponse): TransactionShareInput {
        return TransactionShareInput(
            participantId = share.participantId,
            shareAmount = share.shareAmount,
            shareRatio = share.shareRatio,
        )
    }

    private fun replacePayments(
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

    private fun replaceShares(
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

    private fun syncLinkedExpensePostsMetadata(transaction: Transaction) {
        postRepository.findByTransactionIdAndDeletedAtIsNull(transaction.id)
            .forEach { post ->
                post.update(
                    title = post.title,
                    category = transaction.category,
                    content = post.content,
                    occurredAt = transaction.occurredAt,
                    placeName = post.placeName,
                    latitude = post.latitude,
                    longitude = post.longitude,
                )
            }
    }

    private fun markLinkedExpensePostsDeleted(transaction: Transaction) {
        postRepository.findByTransactionIdAndDeletedAtIsNull(transaction.id)
            .forEach { it.markDeleted() }
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

    private fun validateActiveTransaction(transaction: Transaction) {
        if (transaction.status != TransactionStatus.ACTIVE) {
            throw BusinessException(TransactionErrorCode.TRANSACTION_ALREADY_VOIDED)
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

    private fun toStatisticsItemResponse(row: TransactionStatisticsRow): TransactionStatisticsItemResponse {
        return TransactionStatisticsItemResponse(
            key = row.key,
            label = row.label,
            transactionCount = row.transactionCount,
            totalBaseAmount = row.totalBaseAmount,
        )
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
        private const val MAX_PAGE_SIZE = 100
    }
}

package com.togethertrip.main.transaction.service

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.global.exception.CommonErrorCode
import com.togethertrip.main.global.response.CursorResponse
import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionEventType
import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.transaction.domain.TransactionType
import com.togethertrip.main.transaction.domain.exchange.TransactionCurrencySnapshot
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
import com.togethertrip.main.transaction.service.support.LinkedExpensePostSynchronizer
import com.togethertrip.main.transaction.service.support.TransactionAllocationWriter
import com.togethertrip.main.transaction.service.support.TransactionStatisticsGroupBy
import com.togethertrip.main.transaction.service.support.TransactionStatisticsPeriod
import com.togethertrip.main.transaction.service.support.TransactionCreationService
import com.togethertrip.main.transaction.service.support.TransactionEventRecorder
import com.togethertrip.main.settlement.service.support.TripParticipantBalanceSummaryProjectionService
import com.togethertrip.main.trip.domain.Trip
import com.togethertrip.main.trip.service.support.TripAccessResolver
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
    private val transactionExchangeRateResolver: TransactionExchangeRateResolver,
    private val transactionCreationService: TransactionCreationService,
    private val balanceSummaryProjectionService: TripParticipantBalanceSummaryProjectionService,
    private val tripAccessResolver: TripAccessResolver,
    private val transactionEventRecorder: TransactionEventRecorder,
    private val transactionAllocationWriter: TransactionAllocationWriter,
    private val linkedExpensePostSynchronizer: LinkedExpensePostSynchronizer,
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
        tripAccessResolver.getActiveUser(userId)
        tripAccessResolver.getAccessibleTrip(
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
        tripAccessResolver.getActiveUser(userId)
        val trip = tripAccessResolver.getAccessibleTrip(
            userId = userId,
            tripId = tripId,
        )
        requireTransactionWritable(trip)
        tripAccessResolver.getActiveParticipantByUserId(
            tripId = tripId,
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
        tripAccessResolver.getActiveUser(userId)
        tripAccessResolver.getAccessibleTrip(
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
        val user = tripAccessResolver.getActiveUser(userId)
        val trip = tripAccessResolver.getAccessibleTripForUpdate(
            userId = userId,
            tripId = tripId,
        )
        requireTransactionWritable(trip)
        val transaction = getTransactionOrThrow(
            tripId = tripId,
            transactionId = transactionId,
        )
        transaction.assertMutableBy(userId)
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
        linkedExpensePostSynchronizer.syncMetadata(transaction)
        val currentPayments = transactionAllocationWriter.replacePayments(
            transaction = transaction,
            tripId = tripId,
            allocations = ledgerEntry.payments,
            snapshot = currencySnapshot,
            previousPayments = previousPayments,
        )
        val currentShares = transactionAllocationWriter.replaceShares(
            transaction = transaction,
            tripId = tripId,
            allocations = ledgerEntry.shares,
            snapshot = currencySnapshot,
            previousShares = previousShares,
        )

        transactionEventRecorder.record(
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
        val user = tripAccessResolver.getActiveUser(userId)
        val trip = tripAccessResolver.getAccessibleTripForUpdate(
            userId = userId,
            tripId = tripId,
        )
        requireTransactionWritable(trip)
        val transaction = getTransactionOrThrow(
            tripId = tripId,
            transactionId = transactionId,
        )
        val payments = transactionPaymentRepository.findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(transaction.id)
        val shares = transactionShareRepository.findByTransactionIdAndDeletedAtIsNullOrderByIdAsc(transaction.id)

        transaction.voidBy(userId)
        linkedExpensePostSynchronizer.markDeleted(transaction)

        transactionEventRecorder.record(
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
        tripAccessResolver.getActiveUser(userId)
        tripAccessResolver.getAccessibleTrip(
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
        tripAccessResolver.getActiveUser(userId)
        val trip = tripAccessResolver.getAccessibleTrip(
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
        tripAccessResolver.getActiveUser(userId)
        tripAccessResolver.getAccessibleTrip(
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

    private fun requireTransactionWritable(trip: Trip) {
        if (!trip.canChangeTransactions()) {
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

package com.togethertrip.main.transaction.dto.response

import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.transaction.domain.TransactionType
import java.math.BigDecimal
import java.time.Instant

data class TransactionSummaryResponse(
    val id: Long,
    val tripId: Long,
    val transactionType: TransactionType,
    val amount: BigDecimal,
    val currency: String,
    val exchangeRate: BigDecimal,
    val baseCurrency: String,
    val baseAmount: BigDecimal,
    val category: String?,
    val occurredAt: Instant?,
    val status: TransactionStatus,
    val createdByUserId: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(transaction: Transaction): TransactionSummaryResponse {
            return TransactionSummaryResponse(
                id = transaction.id,
                tripId = transaction.trip.id,
                transactionType = transaction.transactionType,
                amount = transaction.amount,
                currency = transaction.currency,
                exchangeRate = transaction.exchangeRate,
                baseCurrency = transaction.baseCurrency,
                baseAmount = transaction.baseAmount,
                category = transaction.category,
                occurredAt = transaction.occurredAt,
                status = transaction.status,
                createdByUserId = transaction.createdBy.id,
                createdAt = transaction.createdAt,
                updatedAt = transaction.updatedAt,
            )
        }
    }
}

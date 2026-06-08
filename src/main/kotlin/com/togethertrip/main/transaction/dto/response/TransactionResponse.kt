package com.togethertrip.main.transaction.dto.response

import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionEvent
import com.togethertrip.main.transaction.domain.TransactionExchangeRatePreview
import com.togethertrip.main.transaction.domain.TransactionPayment
import com.togethertrip.main.transaction.domain.TransactionShare
import com.togethertrip.main.transaction.domain.TransactionStatus
import com.togethertrip.main.transaction.domain.TransactionType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class TransactionExchangeRatePreviewResponse(
    val baseCurrency: String,
    val targetCurrency: String,
    val rate: BigDecimal,
    val rateDate: LocalDate,
    val source: String?,
) {
    companion object {
        fun from(preview: TransactionExchangeRatePreview): TransactionExchangeRatePreviewResponse {
            return TransactionExchangeRatePreviewResponse(
                baseCurrency = preview.baseCurrency,
                targetCurrency = preview.currency,
                rate = preview.rate,
                rateDate = preview.rateDate,
                source = preview.source,
            )
        }
    }
}

data class TransactionSummaryResponse(
    val id: Long,
    val tripId: Long,
    val transactionType: TransactionType,
    val amount: BigDecimal,
    val currency: String,
    val exchangeRate: BigDecimal,
    val baseCurrency: String,
    val baseAmount: BigDecimal,
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
                status = transaction.status,
                createdByUserId = transaction.createdBy.id,
                createdAt = transaction.createdAt,
                updatedAt = transaction.updatedAt,
            )
        }
    }
}

data class TransactionDetailResponse(
    val summary: TransactionSummaryResponse,
    val payments: List<TransactionPaymentResponse>,
    val shares: List<TransactionShareResponse>,
) {
    companion object {
        fun from(
            transaction: Transaction,
            payments: List<TransactionPayment>,
            shares: List<TransactionShare>,
        ): TransactionDetailResponse {
            return TransactionDetailResponse(
                summary = TransactionSummaryResponse.from(transaction),
                payments = payments.map(TransactionPaymentResponse::from),
                shares = shares.map(TransactionShareResponse::from),
            )
        }
    }
}

data class TransactionPaymentResponse(
    val id: Long,
    val participantId: Long,
    val participantDisplayName: String,
    val amount: BigDecimal,
    val currency: String,
    val exchangeRate: BigDecimal,
    val baseCurrency: String,
    val baseAmount: BigDecimal,
) {
    companion object {
        fun from(payment: TransactionPayment): TransactionPaymentResponse {
            return TransactionPaymentResponse(
                id = payment.id,
                participantId = payment.tripParticipant.id,
                participantDisplayName = payment.tripParticipant.displayName,
                amount = payment.amount,
                currency = payment.currency,
                exchangeRate = payment.exchangeRate,
                baseCurrency = payment.baseCurrency,
                baseAmount = payment.baseAmount,
            )
        }
    }
}

data class TransactionShareResponse(
    val id: Long,
    val participantId: Long,
    val participantDisplayName: String,
    val shareAmount: BigDecimal,
    val currency: String,
    val exchangeRate: BigDecimal,
    val baseCurrency: String,
    val baseShareAmount: BigDecimal,
    val shareRatio: BigDecimal?,
) {
    companion object {
        fun from(share: TransactionShare): TransactionShareResponse {
            return TransactionShareResponse(
                id = share.id,
                participantId = share.tripParticipant.id,
                participantDisplayName = share.tripParticipant.displayName,
                shareAmount = share.shareAmount,
                currency = share.currency,
                exchangeRate = share.exchangeRate,
                baseCurrency = share.baseCurrency,
                baseShareAmount = share.baseShareAmount,
                shareRatio = share.shareRatio,
            )
        }
    }
}

data class TransactionEventResponse(
    val id: Long,
    val transactionId: Long,
    val tripId: Long,
    val eventType: String,
    val aggregateVersion: Long,
    val payload: String,
    val createdByUserId: Long,
    val createdAt: Instant,
) {
    companion object {
        fun from(event: TransactionEvent): TransactionEventResponse {
            return TransactionEventResponse(
                id = event.id,
                transactionId = event.transaction.id,
                tripId = event.trip.id,
                eventType = event.eventType.name,
                aggregateVersion = event.aggregateVersion,
                payload = event.payload,
                createdByUserId = event.createdBy.id,
                createdAt = event.createdAt,
            )
        }
    }
}

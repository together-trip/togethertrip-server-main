package com.togethertrip.main.transaction.dto.response

import com.togethertrip.main.transaction.domain.Transaction
import com.togethertrip.main.transaction.domain.TransactionPayment
import com.togethertrip.main.transaction.domain.TransactionShare

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

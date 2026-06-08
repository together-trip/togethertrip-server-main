package com.togethertrip.main.transaction.domain.ledger

import com.togethertrip.main.global.exception.BusinessException
import com.togethertrip.main.transaction.domain.TransactionType
import com.togethertrip.main.transaction.exception.TransactionErrorCode
import java.math.BigDecimal

data class TransactionLedgerEntry(
    val transactionType: TransactionType,
    val amount: BigDecimal,
    val currency: String,
    val payments: List<PaymentAllocation>,
    val shares: List<ShareAllocation>,
) {

    init {
        if (amount <= BigDecimal.ZERO) {
            throw BusinessException(TransactionErrorCode.INVALID_TRANSACTION_AMOUNT)
        }
        if (payments.map { payment -> payment.participantId }.toSet().size != payments.size) {
            throw BusinessException(TransactionErrorCode.DUPLICATE_TRANSACTION_PARTICIPANT)
        }
        if (shares.map { share -> share.participantId }.toSet().size != shares.size) {
            throw BusinessException(TransactionErrorCode.DUPLICATE_TRANSACTION_PARTICIPANT)
        }
        if (payments.fold(BigDecimal.ZERO) { total, payment -> total + payment.amount }.compareTo(amount) != 0) {
            throw BusinessException(TransactionErrorCode.TRANSACTION_PAYMENT_TOTAL_MISMATCH)
        }
        if (shares.fold(BigDecimal.ZERO) { total, share -> total + share.shareAmount }.compareTo(amount) != 0) {
            throw BusinessException(TransactionErrorCode.TRANSACTION_SHARE_TOTAL_MISMATCH)
        }
    }
}

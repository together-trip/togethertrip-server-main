package com.togethertrip.main.transaction.dto.request

import com.togethertrip.main.transaction.domain.TransactionType
import com.togethertrip.main.transaction.domain.ledger.TransactionLedgerEntry
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class UpdateTransactionRequest(
    @field:NotNull
    val transactionType: TransactionType = TransactionType.EXPENSE,
    @field:NotNull
    @field:DecimalMin(value = "0.01")
    val amount: BigDecimal,
    @field:NotBlank
    @field:Size(min = 3, max = 3)
    val currency: String,
    @field:Valid
    @field:NotEmpty
    val payments: List<TransactionPaymentInput>,
    @field:Valid
    @field:NotEmpty
    val shares: List<TransactionShareInput>,
) {
    fun toLedgerEntry(): TransactionLedgerEntry {
        return TransactionLedgerEntry(
            transactionType = transactionType,
            amount = amount,
            currency = currency,
            payments = payments.map(TransactionPaymentInput::toAllocation),
            shares = shares.map(TransactionShareInput::toAllocation),
        )
    }
}

package com.togethertrip.main.transaction.dto.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty

data class UpdateTransactionPaymentsRequest(
    @field:Valid
    @field:NotEmpty
    val payments: List<TransactionPaymentInput>,
)

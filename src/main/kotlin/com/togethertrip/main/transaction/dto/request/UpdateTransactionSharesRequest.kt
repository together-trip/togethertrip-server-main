package com.togethertrip.main.transaction.dto.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty

data class UpdateTransactionSharesRequest(
    @field:Valid
    @field:NotEmpty
    val shares: List<TransactionShareInput>,
)

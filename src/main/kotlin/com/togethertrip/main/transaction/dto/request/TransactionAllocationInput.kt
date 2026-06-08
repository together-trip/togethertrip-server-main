package com.togethertrip.main.transaction.dto.request

import com.togethertrip.main.transaction.domain.PaymentAllocation
import com.togethertrip.main.transaction.domain.ShareAllocation
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class TransactionPaymentInput(
    @field:NotNull
    val participantId: Long,
    @field:NotNull
    @field:DecimalMin(value = "0.00")
    val amount: BigDecimal,
) {
    fun toAllocation(): PaymentAllocation {
        return PaymentAllocation(
            participantId = participantId,
            amount = amount,
        )
    }
}

data class TransactionShareInput(
    @field:NotNull
    val participantId: Long,
    @field:NotNull
    @field:DecimalMin(value = "0.00")
    val shareAmount: BigDecimal,
    val shareRatio: BigDecimal? = null,
) {
    fun toAllocation(): ShareAllocation {
        return ShareAllocation(
            participantId = participantId,
            shareAmount = shareAmount,
            shareRatio = shareRatio,
        )
    }
}

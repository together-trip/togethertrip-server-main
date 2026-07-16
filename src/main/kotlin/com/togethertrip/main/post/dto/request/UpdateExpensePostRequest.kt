package com.togethertrip.main.post.dto.request

import com.togethertrip.main.post.validation.ValidPlace
import com.togethertrip.main.transaction.domain.TransactionType
import com.togethertrip.main.transaction.dto.request.TransactionPaymentInput
import com.togethertrip.main.transaction.dto.request.TransactionShareInput
import com.togethertrip.main.transaction.dto.request.UpdateTransactionRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.time.Instant

@ValidPlace
data class UpdateExpensePostRequest(
    val title: String? = null,
    val category: String? = null,
    val content: String? = null,
    val occurredAt: Instant? = null,
    override val placeName: String? = null,
    override val latitude: BigDecimal? = null,
    override val longitude: BigDecimal? = null,
    val replaceAttachments: Boolean = false,
    val files: List<MultipartFile> = emptyList(),
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
) : PlaceRequest {

    fun toUpdateTransactionRequest(): UpdateTransactionRequest {
        return UpdateTransactionRequest(
            transactionType = transactionType,
            amount = amount,
            currency = currency,
            category = category,
            occurredAt = occurredAt,
            payments = payments,
            shares = shares,
        )
    }

}

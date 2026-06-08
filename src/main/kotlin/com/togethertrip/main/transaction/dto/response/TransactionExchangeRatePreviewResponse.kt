package com.togethertrip.main.transaction.dto.response

import com.togethertrip.main.transaction.domain.TransactionExchangeRatePreview
import java.math.BigDecimal
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

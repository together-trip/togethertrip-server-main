package com.togethertrip.main.transaction.dto.response

import com.togethertrip.main.transaction.domain.TransactionPayment
import com.togethertrip.main.trip.dto.response.TripParticipantDisplay
import java.math.BigDecimal

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
                participantDisplayName = TripParticipantDisplay.displayName(payment.tripParticipant),
                amount = payment.amount,
                currency = payment.currency,
                exchangeRate = payment.exchangeRate,
                baseCurrency = payment.baseCurrency,
                baseAmount = payment.baseAmount,
            )
        }
    }
}

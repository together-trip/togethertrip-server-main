package com.togethertrip.main.transaction.dto.response

import com.togethertrip.main.transaction.domain.TransactionShare
import java.math.BigDecimal

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

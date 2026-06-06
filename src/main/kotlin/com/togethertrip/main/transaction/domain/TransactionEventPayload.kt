package com.togethertrip.main.transaction.domain

data class TransactionEventPayload(
    val eventType: TransactionEventType,
    val transactionId: Long,
    val amount: String,
    val currency: String,
    val exchangeRate: String,
    val baseCurrency: String,
    val baseAmount: String,
    val status: TransactionStatus,
) {

    fun toJson(): String {
        return """
            {
              "eventType": "${eventType.name}",
              "transactionId": $transactionId,
              "amount": "$amount",
              "currency": "$currency",
              "exchangeRate": "$exchangeRate",
              "baseCurrency": "$baseCurrency",
              "baseAmount": "$baseAmount",
              "status": "${status.name}"
            }
        """.trimIndent()
    }

    companion object {
        fun from(
            transaction: Transaction,
            eventType: TransactionEventType,
        ): TransactionEventPayload {
            return TransactionEventPayload(
                eventType = eventType,
                transactionId = transaction.id,
                amount = transaction.amount.toPlainString(),
                currency = transaction.currency,
                exchangeRate = transaction.exchangeRate.toPlainString(),
                baseCurrency = transaction.baseCurrency,
                baseAmount = transaction.baseAmount.toPlainString(),
                status = transaction.status,
            )
        }
    }
}

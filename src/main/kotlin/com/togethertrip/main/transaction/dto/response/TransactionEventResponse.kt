package com.togethertrip.main.transaction.dto.response

import com.togethertrip.main.transaction.domain.TransactionEvent
import java.time.Instant

data class TransactionEventResponse(
    val id: Long,
    val transactionId: Long,
    val tripId: Long,
    val eventType: String,
    val aggregateVersion: Long,
    val payload: String,
    val createdByUserId: Long,
    val createdAt: Instant,
) {
    companion object {
        fun from(event: TransactionEvent): TransactionEventResponse {
            return TransactionEventResponse(
                id = event.id,
                transactionId = event.transaction.id,
                tripId = event.trip.id,
                eventType = event.eventType.name,
                aggregateVersion = event.aggregateVersion,
                payload = event.payload,
                createdByUserId = event.createdBy.id,
                createdAt = event.createdAt,
            )
        }
    }
}

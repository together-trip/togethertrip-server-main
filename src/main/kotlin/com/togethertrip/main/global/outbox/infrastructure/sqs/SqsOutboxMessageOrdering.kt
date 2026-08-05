package com.togethertrip.main.global.outbox.infrastructure.sqs

import com.togethertrip.main.global.outbox.domain.OutboxEvent

data class SqsOutboxMessageOrdering(
    val messageGroupId: String,
    val messageDeduplicationId: String,
) {
    companion object {
        fun from(event: OutboxEvent): SqsOutboxMessageOrdering {
            require(event.id > 0) { "A persisted outbox event ID is required for FIFO deduplication" }
            require(event.aggregateType.isNotBlank()) { "An aggregate type is required for FIFO ordering" }

            return SqsOutboxMessageOrdering(
                messageGroupId = "${event.aggregateType}:${event.aggregateId}",
                messageDeduplicationId = event.id.toString(),
            )
        }
    }
}

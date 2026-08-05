package com.togethertrip.main.global.outbox.infrastructure.sqs

import com.togethertrip.main.global.outbox.domain.OutboxEvent
import com.togethertrip.main.global.outbox.service.OutboxEventSender
import tools.jackson.databind.ObjectMapper
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

class SqsOutboxEventSender(
    private val sqsClient: SqsClient,
    private val objectMapper: ObjectMapper,
    private val properties: SqsOutboxProperties,
) : OutboxEventSender {

    override fun send(event: OutboxEvent) {
        val message = SqsOutboxEventMessage(
            id = event.id,
            aggregateType = event.aggregateType,
            aggregateId = event.aggregateId,
            eventType = event.eventType,
            payload = objectMapper.readTree(event.payload),
        )
        val requestBuilder = SendMessageRequest.builder()
            .queueUrl(properties.queueUrl)
            .messageBody(objectMapper.writeValueAsString(message))

        if (properties.isFifoQueue()) {
            val ordering = SqsOutboxMessageOrdering.from(event)
            requestBuilder
                .messageGroupId(ordering.messageGroupId)
                .messageDeduplicationId(ordering.messageDeduplicationId)
        }

        sqsClient.sendMessage(requestBuilder.build())
    }
}

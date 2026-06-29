package com.togethertrip.main.global.outbox.infrastructure.sqs

import tools.jackson.databind.JsonNode

data class SqsOutboxEventMessage(
    val id: Long,
    val aggregateType: String,
    val aggregateId: Long,
    val eventType: String,
    val payload: JsonNode,
)

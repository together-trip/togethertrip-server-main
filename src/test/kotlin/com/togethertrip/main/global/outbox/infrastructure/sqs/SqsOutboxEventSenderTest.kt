package com.togethertrip.main.global.outbox.infrastructure.sqs

import com.togethertrip.main.global.outbox.domain.OutboxEvent
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import tools.jackson.module.kotlin.jacksonObjectMapper
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import java.time.Instant
import kotlin.test.assertEquals

class SqsOutboxEventSenderTest {

    private val sqsClient = mock(SqsClient::class.java)
    private val objectMapper = jacksonObjectMapper()
    private val sender = SqsOutboxEventSender(
        sqsClient = sqsClient,
        objectMapper = objectMapper,
        properties = SqsOutboxProperties(
            queueUrl = "https://sqs.ap-northeast-2.amazonaws.com/123/togethertrip-test",
        ),
    )

    @Test
    fun `notification 서비스가 읽는 outbox message 포맷으로 SQS에 발행한다`() {
        val event = OutboxEvent(
            aggregateType = "TRIP",
            aggregateId = 10L,
            eventType = "TRIP_PARTICIPANTS_ADDED",
            payload = """{"eventVersion":1,"recipients":[{"userId":1}],"occurredAt":"2026-06-22T12:00:00Z"}""",
        ).apply {
            id = 5L
            createdAt = Instant.parse("2026-06-22T12:00:00Z")
            updatedAt = Instant.parse("2026-06-22T12:00:00Z")
        }

        sender.send(event)

        val captor = ArgumentCaptor.forClass(SendMessageRequest::class.java)
        verify(sqsClient).sendMessage(captor.capture())

        val request = captor.value
        assertEquals("https://sqs.ap-northeast-2.amazonaws.com/123/togethertrip-test", request.queueUrl())

        val body = objectMapper.readTree(request.messageBody())
        assertEquals(5L, body["id"].longValue())
        assertEquals("TRIP", body["aggregateType"].stringValue())
        assertEquals(10L, body["aggregateId"].longValue())
        assertEquals("TRIP_PARTICIPANTS_ADDED", body["eventType"].stringValue())
        assertEquals(1, body["payload"]["eventVersion"].intValue())
        assertEquals(1L, body["payload"]["recipients"][0]["userId"].longValue())
        assertEquals("2026-06-22T12:00:00Z", body["payload"]["occurredAt"].stringValue())
        assertEquals(null, request.messageGroupId())
        assertEquals(null, request.messageDeduplicationId())
    }

    @Test
    fun `FIFO queue는 aggregate ordering group과 event deduplication ID를 설정한다`() {
        val fifoSender = SqsOutboxEventSender(
            sqsClient = sqsClient,
            objectMapper = objectMapper,
            properties = SqsOutboxProperties(
                queueUrl = "https://sqs.ap-northeast-2.amazonaws.com/123/togethertrip-test.fifo",
                queueType = SqsOutboxQueueType.FIFO,
            ),
        )
        val event = outboxEvent(id = 15L, aggregateType = "SETTLEMENT_TRANSFER", aggregateId = 203L)

        fifoSender.send(event)

        val captor = ArgumentCaptor.forClass(SendMessageRequest::class.java)
        verify(sqsClient).sendMessage(captor.capture())
        assertEquals("SETTLEMENT_TRANSFER:203", captor.value.messageGroupId())
        assertEquals("15", captor.value.messageDeduplicationId())
    }

    private fun outboxEvent(
        id: Long,
        aggregateType: String,
        aggregateId: Long,
    ): OutboxEvent {
        return OutboxEvent(
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = "SETTLEMENT_TRANSFER_COMPLETED",
            payload = """{"eventVersion":1,"recipients":[{"userId":1}]}""",
        ).apply {
            this.id = id
            createdAt = Instant.parse("2026-06-22T12:00:00Z")
            updatedAt = Instant.parse("2026-06-22T12:00:00Z")
        }
    }
}
